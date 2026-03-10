package com.example.nexuswallet.feature.market.ui

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.example.nexuswallet.feature.market.data.remote.PricePoint
import com.example.nexuswallet.feature.market.domain.Token
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.div

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

        val paddingBottom = 30f
        val paddingLeft = 65f

        val chartHeight = height - paddingBottom

        val stepX =
            if (pricePoints.size > 1)
                (width - paddingLeft) / (pricePoints.size - 1)
            else 0f

        val points = pricePoints.mapIndexed { index, point ->

            val x = paddingLeft + (index * stepX)

            val y = chartHeight -
                    ((point.price - minPrice) / range * chartHeight).toFloat()

            Offset(x, y.coerceIn(0f, chartHeight))
        }

        // =========================
        // Y AXIS INTERVALS
        // =========================

        val yAxisSteps = (chartHeight / 50).toInt().coerceIn(4, 6)
        val priceStep = range / yAxisSteps

        for (i in 0..yAxisSteps) {

            val priceValue = minPrice + (priceStep * i)

            val y = chartHeight - (i * (chartHeight / yAxisSteps))

            // Grid line
            drawLine(
                color = axisColor.copy(alpha = 0.3f),
                start = Offset(paddingLeft, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )

            // Label
            val label = "$${formatLargeNumber(priceValue)}"

            val textLayout = textMeasurer.measure(
                text = label,
                style = TextStyle(
                    fontSize = 13.sp,
                    color = textColor
                )
            )

            drawText(
                textLayout,
                topLeft = Offset(
                    paddingLeft - textLayout.size.width - 8f,
                    y - textLayout.size.height / 2
                )
            )
        }

        // =========================
        // AREA FILL
        // =========================

        val fillPath = Path().apply {

            val first = points.first()
            val last = points.last()

            moveTo(first.x, chartHeight)
            lineTo(first.x, first.y)

            for (i in 1 until points.size) {
                lineTo(points[i].x, points[i].y)
            }

            lineTo(last.x, chartHeight)
            close()
        }

        drawPath(
            path = fillPath,
            color = lineColor.copy(alpha = 0.1f),
            style = Fill
        )

        // =========================
        // LINE GRAPH
        // =========================

        for (i in 0 until points.size - 1) {
            drawLine(
                color = lineColor,
                start = points[i],
                end = points[i + 1],
                strokeWidth = 3f
            )
        }

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
                        fontSize = 12.sp,
                        color = textColor
                    )
                )

                drawText(
                    textLayout,
                    topLeft = Offset(
                        x - textLayout.size.width / 2,
                        height - textLayout.size.height
                    )
                )

                drawLine(
                    color = axisColor,
                    start = Offset(x, chartHeight),
                    end = Offset(x, chartHeight + 6f),
                    strokeWidth = 1f
                )
            }
        }
    }
}