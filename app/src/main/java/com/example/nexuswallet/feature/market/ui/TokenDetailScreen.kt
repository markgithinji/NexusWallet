package com.example.nexuswallet.feature.market.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.nexuswallet.R
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.formatLargeNumber
import com.example.nexuswallet.feature.core.util.formatPrice
import com.example.nexuswallet.feature.core.util.formatSupply
import com.example.nexuswallet.feature.core.util.formatTwoDecimals
import com.example.nexuswallet.feature.market.domain.model.ChartData
import com.example.nexuswallet.feature.market.domain.model.ChartDuration
import com.example.nexuswallet.feature.market.domain.model.NewsArticle
import com.example.nexuswallet.feature.market.domain.model.TokenDetail
import com.example.nexuswallet.feature.wallet.ui.common.FullScreenLoading
import com.example.nexuswallet.feature.wallet.ui.common.shimmer
import com.example.nexuswallet.ui.theme.success
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenDetailScreen(
    onNavigateUp: () -> Unit,
    tokenId: String,
    viewModel: TokenDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val chartState by viewModel.chartState.collectAsStateWithLifecycle()
    val newsState by viewModel.newsState.collectAsStateWithLifecycle()
    val selectedDuration by viewModel.selectedDuration.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TokenDetailTopBar(
                tokenId = tokenId,
                onNavigateUp = onNavigateUp,
                onRefresh = { viewModel.refresh() }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when (val state = uiState) {
            is Result.Loading -> {
                FullScreenLoading(message = stringResource(R.string.loading_token_details))
            }

            is Result.Error -> {
                ErrorScreen(
                    message = state.message,
                    onRetry = { viewModel.retryLoading() },
                    modifier = Modifier.padding(padding)
                )
            }

            is Result.Success -> {
                val token = state.data

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header Card
                    item {
                        TokenHeaderCard(token = token)
                    }

                    // Price Card
                    item {
                        PriceCard(token = token)
                    }

                    // Chart Card with duration selector
                    item {
                        PriceChart(
                            chartState = chartState,
                            selectedDuration = selectedDuration,
                            onDurationSelected = { viewModel.selectDuration(it) }
                        )
                    }

                    // Market Stats Card
                    item {
                        MarketStatsCard(token = token)
                    }

                    // Supply Info Card
                    item {
                        SupplyCard(token = token)
                    }

                    // All Time High/Low Card
                    item {
                        AllTimeCard(token = token)
                    }

                    // News Section
                    item {
                        NewsSection(
                            newsState = newsState,
                            onRetry = { viewModel.loadNews() }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TokenDetailTopBar(
    tokenId: String,
    onNavigateUp: () -> Unit,
    onRefresh: () -> Unit,
    viewModel: TokenDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val token = (uiState as? Result.Success)?.data

    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Coin icon
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                ) {
                    if (token != null) {
                        AsyncImage(
                            model = token.image,
                            contentDescription = token.name,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        // Fallback icon while loading
                        Icon(
                            imageVector = Icons.Outlined.AccountBalanceWallet,
                            contentDescription = stringResource(R.string.token_icon),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Text(
                    text = token?.name ?: tokenId.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateUp) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = stringResource(R.string.refresh),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun ErrorScreen(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Error,
            contentDescription = stringResource(R.string.error),
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
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

@Composable
fun PriceChart(
    chartState: Result<ChartData>,
    selectedDuration: ChartDuration,
    onDurationSelected: (ChartDuration) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with title
            Text(
                text = stringResource(R.string.price_chart),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Duration selector chips
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChartDuration.entries.filter { it != ChartDuration.MAX }.forEach { duration ->
                    FilterChip(
                        selected = selectedDuration == duration,
                        onClick = { onDurationSelected(duration) },
                        label = {
                            Text(
                                text = duration.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 12.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                when (chartState) {
                    is Result.Loading -> {
                        ChartLoadingState()
                    }

                    is Result.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Error,
                                    contentDescription = stringResource(R.string.error),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.failed_to_load_chart),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    is Result.Success -> {
                        val chartData = chartState.data
                        if (chartData.prices.isNotEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                PriceLineChart(
                                    pricePoints = chartData.prices,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                val firstPrice = chartData.prices.first().price
                                val lastPrice = chartData.prices.last().price
                                val priceChange = lastPrice - firstPrice
                                val priceChangePercent = (priceChange / firstPrice) * 100

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Open price
                                    Column {
                                        Text(
                                            text = stringResource(R.string.open),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "$${firstPrice.formatPrice()}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    // Change percentage
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = stringResource(R.string.change),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Surface(
                                            shape = CircleShape,
                                            color = if (priceChange >= 0)
                                                MaterialTheme.colorScheme.success.copy(alpha = 0.1f)
                                            else
                                                MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (priceChange >= 0)
                                                    Icons.AutoMirrored.Outlined.TrendingUp
                                                else
                                                    Icons.AutoMirrored.Outlined.TrendingDown,
                                                contentDescription = stringResource(R.string.price_trend),
                                                modifier = Modifier.size(12.dp),
                                                tint = if (priceChange >= 0)
                                                    MaterialTheme.colorScheme.success
                                                else
                                                    MaterialTheme.colorScheme.error
                                            )
                                        }

                                        Text(
                                            text = "${if (priceChange >= 0) "+" else ""}${priceChangePercent.formatTwoDecimals()}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (priceChange >= 0)
                                                MaterialTheme.colorScheme.success
                                            else
                                                MaterialTheme.colorScheme.error
                                        )
                                    }

                                    // Close price
                                    Column(
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text(
                                            text = stringResource(R.string.close),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "$${lastPrice.formatPrice()}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.no_chart_data),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartLoadingState() {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Real-world matched chart skeleton
        PriceChartSkeleton(
            modifier = Modifier.shimmer()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Open/Change/Close row placeholder
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Open label
            Column {
                Box(modifier = Modifier.width(40.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                Spacer(modifier = Modifier.height(6.dp))
                Box(modifier = Modifier.width(80.dp).height(18.dp).clip(RoundedCornerShape(4.dp)).shimmer())
            }
            
            // Change label
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.width(40.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                Spacer(modifier = Modifier.height(6.dp))
                Box(modifier = Modifier.size(32.dp).clip(CircleShape).shimmer())
            }

            // Close label
            Column(horizontalAlignment = Alignment.End) {
                Box(modifier = Modifier.width(40.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                Spacer(modifier = Modifier.height(6.dp))
                Box(modifier = Modifier.width(80.dp).height(18.dp).clip(RoundedCornerShape(4.dp)).shimmer())
            }
        }
    }
}

@Composable
private fun NewsSection(
    newsState: Result<List<NewsArticle>>,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Article,
                    contentDescription = stringResource(R.string.news_icon),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.latest_news),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (newsState) {
                is Result.Loading -> {
                    repeat(3) { index ->
                        ShimmerNewsItem()
                        if (index < 2) Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                is Result.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = newsState.message.ifBlank { stringResource(R.string.failed_to_load_news) },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = onRetry) {
                                Text(stringResource(R.string.try_again))
                            }
                        }
                    }
                }

                is Result.Success -> {
                    val articles = newsState.data
                    if (articles.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.no_news_available),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        // News items
                        articles.take(3).forEachIndexed { index, article ->
                            NewsItem(article = article)

                            if (index < articles.size - 1 && index < 2) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.outline,
                                    thickness = 1.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShimmerNewsItem() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .shimmer()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .shimmer()
        )
    }
}

@Composable
fun TokenHeaderCard(token: TokenDetail) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = token.image,
                contentDescription = token.name,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = token.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${token.symbol.uppercase()} • ${
                        stringResource(
                            R.string.rank_label,
                            token.marketCapRank
                        )
                    }",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PriceCard(token: TokenDetail) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.current_price),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "$${token.currentPrice.formatPrice()}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 24h change
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (token.priceChangePercentage24h >= 0)
                            Icons.AutoMirrored.Outlined.TrendingUp
                        else
                            Icons.AutoMirrored.Outlined.TrendingDown,
                        contentDescription = stringResource(R.string.trend_24h),
                        modifier = Modifier.size(16.dp),
                        tint = if (token.priceChangePercentage24h >= 0)
                            MaterialTheme.colorScheme.success
                        else
                            MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "${if (token.priceChangePercentage24h >= 0) "+" else ""}${token.priceChangePercentage24h.formatTwoDecimals()}%",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (token.priceChangePercentage24h >= 0)
                            MaterialTheme.colorScheme.success
                        else
                            MaterialTheme.colorScheme.error
                    )
                }

                Text(
                    text = stringResource(R.string.label_24h),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.weight(1f))

                // 24h range
                Text(
                    text = "${stringResource(R.string.low_short)}$${token.low24h.formatPrice()} ${
                        stringResource(
                            R.string.high_short
                        )
                    }$${token.high24h.formatPrice()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun MarketStatsCard(token: TokenDetail) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.market_stats),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Market Cap
            StatRowWithChange(
                label = stringResource(R.string.market_cap),
                value = "$${formatLargeNumber(token.marketCap)}",
                change = "${((token.marketCap / token.currentPrice) * 100).toInt()}%${
                    stringResource(
                        R.string.of_supply
                    )
                }",
                changeUp = true
            )

            // Fully Diluted Valuation
            token.fullyDilutedValuation?.let { fdv ->
                StatRowWithChange(
                    label = stringResource(R.string.fdv),
                    value = "$${formatLargeNumber(fdv)}",
                    change = "",
                    changeUp = true
                )
            }

            // 24h Trading Volume
            StatRowWithChange(
                label = stringResource(R.string.volume_24h),
                value = "$${formatLargeNumber(token.totalVolume)}",
                change = "${((token.totalVolume / token.marketCap) * 100).toInt()}%${
                    stringResource(
                        R.string.of_market_cap
                    )
                }",
                changeUp = true
            )

            // Volume/Market Cap Ratio
            val volumeRatio = if (token.marketCap > 0) {
                (token.totalVolume / token.marketCap * 100).toInt()
            } else 0
            StatRowWithChange(
                label = stringResource(R.string.volume_market_cap_ratio),
                value = "${volumeRatio}%",
                change = "",
                changeUp = true
            )
        }
    }
}

@Composable
fun SupplyCard(token: TokenDetail) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.supply_info),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Circulating Supply
            StatRowWithChange(
                label = stringResource(R.string.circulating_supply),
                value = formatSupply(token.circulatingSupply),
                change = token.symbol.uppercase(),
                changeUp = true
            )

            // Total Supply
            token.totalSupply?.let {
                StatRowWithChange(
                    label = stringResource(R.string.total_supply),
                    value = formatSupply(it),
                    change = token.symbol.uppercase(),
                    changeUp = true
                )
            }

            // Max Supply
            token.maxSupply?.let {
                StatRowWithChange(
                    label = stringResource(R.string.max_supply),
                    value = formatSupply(it),
                    change = token.symbol.uppercase(),
                    changeUp = true
                )
            }

            // Supply progress bar
            if (token.totalSupply != null && token.totalSupply > 0) {
                val circulatingPercentage =
                    (token.circulatingSupply / token.totalSupply * 100).toFloat()
                SupplyProgressBar(
                    percentage = circulatingPercentage,
                    label = stringResource(R.string.circulating_total)
                )
            }
        }
    }
}

@Composable
fun SupplyProgressBar(percentage: Float, label: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${percentage.toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(percentage / 100f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary)
                    .clip(RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
fun AllTimeCard(token: TokenDetail) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.ath_atl),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // All Time High
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ath),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$${token.ath.formatPrice()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${if (token.athChangePercentage >= 0) "+" else ""}${token.athChangePercentage.formatTwoDecimals()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (token.athChangePercentage >= 0)
                            MaterialTheme.colorScheme.success
                        else
                            MaterialTheme.colorScheme.error
                    )
                }

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.atl),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$${token.atl.formatPrice()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${if (token.atlChangePercentage >= 0) "+" else ""}${token.atlChangePercentage.formatTwoDecimals()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (token.atlChangePercentage >= 0)
                            MaterialTheme.colorScheme.success
                        else
                            MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Dates
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.ath_label, token.athDate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.atl_label, token.atlDate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
fun StatRowWithChange(
    label: String,
    value: String,
    change: String,
    changeUp: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (change.isNotEmpty()) {
                Text(
                    text = change,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


@Composable
fun NewsItem(
    article: NewsArticle
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // TODO: Handle news item click
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Content
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (!article.summary.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = article.summary.take(100) + "...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = article.source,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = formatRelativeTime(article.publishedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// Helper to format relative time
@Composable
fun formatRelativeTime(dateString: String): String {
    val result = remember(dateString) {
        try {
            val published = Instant.parse(dateString)
            val now = Instant.now()
            val hours = Duration.between(published, now).toHours()
            Triple(true, hours, published)
        } catch (e: Exception) {
            Triple(false, 0L, null)
        }
    }

    if (!result.first) return dateString.take(10)

    val hours = result.second
    val published = result.third as Instant

    return when {
        hours < 1 -> stringResource(R.string.just_now)
        hours < 24 -> stringResource(R.string.hours_ago, hours)
        hours < 168 -> stringResource(R.string.days_ago, hours / 24)
        else -> DateTimeFormatter
            .ofPattern("MMM d")
            .format(published.atZone(ZoneId.systemDefault()))
    }
}
