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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.example.nexuswallet.R
import com.example.nexuswallet.feature.core.ui.LocalCurrency
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.formatCurrency
import com.example.nexuswallet.feature.core.util.formatPrice
import com.example.nexuswallet.feature.core.util.formatTwoDecimals
import com.example.nexuswallet.feature.market.domain.model.ConnectionState
import com.example.nexuswallet.feature.market.domain.model.Token
import com.example.nexuswallet.feature.wallet.ui.common.FullScreenLoading
import com.example.nexuswallet.feature.wallet.ui.common.InlineLoading
import com.example.nexuswallet.ui.theme.success


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun MarketScreen(
    onNavigateToTokenDetail: (String) -> Unit,
    padding: PaddingValues,
    viewModel: MarketViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tokens = uiState.filteredTokens
    val searchQuery = uiState.searchQuery
    val connectionState = uiState.connectionState
    val isLoadingMore = uiState.isLoadingMore

    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading && isRefreshing) {
            isRefreshing = false
        }
    }

    val refreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            viewModel.refreshData()
        }
    )

    Scaffold(
        topBar = {
            MarketTopBar(
                connectionState = connectionState,
                coinCount = tokens.size
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(refreshState)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = scaffoldPadding.calculateTopPadding())
            ) {
                // Show disconnected banner if WebSocket is in error/disconnected state and we have data
                val showBanner = connectionState == ConnectionState.ERROR ||
                        connectionState == ConnectionState.DISCONNECTED

                if (showBanner && tokens.isNotEmpty()) {
                    DisconnectedBanner(
                        onRetry = { viewModel.retryWebSocket() },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                // Search bar
                MarketSearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.updateSearchQuery(it) },
                    onClear = { viewModel.clearSearch() },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // Content
                Box(modifier = Modifier.weight(1f)) {
                    val mergedPadding = PaddingValues(
                        top = 8.dp,
                        bottom = padding.calculateBottomPadding() + 16.dp,
                        start = 16.dp,
                        end = 16.dp
                    )

                    if (!uiState.isLoading && uiState.error == null) {
                        // Only show empty result state if we have a search query
                        if (tokens.isEmpty() && !isLoadingMore && searchQuery.isNotBlank()) {
                            EmptySearchResult()
                        } else {
                            MarketList(
                                tokens = tokens,
                                isLoadingMore = isLoadingMore,
                                onTokenClick = { token ->
                                    onNavigateToTokenDetail(token.id)
                                },
                                onLoadMore = { viewModel.loadNextPage() },
                                contentPadding = mergedPadding
                            )
                        }
                    }
                }
            }

            // Centralized Loading and Error views for better vertical centering
            if (uiState.isLoading && tokens.isEmpty()) {
                FullScreenLoading(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = scaffoldPadding.calculateTopPadding()),
                    message = stringResource(R.string.loading_market_data)
                )
            } else if (uiState.error != null && tokens.isEmpty()) {
                ErrorView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = scaffoldPadding.calculateTopPadding()),
                    message = uiState.error!!,
                    onRetry = {
                        isRefreshing = true
                        viewModel.refreshData()
                    }
                )
            }

            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = refreshState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = scaffoldPadding.calculateTopPadding()),
                backgroundColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun DisconnectedBanner(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Outlined.WifiOff,
                contentDescription = stringResource(R.string.live_updates_disconnected),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )

            Text(
                text = stringResource(R.string.live_updates_disconnected),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )

            TextButton(
                onClick = onRetry,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(
                    stringResource(R.string.reconnect),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketTopBar(
    connectionState: ConnectionState,
    coinCount: Int
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Market icon in title
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ShowChart,
                    contentDescription = "Market icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(R.string.market_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        actions = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Live connection indicator
                ConnectionStatus(
                    state = connectionState
                )

                // Coin count badge
                CoinCountBadge(
                    count = coinCount
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
fun ConnectionStatus(
    state: ConnectionState
) {
    val isConnected = state == ConnectionState.CONNECTED
    val isConnecting = state == ConnectionState.CONNECTING

    val color = when (state) {
        ConnectionState.CONNECTED -> MaterialTheme.colorScheme.success
        ConnectionState.CONNECTING -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.error
    }

    val statusText = when (state) {
        ConnectionState.CONNECTED -> stringResource(R.string.status_live)
        ConnectionState.CONNECTING -> "CONNECTING"
        else -> stringResource(R.string.status_offline)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Live pulsing indicator
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color = color)
                .then(
                    if (isConnected || isConnecting) {
                        Modifier.graphicsLayer {
                            alpha = 0.8f
                        }
                    } else {
                        Modifier
                    }
                )
        )

        Text(
            text = statusText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun CoinCountBadge(
    count: Int
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = stringResource(R.string.coin_count, count),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun MarketSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = stringResource(R.string.search),
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                decorationBox = { innerTextField ->
                    Box(
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        innerTextField()
                    }
                }
            )

            if (query.isNotBlank()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.clear_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MarketList(
    tokens: List<Token>,
    isLoadingMore: Boolean,
    onTokenClick: (Token) -> Unit,
    onLoadMore: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tokens) { token ->
            TokenItem(
                token = token,
                onClick = onTokenClick
            )
        }

        if (isLoadingMore) {
            item {
                InlineLoading(
                    modifier = Modifier.padding(vertical = 16.dp),
                    message = stringResource(R.string.loading)
                )
            }
        }

        // Trigger load more when reaching the end
        item {
            LaunchedEffect(Unit) { // TODO: Improve to use paging
                onLoadMore()
            }
        }
    }
}

@Composable
fun TokenItem(
    token: Token,
    onClick: (Token) -> Unit = {}
) {
    val currencyState = LocalCurrency.current

    val animatedPrice by animateFloatAsState(
        targetValue = (token.currentPrice * currencyState.usdToRate).toFloat(),
        animationSpec = tween(durationMillis = 300),
        label = "priceAnimation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(token) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank badge
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = token.marketCapRank.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Coin icon
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
            ) {
                val painterState = remember { mutableStateOf<AsyncImagePainter.State?>(null) }

                AsyncImage(
                    model = token.image,
                    contentDescription = token.name,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape),
                    onState = { state ->
                        painterState.value = state
                    }
                )

                // Show shimmer while loading
                if (painterState.value is AsyncImagePainter.State.Loading ||
                    painterState.value == null
                ) {
                    ShimmerPlaceholder(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                    )
                }

                // Show error icon if failed
                if (painterState.value is AsyncImagePainter.State.Error) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AccountBalanceWallet,
                            contentDescription = "Token icon",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Coin name and symbol
            Column(
                modifier = Modifier.weight(0.7f)
            ) {
                Text(
                    text = token.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = token.symbol.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Mini sparkline
            Box(
                modifier = Modifier
                    .weight(0.7f)
                    .height(36.dp)
                    .padding(horizontal = 6.dp)
            ) {
                token.sparklineIn7d?.price?.let { prices ->
                    if (prices.size >= 2) {
                        TokenSparkline(
                            prices = prices,
                            isPositive = token.priceChangePercentage24h >= 0,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Price and change
            Column(
                modifier = Modifier.weight(1.2f),
                horizontalAlignment = Alignment.End
            ) {
                val formattedPrice = animatedPrice.toDouble().formatCurrency(currencyState.currency)
                Text(
                    text = formattedPrice,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Visible,
                    softWrap = false
                )

                val priceChange = token.priceChangePercentage24h
                val changeColor = if (priceChange >= 0)
                    MaterialTheme.colorScheme.success
                else
                    MaterialTheme.colorScheme.error

                Box(
                    modifier = Modifier
                        .height(20.dp)
                        .widthIn(min = 64.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = if (priceChange >= 0)
                                Icons.AutoMirrored.Outlined.TrendingUp
                            else
                                Icons.AutoMirrored.Outlined.TrendingDown,
                            contentDescription = "Price trend",
                            modifier = Modifier.size(12.dp),
                            tint = changeColor
                        )
                        Text(
                            text = "${if (priceChange >= 0) "+" else ""}${priceChange.formatTwoDecimals()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = changeColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TokenSparkline(
    prices: List<Double>,
    isPositive: Boolean,
    modifier: Modifier = Modifier
) {
    val color = if (isPositive)
        MaterialTheme.colorScheme.success
    else
        MaterialTheme.colorScheme.error

    Canvas(modifier = modifier) {
        if (prices.size < 2) return@Canvas

        // Downsample points if there are too many (e.g., more than 50) for a smoother sparkline
        val maxDisplayPoints = 50
        val sampledPrices = if (prices.size > maxDisplayPoints) {
            val step = prices.size / maxDisplayPoints
            prices.filterIndexed { index, _ -> index % step == 0 }.take(maxDisplayPoints)
        } else {
            prices
        }

        val minPrice = sampledPrices.min()
        val maxPrice = sampledPrices.max()
        val range = (maxPrice - minPrice).coerceAtLeast(0.00000001)

        val width = size.width
        val height = size.height

        val path = Path()
        val points = sampledPrices.mapIndexed { index, price ->
            val x = index * (width / (sampledPrices.size - 1))
            val y = height - ((price - minPrice) / range * height).toFloat()
            Offset(x, y)
        }

        if (points.isEmpty()) return@Canvas

        path.moveTo(points[0].x, points[0].y)

        for (i in 0 until points.size - 1) {
            val p0 = points[i]
            val p1 = points[i + 1]
            
            // Cubic Bezier curve for smoothing
            // Control points are calculated as half-way between points on the X axis
            val controlPoint1 = Offset(p0.x + (p1.x - p0.x) / 2f, p0.y)
            val controlPoint2 = Offset(p0.x + (p1.x - p0.x) / 2f, p1.y)
            
            path.cubicTo(
                controlPoint1.x, controlPoint1.y,
                controlPoint2.x, controlPoint2.y,
                p1.x, p1.y
            )
        }

        // Draw the line
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = 1.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        
        // Add a subtle area fill below the line
        val fillPath = Path().apply {
            addPath(path)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    color.copy(alpha = 0.2f),
                    color.copy(alpha = 0f)
                ),
                startY = 0f,
                endY = height
            )
        )
    }
}

@Composable
fun ShimmerPlaceholder(modifier: Modifier = Modifier) {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.surfaceVariant
    )

    val transition = rememberInfiniteTransition(label = "shimmer_simple")
    val translateX = transition.animateFloat(
        initialValue = -1000f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutLinearInEasing)
        ),
        label = "shimmer_translate"
    )

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .drawBehind {
                val brush = Brush.linearGradient(
                    colors = shimmerColors,
                    start = Offset(translateX.value, 0f),
                    end = Offset(translateX.value + 300f, 300f)
                )
                drawRect(brush = brush)
            }
    )
}

@Composable
fun EmptySearchResult(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
            ) {
                // Animated search icon with background
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SearchOff,
                        contentDescription = "No results",
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Main message
                Text(
                    text = stringResource(R.string.no_matching_coins),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Secondary message
                Text(
                    text = stringResource(R.string.no_matching_coins_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Search tips
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.search_tips_title),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CurrencyBitcoin,
                                contentDescription = stringResource(R.string.bitcoin_icon),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.search_tip_name),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Sell,
                                contentDescription = stringResource(R.string.sell_icon),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.search_tip_symbol),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.refresh_icon),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.search_tip_clear),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Error,
                    contentDescription = "Error",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.connection_error),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        stringResource(R.string.try_again),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
