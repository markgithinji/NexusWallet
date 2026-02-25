package com.example.nexuswallet.feature.market.ui

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.market.domain.Token
import kotlinx.coroutines.delay
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketScreen(
    navController: NavController,
    padding: PaddingValues
) {
    val viewModel: MarketViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val tokens by viewModel.filteredTokens.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isWebSocketConnected by viewModel.isWebSocketConnected.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F7))
            .padding(padding)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header with title and connection status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Market",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Connection status
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LiveIndicator(isConnected = isWebSocketConnected)
                        Text(
                            text = if (isWebSocketConnected) "Live" else "Offline",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isWebSocketConnected)
                                Color(0xFF10B981)
                            else
                                Color(0xFFEF4444)
                        )
                    }

                    // Coin count
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        ),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Text(
                            text = "${tokens.size} coins",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = Color(0xFF374151)
                        )
                    }
                }
            }

            // Search bar
            MarketSearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                onClear = { viewModel.clearSearch() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Content
            Box(modifier = Modifier.weight(1f)) {
                when (uiState) {
                    com.example.nexuswallet.feature.coin.Result.Loading -> {
                        if (tokens.isEmpty()) {
                            LoadingView()
                        } else {
                            MarketList(
                                tokens = tokens,
                                isLoadingMore = isLoadingMore,
                                onTokenClick = { token ->
                                    navController.navigate("token/${token.id}")
                                },
                                onLoadMore = { viewModel.loadNextPage() }
                            )
                        }
                    }

                    is com.example.nexuswallet.feature.coin.Result.Error -> {
                        if (tokens.isEmpty()) {
                            ErrorView(
                                message = (uiState as com.example.nexuswallet.feature.coin.Result.Error).message,
                                onRetry = { viewModel.refreshData() }
                            )
                        } else {
                            Column {
                                // Error banner
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFFFEF2F2)
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
                                            Icons.Outlined.Error,
                                            contentDescription = null,
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Connection issues. Showing cached data.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFEF4444),
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { viewModel.refreshData() },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.Refresh,
                                                contentDescription = "Retry",
                                                tint = Color(0xFFEF4444),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }

                                MarketList(
                                    tokens = tokens,
                                    isLoadingMore = isLoadingMore,
                                    onTokenClick = { token ->
                                        navController.navigate("token/${token.id}")
                                    },
                                    onLoadMore = { viewModel.loadNextPage() }
                                )
                            }
                        }
                    }

                    is Result.Success -> {
                        if (tokens.isEmpty() && !isLoadingMore) {
                            EmptySearchResult()
                        } else {
                            MarketList(
                                tokens = tokens,
                                isLoadingMore = isLoadingMore,
                                onTokenClick = { token ->
                                    navController.navigate("token/${token.id}")
                                },
                                onLoadMore = { viewModel.loadNextPage() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MarketSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
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
                contentDescription = "Search",
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(20.dp),
                tint = Color(0xFF6B7280)
            )

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.Black
                ),
                decorationBox = { innerTextField ->
                    Box(
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                text = "Search by name or symbol...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF9CA3AF)
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
                        contentDescription = "Clear search",
                        tint = Color(0xFF6B7280),
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
    onLoadMore: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Color(0xFF3B82F6),
                        strokeWidth = 3.dp
                    )
                }
            }
        }

        // Trigger load more when reaching the end
        item {
            LaunchedEffect(Unit) {
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
    val animatedPrice by animateFloatAsState(
        targetValue = token.currentPrice.toFloat(),
        animationSpec = tween(durationMillis = 300),
        label = "priceAnimation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(token) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
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
                        color = Color(0xFF3B82F6).copy(alpha = 0.1f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = token.marketCapRank?.toString() ?: "—",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF3B82F6),
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
                    painterState.value == null) {
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
                            .background(Color(0xFFF3F4F6)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AccountBalanceWallet,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF6B7280)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Coin name and symbol
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = token.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = token.symbol.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF6B7280)
                )
            }

            // Price and change
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "$${animatedPrice.formatTwoDecimals()}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                val priceChange = token.priceChangePercentage24h ?: 0.0
                val changeColor = if (priceChange >= 0)
                    Color(0xFF10B981)
                else
                    Color(0xFFEF4444)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = if (priceChange >= 0)
                            Icons.Outlined.TrendingUp
                        else
                            Icons.Outlined.TrendingDown,
                        contentDescription = null,
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

@Composable
fun ShimmerPlaceholder(modifier: Modifier = Modifier) {
    val shimmerColors = listOf(
        Color(0xFFE0E0E0),
        Color(0xFFF5F5F5),
        Color(0xFFE0E0E0)
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
            .background(Color(0xFFE0E0E0))
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
fun LiveIndicator(isConnected: Boolean) {
    var pulse by remember { mutableStateOf(0f) }

    LaunchedEffect(isConnected) {
        if (isConnected) {
            while (true) {
                pulse = 1f
                delay(1000)
                pulse = 0f
                delay(1000)
            }
        }
    }

    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .graphicsLayer {
                alpha = 0.7f + pulse * 0.3f
                scaleX = 1f + pulse * 0.3f
                scaleY = 1f + pulse * 0.3f
            }
            .background(
                color = if (isConnected) Color(0xFF10B981) else Color(0xFFEF4444)
            )
    )
}

@Composable
fun EmptySearchResult() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "No results",
                    modifier = Modifier.size(48.dp),
                    tint = Color(0xFF6B7280)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "No matching coins",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Try adjusting your search",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6B7280),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(0.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF3B82F6),
                    strokeWidth = 3.dp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Loading market data...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6B7280)
                )
            }
        }
    }
}

@Composable
fun ErrorView(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Error,
                    contentDescription = "Error",
                    modifier = Modifier.size(48.dp),
                    tint = Color(0xFFEF4444)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Connection Error",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6B7280),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3B82F6)
                    )
                ) {
                    Text("Try Again")
                }
            }
        }
    }
}

fun Float.formatTwoDecimals(): String = String.format("%.2f", this)
fun Double.formatTwoDecimals(): String = String.format("%.2f", this)