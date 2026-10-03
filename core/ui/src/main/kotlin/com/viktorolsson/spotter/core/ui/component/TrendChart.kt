package com.viktorolsson.spotter.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

data class ChartPoint(val date: LocalDate, val value: Double)

private val axisDate = DateTimeFormatter.ofPattern("d MMM")
private const val Y_LABELS = 4

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
    formatValue: (Double) -> String = { "%.0f".format(it) },
    emptyText: String = "",
) {
    if (primary.size < 2) {
        Box(modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    fun x(points: List<ChartPoint>) =
        if (evenlySpaced) points.indices.map { it.toDouble() } else points.map { it.date.toEpochDay().toDouble() }
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(primary, secondary, evenlySpaced) {
        producer.runTransaction {
            lineModel {
                series(x(primary), primary.map { it.value })
                if (secondary.isNotEmpty()) series(x(secondary), secondary.map { it.value })
            }
        }
    }
    val values = (primary + secondary).map { it.value }
    val spread = (values.max() - values.min()).coerceAtLeast(values.max() * 0.05).coerceAtLeast(1.0)
    val rangeProvider = remember(values) {
        val minY = floor((values.min() - spread * 0.25).coerceAtLeast(0.0))
        // Round the span up to whole steps between the axis labels, so every label is a distinct whole number.
        val intervals = Y_LABELS - 1
        val span = ceil((ceil(values.max() + spread * 0.25) - minY) / intervals) * intervals
        CartesianLayerRangeProvider.fixed(minY = minY, maxY = minY + span)
    }
    val dateFor: (Double) -> LocalDate = remember(primary, evenlySpaced) {
        { value -> if (evenlySpaced) primary[value.roundToInt().coerceIn(0, primary.lastIndex)].date else LocalDate.ofEpochDay(value.toLong()) }
    }
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.tertiary
    ProvideVicoTheme(rememberM3VicoTheme()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(
                        LineCartesianLayer.rememberLine(
                            fill = remember(primaryColor) { LineCartesianLayer.LineFill.single(Fill(primaryColor)) },
                            areaFill = remember(primaryColor) {
                                LineCartesianLayer.AreaFill.single(Fill(primaryColor.copy(alpha = 0.12f)))
                            },
                        ),
                        LineCartesianLayer.rememberLine(
                            fill = remember(secondaryColor) { LineCartesianLayer.LineFill.single(Fill(secondaryColor)) },
                        ),
                    ),
                    rangeProvider = rangeProvider,
                ),
                startAxis = VerticalAxis.rememberStart(
                    valueFormatter = remember(formatValue) { CartesianValueFormatter { _, value, _ -> formatValue(value) } },
                    // A fixed label count reads well for both narrow (99–106) and wide ranges.
                    itemPlacer = remember { VerticalAxis.ItemPlacer.count(count = { Y_LABELS }) },
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = remember(dateFor) {
                        CartesianValueFormatter { _, value, _ -> dateFor(value).format(axisDate) }
                    },
                ),
            ),
            modelProducer = producer,
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
            modifier = modifier.fillMaxWidth().height(200.dp),
        )
    }
}
