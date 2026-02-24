package com.example.nexuswallet.feature.market.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.CurrencyBitcoin
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.nexuswallet.feature.market.data.model.NewsArticle
import com.example.nexuswallet.feature.market.data.remote.ChartData
import com.example.nexuswallet.feature.market.data.remote.ChartDuration
import com.example.nexuswallet.feature.market.data.remote.TokenDetail

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenDetailScreen(
    navController: NavController,
    tokenId: String,
    viewModel: TokenDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val chartData by viewModel.chartData.collectAsState()
    val selectedDuration by viewModel.selectedDuration.collectAsState()
    val isLoadingChart by viewModel.isLoadingChart.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = when (tokenId) {
                                "bitcoin" -> Icons.Outlined.CurrencyBitcoin
                                "ethereum" -> Icons.Outlined.Diamond
                                "solana" -> Icons.Outlined.FlashOn
                                else -> Icons.Outlined.AccountBalanceWallet
                            },
                            contentDescription = null,
                            tint = when (tokenId) {
                                "bitcoin" -> Color(0xFFF7931A)
                                "ethereum" -> Color(0xFF627EEA)
                                "solana" -> Color(0xFF00FFA3)
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                        Text(
                            text = tokenId.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    scrolledContainerColor = Color.White
                )
            )
        },
        containerColor = Color(0xFFF5F5F7)
    ) { padding ->
        when (uiState) {
            is TokenDetailUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF3B82F6))
                }
            }

            is TokenDetailUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Error,
                        contentDescription = "Error",
                        modifier = Modifier.size(48.dp),
                        tint = Color(0xFFEF4444)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = (uiState as TokenDetailUiState.Error).message,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.refresh() },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Try Again")
                    }
                }
            }

            is TokenDetailUiState.Success -> {
                val token = (uiState as TokenDetailUiState.Success).token

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
                            chartData = chartData,
                            selectedDuration = selectedDuration,
                            isLoading = isLoadingChart,
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

                    item {
                        NewsCard(
                            articles = viewModel.newsArticles.collectAsState().value,
                            isLoading = viewModel.isLoadingNews.collectAsState().value
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PriceChart(
    chartData: ChartData?,
    selectedDuration: ChartDuration,
    isLoading: Boolean,
    onDurationSelected: (ChartDuration) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with title and duration selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Price Chart",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                // Duration chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ChartDuration.values().forEach { duration ->
                        FilterChip(
                            selected = selectedDuration == duration,
                            onClick = { onDurationSelected(duration) },
                            label = {
                                Text(
                                    text = duration.label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF3B82F6),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFFF3F4F6)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Chart area
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Color(0xFF3B82F6),
                        strokeWidth = 2.dp
                    )
                }
            } else if (chartData != null && chartData.prices.isNotEmpty()) {
                PriceLineChart(
                    pricePoints = chartData.prices,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Price stats
                val firstPrice = chartData.prices.first().price
                val lastPrice = chartData.prices.last().price
                val priceChange = lastPrice - firstPrice
                val priceChangePercent = (priceChange / firstPrice) * 100

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Open",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF6B7280)
                        )
                        Text(
                            text = "$${firstPrice.formatPrice()}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Change",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF6B7280)
                        )
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
                                modifier = Modifier.size(14.dp),
                                tint = if (priceChange >= 0) Color(0xFF10B981) else Color(0xFFEF4444)
                            )
                            Text(
                                text = "${if (priceChange >= 0) "+" else ""}${priceChangePercent.formatTwoDecimals()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (priceChange >= 0) Color(0xFF10B981) else Color(
                                    0xFFEF4444
                                )
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "Close",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF6B7280)
                        )
                        Text(
                            text = "$${lastPrice.formatPrice()}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No chart data available",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF6B7280)
                    )
                }
            }
        }
    }
}

@Composable
fun TokenHeaderCard(token: TokenDetail) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
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
                    color = Color.Black
                )
                Text(
                    text = "${token.symbol.uppercase()} • Rank #${token.marketCapRank}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6B7280)
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Current Price",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF6B7280)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "$${token.currentPrice.formatPrice()}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black
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
                            Icons.Outlined.TrendingUp
                        else
                            Icons.Outlined.TrendingDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (token.priceChangePercentage24h >= 0)
                            Color(0xFF10B981)
                        else
                            Color(0xFFEF4444)
                    )
                    Text(
                        text = "${if (token.priceChangePercentage24h >= 0) "+" else ""}${token.priceChangePercentage24h.formatTwoDecimals()}%",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (token.priceChangePercentage24h >= 0)
                            Color(0xFF10B981)
                        else
                            Color(0xFFEF4444)
                    )
                }

                Text(
                    text = "• 24h",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF6B7280)
                )

                Spacer(modifier = Modifier.weight(1f))

                // 24h range
                Text(
                    text = "L: $${token.low24h.formatPrice()} H: $${token.high24h.formatPrice()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF6B7280)
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Market Stats",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Market Cap
            StatRowWithChange(
                label = "Market Cap",
                value = "$${formatLargeNumber(token.marketCap)}",
                change = "${((token.marketCap / token.currentPrice) * 100).toInt()}% of supply",
                changeUp = true
            )

            // Fully Diluted Valuation
            token.fullyDilutedValuation?.let { fdv ->
                StatRowWithChange(
                    label = "Fully Diluted Valuation",
                    value = "$${formatLargeNumber(fdv)}",
                    change = "",
                    changeUp = true
                )
            }

            // 24h Trading Volume
            StatRowWithChange(
                label = "24h Trading Volume",
                value = "$${formatLargeNumber(token.totalVolume)}",
                change = "${((token.totalVolume / token.marketCap) * 100).toInt()}% of market cap",
                changeUp = true
            )

            // Volume/Market Cap Ratio
            val volumeRatio = if (token.marketCap > 0) {
                (token.totalVolume / token.marketCap * 100).toInt()
            } else 0
            StatRowWithChange(
                label = "Volume / Market Cap",
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Supply Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Circulating Supply
            StatRowWithChange(
                label = "Circulating Supply",
                value = formatSupply(token.circulatingSupply),
                change = "${token.symbol.uppercase()}",
                changeUp = true
            )

            // Total Supply
            token.totalSupply?.let {
                StatRowWithChange(
                    label = "Total Supply",
                    value = formatSupply(it),
                    change = "${token.symbol.uppercase()}",
                    changeUp = true
                )
            }

            // Max Supply
            token.maxSupply?.let {
                StatRowWithChange(
                    label = "Max Supply",
                    value = formatSupply(it),
                    change = "${token.symbol.uppercase()}",
                    changeUp = true
                )
            }

            // Supply progress bar
            if (token.totalSupply != null && token.totalSupply > 0) {
                val circulatingPercentage =
                    (token.circulatingSupply / token.totalSupply * 100).toFloat()
                SupplyProgressBar(
                    percentage = circulatingPercentage,
                    label = "Circulating / Total"
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
                color = Color(0xFF6B7280)
            )
            Text(
                text = "${percentage.toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFFE5E7EB))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(percentage / 100f)
                    .fillMaxHeight()
                    .background(Color(0xFF3B82F6))
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "All Time High / Low",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(16.dp))

            // All Time High
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "All Time High",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF6B7280)
                    )
                    Text(
                        text = "$${token.ath.formatPrice()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        text = "${if (token.athChangePercentage >= 0) "+" else ""}${token.athChangePercentage.formatTwoDecimals()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (token.athChangePercentage >= 0) Color(0xFF10B981) else Color(
                            0xFFEF4444
                        )
                    )
                }

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        text = "All Time Low",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF6B7280)
                    )
                    Text(
                        text = "$${token.atl.formatPrice()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        text = "${if (token.atlChangePercentage >= 0) "+" else ""}${token.atlChangePercentage.formatTwoDecimals()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (token.atlChangePercentage >= 0) Color(0xFF10B981) else Color(
                            0xFFEF4444
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Dates
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "ATH: ${token.athDate}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF6B7280),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "ATL: ${token.atlDate}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF6B7280),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
fun AboutCard(token: TokenDetail) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "About ${token.name}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = token.description ?: "No description available.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF374151)
            )
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
            color = Color(0xFF6B7280)
        )

        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black
            )
            if (change.isNotEmpty()) {
                Text(
                    text = change,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF6B7280)
                )
            }
        }
    }
}

@Composable
fun PriceChart(
    chartData: ChartData?,
    selectedDuration: ChartDuration,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with title and duration selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Price Chart",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                // Duration chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ChartDuration.values().forEach { duration ->
                        FilterChip(
                            selected = selectedDuration == duration,
                            onClick = { /* Will be handled by ViewModel */ },
                            label = {
                                Text(
                                    text = duration.label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF3B82F6),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Chart area
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Color(0xFF3B82F6),
                        strokeWidth = 2.dp
                    )
                }
            } else if (chartData != null && chartData.prices.isNotEmpty()) {
                PriceLineChart(
                    pricePoints = chartData.prices,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No chart data available",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF6B7280)
                    )
                }
            }

            // Price stats
            if (chartData != null && chartData.prices.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))

                val firstPrice = chartData.prices.first().price
                val lastPrice = chartData.prices.last().price
                val priceChange = lastPrice - firstPrice
                val priceChangePercent = (priceChange / firstPrice) * 100

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Open",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF6B7280)
                        )
                        Text(
                            text = "$${firstPrice.formatPrice()}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Change",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF6B7280)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (priceChange >= 0)
                                    Icons.Outlined.TrendingUp
                                else
                                    Icons.Outlined.TrendingDown,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (priceChange >= 0) Color(0xFF10B981) else Color(0xFFEF4444)
                            )
                            Text(
                                text = "${if (priceChange >= 0) "+" else ""}${priceChangePercent.formatTwoDecimals()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (priceChange >= 0) Color(0xFF10B981) else Color(
                                    0xFFEF4444
                                )
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "Close",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF6B7280)
                        )
                        Text(
                            text = "$${lastPrice.formatPrice()}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun NewsCard(
    articles: List<NewsArticle>,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
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
                    imageVector = Icons.Outlined.Article,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFF3B82F6)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Latest News (24h delay)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Color(0xFF3B82F6),
                        strokeWidth = 2.dp
                    )
                }
            } else if (articles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No news available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF6B7280)
                    )
                }
            } else {
                articles.take(3).forEachIndexed { index, article ->
                    NewsItem(article = article)

                    if (index < articles.size - 1 && index < 2) {
                        Divider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = Color(0xFFE5E7EB),
                            thickness = 1.dp
                        )
                    }
                }

                // Note about free plan limitations
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "⚠️ Free plan: 24h delay, no links",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF6B7280),
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun NewsItem(
    article: NewsArticle
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
               // TODO: Handle news item click
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE5E7EB)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Article,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFF6B7280)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (!article.summary.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = article.summary.take(100) + "...",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF6B7280),
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
                        color = Color(0xFF3B82F6),
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = formatRelativeTime(article.publishedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF6B7280)
                    )
                }
            }
        }
    }
}

// Helper to format relative time
@RequiresApi(Build.VERSION_CODES.O)
fun formatRelativeTime(dateString: String): String {
    return try {
        val published = java.time.Instant.parse(dateString)
        val now = java.time.Instant.now()
        val hours = java.time.Duration.between(published, now).toHours()

        when {
            hours < 1 -> "just now"
            hours < 24 -> "${hours}h ago"
            hours < 168 -> "${hours / 24}d ago"
            else -> java.time.format.DateTimeFormatter
                .ofPattern("MMM d")
                .format(published.atZone(java.time.ZoneId.systemDefault()))
        }
    } catch (e: Exception) {
        dateString.take(10) // Fallback to date part
    }
}

fun Double.formatPrice(): String {
    return when {
        this >= 1000 -> String.format("%,.2f", this)
        this >= 1 -> String.format("%,.4f", this)
        else -> String.format("%,.6f", this)
    }
}

fun formatLargeNumber(number: Double): String {
    return when {
        number >= 1_000_000_000_000 -> "${(number / 1_000_000_000_000.0).formatTwoDecimals()}T"
        number >= 1_000_000_000 -> "${(number / 1_000_000_000.0).formatTwoDecimals()}B"
        number >= 1_000_000 -> "${(number / 1_000_000.0).formatTwoDecimals()}M"
        number >= 1_000 -> "${(number / 1_000.0).formatTwoDecimals()}K"
        else -> number.formatTwoDecimals()
    }
}

fun formatSupply(supply: Double): String {
    return when {
        supply >= 1_000_000_000 -> "${(supply / 1_000_000_000.0).formatTwoDecimals()}B"
        supply >= 1_000_000 -> "${(supply / 1_000_000.0).formatTwoDecimals()}M"
        supply >= 1_000 -> "${(supply / 1_000.0).formatTwoDecimals()}K"
        else -> supply.formatTwoDecimals()
    }
}