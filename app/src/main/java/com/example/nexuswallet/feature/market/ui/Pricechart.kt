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
import androidx.compose.ui.text.font.FontWeight
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
import kotlin.div

@Composable
fun PriceLineChart(
    pricePoints: List<PricePoint>,
    modifier: Modifier = Modifier
) {
    if (pricePoints.isEmpty()) return

    val minPrice = pricePoints.minOfOrNull { it.price } ?: 0.0
    val maxPrice = pricePoints.maxOfOrNull { it.price } ?: 1.0
    val range = maxPrice - minPrice
    val lineColor = if (pricePoints.last().price >= pricePoints.first().price)
        Color(0xFF10B981)
    else
        Color(0xFFEF4444)

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
    ) {
        val width = size.width
        val height = size.height
        val stepX = width / (pricePoints.size - 1)

        val points = pricePoints.mapIndexed { index, point ->
            val x = index * stepX
            val y = height - ((point.price - minPrice) / range * height).toFloat()
            Offset(x, y.coerceIn(0f, height))
        }

        // Draw area fill
        val fillPath = Path().apply {
            val firstPoint = points.first()
            val lastPoint = points.last()

            moveTo(firstPoint.x, height)
            lineTo(firstPoint.x, firstPoint.y)

            for (i in 1 until points.size) {
                lineTo(points[i].x, points[i].y)
            }

            lineTo(lastPoint.x, height)
            close()
        }

        drawPath(
            path = fillPath,
            color = lineColor.copy(alpha = 0.1f),
            style = Fill
        )

        // Draw line
        for (i in 0 until points.size - 1) {
            drawLine(
                color = lineColor,
                start = points[i],
                end = points[i + 1],
                strokeWidth = 2f
            )
        }

        // Draw start and end dots
        drawCircle(
            color = lineColor,
            center = points.first(),
            radius = 4f
        )
        drawCircle(
            color = lineColor,
            center = points.last(),
            radius = 4f
        )
    }
}