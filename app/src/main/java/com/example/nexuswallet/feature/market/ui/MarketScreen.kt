package com.example.nexuswallet.feature.market.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.nexuswallet.feature.market.domain.Token
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketScreen(
    navController: NavController,
    padding: PaddingValues
) {
    val viewModel: MarketViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val tokens by viewModel.filteredTokens.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isWebSocketConnected by viewModel.isWebSocketConnected.collectAsState()

    val pullToRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refreshData() },
        state = pullToRefreshState,
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        when (uiState) {
            MarketUiState.Loading -> LoadingView()

            is MarketUiState.Error -> {
                ErrorView(
                    message = (uiState as MarketUiState.Error).message,
                    onRetry = { viewModel.refreshData() }
                )
            }

            is MarketUiState.Success -> {
                if (tokens.isEmpty()) {
                    EmptySearchResult()
                } else {
                    MarketList(
                        tokens = tokens,
                        onTokenClick = { token ->
                            navController.navigate("token/${token.id}")
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("Search by name or symbol") },
        singleLine = true,
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search"
                    )
                }
            }
        }
    )
}

@Composable
fun EmptySearchResult() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No matching coins found",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
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
            .clip(MaterialTheme.shapes.small)
            .graphicsLayer {
                alpha = 0.7f + pulse * 0.3f
                scaleX = 1f + pulse * 0.3f
                scaleY = 1f + pulse * 0.3f
            }
            .background(
                color = if (isConnected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error
            )
    )
}

@Composable
fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = "Loading market data...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "⚠️",
            style = MaterialTheme.typography.displayMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Connection Error",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
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
            shape = MaterialTheme.shapes.large
        ) {
            Text("Try Again")
        }
    }
}

@Composable
fun MarketList(tokens: List<Token>, onTokenClick: (Token) -> Unit = {}) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tokens) { token ->
            TokenItem(
                token = token,
                onClick = onTokenClick
            )
        }
    }
}

@Composable
fun TokenItem(token: Token, onClick: (Token) -> Unit = {}) {
    var pulseScale by remember { mutableStateOf(1f) }

    LaunchedEffect(token.currentPrice) {
        pulseScale = 1.02f
        delay(100)
        pulseScale = 1f
    }

    val animatedPrice by animateFloatAsState(
        targetValue = token.currentPrice.toFloat(),
        animationSpec = tween(durationMillis = 300),
        label = "priceAnimation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
            }
            .clickable { onClick(token) },
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = token.image,
                    contentDescription = token.name,
                    modifier = Modifier.size(40.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = token.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = token.symbol.uppercase(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "$${animatedPrice.formatTwoDecimals()}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )

                val priceChange = token.priceChangePercentage24h ?: 0.0
                val changeColor = if (priceChange >= 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }

                Text(
                    text = "${if (priceChange >= 0) "+" else ""}${priceChange.formatTwoDecimals()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = changeColor
                )
            }
        }
    }
}

fun Float.formatTwoDecimals(): String {
    return String.format("%.2f", this)
}

fun Double.formatTwoDecimals(): String {
    return String.format("%.2f", this)
}