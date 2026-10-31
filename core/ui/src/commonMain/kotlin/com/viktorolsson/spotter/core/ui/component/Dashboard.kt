package com.viktorolsson.spotter.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.viktorolsson.spotter.core.model.weekStart
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing
import com.viktorolsson.spotter.core.ui.theme.SpotterTheme
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

// The dashboard's small charts, drawn by hand like the website's (web/src/components): a ring for
// the week's goal, a sparkline under a stat, a change badge and the training heatmap.

/**
 * Progress towards a goal as a ring with the count in the middle ("2/4"). [description] is
 * what a screen reader says instead of the drawing.
 */
@Composable
fun ProgressRing(
    done: Int,
    goal: Int,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = Dimens.RingSize,
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val progress = MaterialTheme.colorScheme.primary
    val glow = SpotterTheme.colors.glow
    val fraction = if (goal > 0) (done.toFloat() / goal).coerceIn(0f, 1f) else 0f
    Box(modifier.size(size).clearAndSetSemantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = Dimens.RingStroke.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, FULL_CIRCLE, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            if (fraction > 0f) {
                // A wider, faint arc under the progress: the accent's glow.
                drawArc(glow, START_ANGLE, FULL_CIRCLE * fraction, false, Offset(inset, inset), arcSize, alpha = GLOW_ALPHA, style = Stroke(stroke * 2, cap = StrokeCap.Round))
                drawArc(progress, START_ANGLE, FULL_CIRCLE * fraction, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Text("$done/$goal", style = SpotterTheme.type.numberSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * A small trend line with a fading area under it. Draws nothing unless at least two values
 * are above zero, so a new user doesn't see a flat line.
 */
@Composable
fun Sparkline(values: List<Double>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    if (values.count { it > 0 } < 2) return
    Canvas(modifier.fillMaxWidth().height(Dimens.SparklineHeight)) {
        val max = values.max().takeIf { it > 0 } ?: return@Canvas
        val stroke = SPARK_STROKE.toPx()
        val step = size.width / (values.size - 1)
        val usable = size.height - stroke
        val points = values.mapIndexed { i, v -> Offset(i * step, stroke / 2 + usable * (1 - (v / max).toFloat())) }
        val line = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = SPARK_FILL_ALPHA), Color.Transparent)))
        drawPath(line, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** A change against a previous period ("+12%"): green when it's an improvement, muted otherwise. */
@Composable
fun Delta(text: String, improving: Boolean, modifier: Modifier = Modifier) {
    val color = if (improving) SpotterTheme.colors.good else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        color = color.copy(alpha = DELTA_TINT_ALPHA),
        contentColor = color,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier,
    ) {
        Text(text, style = SpotterTheme.type.label, modifier = Modifier.padding(horizontal = Dimens.BadgePaddingHorizontal, vertical = Spacing.xxs))
    }
}

/**
 * A stat for the dashboard: label, number, an optional [delta] beside it, a [hint] under it
 * and a [spark] trend at the bottom. Sits two to a row.
 */
@Composable
fun DashboardStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    delta: (@Composable () -> Unit)? = null,
    spark: List<Double> = emptyList(),
) {
    ContentCard(modifier) {
        CardTitle(label)
        // The change wraps under the number when large text leaves no room beside it.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value, style = SpotterTheme.type.numberMedium, color = MaterialTheme.colorScheme.onSurface)
            delta?.invoke()
        }
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = SpotterTheme.colors.faint)
        }
        Sparkline(spark)
    }
}

/**
 * Training days as a grid, one column per week (oldest left) and one row per weekday from
 * Monday: empty, one workout, or more. Days after [today] are left blank. [description] is
 * what a screen reader says instead of the grid.
 */
@Composable
fun TrainingHeatmap(
    workoutsByDay: Map<LocalDate, Int>,
    today: LocalDate,
    weeks: Int,
    description: String,
    modifier: Modifier = Modifier,
) {
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val one = MaterialTheme.colorScheme.primary.copy(alpha = ONE_WORKOUT_ALPHA)
    val more = MaterialTheme.colorScheme.primary
    val first = weekStart(today).minus(weeks - 1, DateTimeUnit.WEEK)
    BoxWithConstraints(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }) {
        val gap = HEATMAP_GAP
        val cell = ((maxWidth - gap * (weeks - 1)) / weeks).coerceAtMost(HEATMAP_MAX_CELL)
        Canvas(Modifier.size(width = cell * weeks + gap * (weeks - 1), height = cell * DAYS + gap * (DAYS - 1))) {
            val cellPx = cell.toPx()
            val stepPx = cellPx + gap.toPx()
            val radius = CornerRadius(cellPx * CELL_ROUNDING)
            for (week in 0 until weeks) {
                for (day in 0 until DAYS) {
                    val date = first.plus(week * DAYS + day, DateTimeUnit.DAY)
                    if (date > today) continue
                    val count = workoutsByDay[date] ?: 0
                    drawRoundRect(
                        color = when {
                            count == 0 -> empty
                            count == 1 -> one
                            else -> more
                        },
                        topLeft = Offset(week * stepPx, day * stepPx),
                        size = Size(cellPx, cellPx),
                        cornerRadius = radius,
                    )
                }
            }
        }
    }
}

/** The heatmap's key: "Less ▢ ▣ ■ More". */
@Composable
fun HeatmapLegend(less: String, more: String, modifier: Modifier = Modifier) {
    val colors = listOf(
        MaterialTheme.colorScheme.surfaceContainerHighest,
        MaterialTheme.colorScheme.primary.copy(alpha = ONE_WORKOUT_ALPHA),
        MaterialTheme.colorScheme.primary,
    )
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(less, style = MaterialTheme.typography.labelSmall, color = SpotterTheme.colors.faint)
        colors.forEach { color ->
            Canvas(Modifier.size(LEGEND_CELL)) { drawRoundRect(color, cornerRadius = CornerRadius(size.width * CELL_ROUNDING)) }
        }
        Text(more, style = MaterialTheme.typography.labelSmall, color = SpotterTheme.colors.faint)
    }
}

/** A column of [content] with a heading, for a card on the dashboard. */
@Composable
fun DashboardCard(title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    ContentCard(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { CardTitle(title) }
            trailing()
        }
        content()
    }
}

private const val FULL_CIRCLE = 360f
private const val START_ANGLE = -90f
private const val GLOW_ALPHA = 0.35f
private const val SPARK_FILL_ALPHA = 0.22f
private const val DELTA_TINT_ALPHA = 0.14f
private const val ONE_WORKOUT_ALPHA = 0.45f
private const val CELL_ROUNDING = 0.25f
private const val DAYS = 7
private val SPARK_STROKE = 1.5.dp
private val HEATMAP_GAP = 3.dp
private val HEATMAP_MAX_CELL = 18.dp
private val LEGEND_CELL = 10.dp
