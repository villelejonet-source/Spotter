package com.viktorolsson.spotter.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.viktorolsson.spotter.core.ui.formatAxisDate
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.SpotterTheme
import com.viktorolsson.spotter.core.ui.localFormat
import kotlinx.datetime.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

data class ChartPoint(val date: LocalDate, val value: Double)

private const val Y_LABELS = 4
private val CHART_HEIGHT = 200.dp
private val EMPTY_HEIGHT = 120.dp
private val PRIMARY_STROKE = 2.5.dp
private val SECONDARY_STROKE = 1.5.dp
private const val AREA_ALPHA = 0.3f

/**
 * A dated line chart that fits all points on screen, with the y-axis fitted to the
 * data (so real progress doesn't flatten against zero). [secondary] draws a second
 * line over the same dates, e.g. a moving average.
 *
 * [evenlySpaced] plots one step per point (labelled with its date), which suits
 * per-session data where two sessions can share a day; otherwise x is the calendar day.
 */
@Composable
fun TrendChart(
    primary: List<ChartPoint>,
    modifier: Modifier = Modifier,
    secondary: List<ChartPoint> = emptyList(),
    evenlySpaced: Boolean = false,
    formatValue: (Double) -> String = { "%.0f".localFormat(it) },
    emptyText: String = "",
) {
    if (primary.size < 2) {
        Box(modifier.fillMaxWidth().height(EMPTY_HEIGHT), contentAlignment = Alignment.Center) {
            Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    fun x(points: List<ChartPoint>) =
        if (evenlySpaced) points.indices.map { it.toDouble() } else points.map { it.date.toEpochDays().toDouble() }
    // Built synchronously: the data is small, and the chart is then complete on its first frame
    // (which also keeps screenshot tests deterministic).
    val model = remember(primary, secondary, evenlySpaced) {
        CartesianChartModel(
            LineCartesianLayerModel.build {
                series(x(primary), primary.map { it.value })
                if (secondary.isNotEmpty()) series(x(secondary), secondary.map { it.value })
            },
        )
    }
    val rangeProvider = remember(primary, secondary) {
        val values = (primary + secondary).map { it.value }
        val spread = (values.max() - values.min()).coerceAtLeast(values.max() * 0.05).coerceAtLeast(1.0)
        val minY = floor((values.min() - spread * 0.25).coerceAtLeast(0.0))
        // Round the span up to whole steps between the axis labels, so every label is a distinct whole number.
        val intervals = Y_LABELS - 1
        val span = ceil((ceil(values.max() + spread * 0.25) - minY) / intervals) * intervals
        CartesianLayerRangeProvider.fixed(minY = minY, maxY = minY + span)
    }
    val dateFor: (Double) -> LocalDate = remember(primary, evenlySpaced) {
        { value -> if (evenlySpaced) primary[value.roundToInt().coerceIn(0, primary.lastIndex)].date else LocalDate.fromEpochDays(value.toLong()) }
    }
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.tertiary
    // The website's charts: a glowing line over a fading area, monospace axis labels in the
    // faint colour, and hairline guides.
    val axisLabel = rememberAxisLabelComponent(style = SpotterTheme.type.label.copy(color = SpotterTheme.colors.faint, letterSpacing = TextUnit.Unspecified))
    val hairline = Fill(MaterialTheme.colorScheme.outlineVariant)
    val guideline = rememberAxisGuidelineComponent(fill = hairline, thickness = Dimens.Hairline)
    val axisLine = rememberAxisLineComponent(fill = hairline, thickness = Dimens.Hairline)
    ProvideVicoTheme(rememberM3VicoTheme()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(
                        LineCartesianLayer.rememberLine(
                            fill = remember(primaryColor) { LineCartesianLayer.LineFill.single(Fill(primaryColor)) },
                            stroke = remember { LineCartesianLayer.LineStroke.Continuous(PRIMARY_STROKE) },
                            areaFill = remember(primaryColor) {
                                LineCartesianLayer.AreaFill.single(
                                    Fill(Brush.verticalGradient(listOf(primaryColor.copy(alpha = AREA_ALPHA), Color.Transparent))),
                                )
                            },
                        ),
                        LineCartesianLayer.rememberLine(
                            fill = remember(secondaryColor) { LineCartesianLayer.LineFill.single(Fill(secondaryColor)) },
                            stroke = remember { LineCartesianLayer.LineStroke.Continuous(SECONDARY_STROKE) },
                        ),
                    ),
                    rangeProvider = rangeProvider,
                ),
                startAxis = VerticalAxis.rememberStart(
                    line = null,
                    label = axisLabel,
                    tick = null,
                    guideline = guideline,
                    valueFormatter = remember(formatValue) { CartesianValueFormatter { _, value, _ -> formatValue(value) } },
                    // A fixed label count reads well for both narrow (99–106) and wide ranges.
                    itemPlacer = remember { VerticalAxis.ItemPlacer.count(count = { Y_LABELS }) },
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    line = axisLine,
                    label = axisLabel,
                    tick = null,
                    guideline = null,
                    valueFormatter = remember(dateFor) {
                        CartesianValueFormatter { _, value, _ -> formatAxisDate(dateFor(value)) }
                    },
                ),
            ),
            model = model,
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
            modifier = modifier.fillMaxWidth().height(CHART_HEIGHT),
        )
    }
}
