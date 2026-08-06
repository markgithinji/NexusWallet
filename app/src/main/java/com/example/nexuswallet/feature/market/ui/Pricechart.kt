package com.example.nexuswallet.feature.market.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nexuswallet.feature.market.domain.model.PricePoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.nexuswallet.feature.core.util.formatLargeNumber

@Composable
fun PriceLineChart(
    pricePoints: List<PricePoint>,
    modifier: Modifier = Modifier
) {
    if (pricePoints.isEmpty()) return

    val minPrice = pricePoints.minOfOrNull { it.price } ?: 0.0
    val maxPrice = pricePoints.maxOfOrNull { it.price } ?: 1.0
    val range = (maxPrice - minPrice).takeIf { it != 0.0 } ?: 1.0

    val lineColor =
        if (pricePoints.last().price >= pricePoints.first().price)
            Color(0xFF10B981)
        else
            Color(0xFFEF4444)

    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val axisColor = MaterialTheme.colorScheme.outline

    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
    ) {

        val width = size.width
        val height = size.height

        val paddingBottom = 60f
        val paddingTop = 40f
        val paddingLeft = 110f

        val chartBottom = height - paddingBottom
        val chartTop = paddingTop
        val usableHeight = chartBottom - chartTop

        val stepX =
            if (pricePoints.size > 1)
                (width - paddingLeft - 20f) / (pricePoints.size - 1)
            else 0f

        val points = pricePoints.mapIndexed { index, point ->

            val x = paddingLeft + (index * stepX)

            val y = chartBottom -
                    ((point.price - minPrice) / range * usableHeight).toFloat()

            Offset(x, y.coerceIn(chartTop, chartBottom))
        }

        // =========================
        // AXES LINES
        // =========================

        // X-axis line
        drawLine(
            color = axisColor.copy(alpha = 0.5f),
            start = Offset(paddingLeft, chartBottom),
            end = Offset(width, chartBottom),
            strokeWidth = 2f
        )

        // Y-axis line
        drawLine(
            color = axisColor.copy(alpha = 0.5f),
            start = Offset(paddingLeft, chartTop - 10f),
            end = Offset(paddingLeft, chartBottom),
            strokeWidth = 2f
        )

        // =========================
        // Y AXIS INTERVALS
        // =========================

        val yAxisSteps = 5
        val priceStep = range / yAxisSteps

        for (i in 0..yAxisSteps) {

            val priceValue = minPrice + (priceStep * i)

            val y = chartBottom - (i * (usableHeight / yAxisSteps))

            // Grid line
            drawLine(
                color = axisColor.copy(alpha = 0.2f),
                start = Offset(paddingLeft, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )

            // Label
            val label = "$${formatLargeNumber(priceValue)}"

            val textLayout = textMeasurer.measure(
                text = label,
                style = TextStyle(
                    fontSize = 11.sp,
                    color = textColor
                )
            )

            // Don't draw the bottom-most Y label if it's too close to X axis labels
            if (i > 0) {
                drawText(
                    textLayout,
                    topLeft = Offset(
                        paddingLeft - textLayout.size.width - 12f,
                        y - textLayout.size.height / 2
                    )
                )
            }
        }

        // =========================
        // AREA FILL (SMOOTHED)
        // =========================

        val graphPath = Path().apply {
            if (points.isNotEmpty()) {
                moveTo(points[0].x, points[0].y)
                for (i in 0 until points.size - 1) {
                    val p0 = points[i]
                    val p1 = points[i + 1]
                    val controlPoint1 = Offset(p0.x + (p1.x - p0.x) / 2f, p0.y)
                    val controlPoint2 = Offset(p0.x + (p1.x - p0.x) / 2f, p1.y)
                    cubicTo(
                        controlPoint1.x, controlPoint1.y,
                        controlPoint2.x, controlPoint2.y,
                        p1.x, p1.y
                    )
                }
            }
        }

        val fillPath = Path().apply {
            addPath(graphPath)
            if (points.isNotEmpty()) {
                lineTo(points.last().x, chartBottom)
                lineTo(points.first().x, chartBottom)
                close()
            }
        }

        drawPath(
            path = fillPath,
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(
                    lineColor.copy(alpha = 0.2f),
                    lineColor.copy(alpha = 0f)
                ),
                startY = chartTop,
                endY = chartBottom
            ),
            style = Fill
        )

        // =========================
        // LINE GRAPH (SMOOTHED)
        // =========================

        drawPath(
            path = graphPath,
            color = lineColor,
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Start / End points
        drawCircle(
            color = lineColor,
            center = points.first(),
            radius = 5f
        )

        drawCircle(
            color = lineColor,
            center = points.last(),
            radius = 5f
        )

        // =========================
        // X AXIS LABELS
        // =========================

        val xAxisSteps = 5

        for (i in 0..xAxisSteps) {

            val index =
                (i * (pricePoints.size - 1) / xAxisSteps)

            if (index < pricePoints.size) {

                val x = paddingLeft + (index * stepX)

                val timestamp = pricePoints[index].timestamp
                val date = Date(timestamp)

                val timeRange =
                    pricePoints.last().timestamp -
                            pricePoints.first().timestamp

                val dateFormat = when {
                    timeRange <= 86_400_000 ->
                        SimpleDateFormat("HH:mm", Locale.getDefault())

                    timeRange <= 604_800_000 ->
                        SimpleDateFormat("EEE", Locale.getDefault())

                    else ->
                        SimpleDateFormat("MM/dd", Locale.getDefault())
                }

                val text = dateFormat.format(date)

                val textLayout = textMeasurer.measure(
                    text = text,
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = textColor
                    )
                )

                val labelX = when (i) {
                    0 -> x // Align first label to the left of the point
                    xAxisSteps -> x - textLayout.size.width // Align last label to the right
                    else -> x - textLayout.size.width / 2 // Center others
                }

                drawText(
                    textLayout,
                    topLeft = Offset(
                        labelX,
                        height - textLayout.size.height - 4f
                    )
                )

                drawLine(
                    color = axisColor.copy(alpha = 0.5f),
                    start = Offset(x, chartBottom),
                    end = Offset(x, chartBottom + 10f),
                    strokeWidth = 1f
                )
            }
        }
    }
}