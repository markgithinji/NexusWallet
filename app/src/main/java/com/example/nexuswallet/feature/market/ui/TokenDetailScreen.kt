package com.example.nexuswallet.feature.market.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.Send
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.nexuswallet.R
import com.example.nexuswallet.feature.core.ui.LocalCurrency
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.formatAsCurrency
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
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

    val scrollState = rememberLazyListState()
    val showTopBarDetails by remember {
        derivedStateOf {
            scrollState.firstVisibleItemIndex > 0 || scrollState.firstVisibleItemScrollOffset > 200
        }
    }

    Scaffold(
        topBar = {
            TokenDetailTopBar(
                tokenId = tokenId,
                showDetails = showTopBarDetails,
                onNavigateUp = onNavigateUp
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        val state = uiState

        // Use a local state to keep the token alive across Loading states
        val activeToken = remember { mutableStateOf<TokenDetail?>(null) }
        LaunchedEffect(state) {
            if (state is Result.Success) {
                activeToken.value = state.data
            }
        }

        // Use a combined refreshing state
        val isFetching = state is Result.Loading || chartState is Result.Loading || newsState is Result.Loading
        var isManualRefresh by remember { mutableStateOf(false) }
        LaunchedEffect(isFetching) {
            if (!isFetching) isManualRefresh = false
        }

        val pullRefreshState = rememberPullRefreshState(
            refreshing = isManualRefresh && isFetching && activeToken.value != null,
            onRefresh = {
                isManualRefresh = true
                viewModel.refresh()
            }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(pullRefreshState)
        ) {
            when (state) {
                is Result.Loading if activeToken.value == null -> {
                    TokenDetailSkeleton(modifier = Modifier.padding(padding))
                }

                is Result.Error if activeToken.value == null -> {
                    ErrorScreen(
                        message = state.message,
                        onRetry = { viewModel.retryLoading() },
                        modifier = Modifier.padding(padding)
                    )
                }

                else -> {
                    val currentToken = activeToken.value
                    if (currentToken != null) {
                        LazyColumn(
                            state = scrollState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                            contentPadding = PaddingValues(bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                TokenHeaderSection(token = currentToken)
                            }

                            item {
                                PriceAndChartGroup(
                                    token = currentToken,
                                    chartState = chartState,
                                    selectedDuration = selectedDuration,
                                    onDurationSelected = { viewModel.selectDuration(it) }
                                )
                            }

                            if (currentToken.sentimentUp != null) {
                                item {
                                    SentimentCard(token = currentToken)
                                }
                            }

                            item {
                                MarketDataSection(token = currentToken)
                            }

                            if (!currentToken.description.isNullOrBlank()) {
                                item {
                                    AboutSection(token = currentToken)
                                }
                            }

                            item {
                                LinksAndTagsSection(token = currentToken)
                            }

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

            PullRefreshIndicator(
                refreshing = isManualRefresh && isFetching && activeToken.value != null,
                state = pullRefreshState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = padding.calculateTopPadding()),
                backgroundColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TokenDetailTopBar(
    tokenId: String,
    showDetails: Boolean,
    onNavigateUp: () -> Unit,
    viewModel: TokenDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val token = (uiState as? Result.Success)?.data

    TopAppBar(
        title = {
            AnimatedContent(
                targetState = showDetails && token != null,
                transitionSpec = {
                    if (targetState) {
                        // Scrolling down: Details enter from bottom, title exits to top
                        (slideInVertically { height -> height } + fadeIn(animationSpec = tween(300, delayMillis = 100)))
                            .togetherWith(slideOutVertically { height -> -height } + fadeOut(animationSpec = tween(300)))
                    } else {
                        // Scrolling up: Title enters from top, details exit to bottom
                        (slideInVertically { height -> -height } + fadeIn(animationSpec = tween(300, delayMillis = 100)))
                            .togetherWith(slideOutVertically { height -> height } + fadeOut(animationSpec = tween(300)))
                    }.using(
                        SizeTransform(clip = false)
                    )
                },
                label = "ToolbarTitleTransition"
            ) { isDetailsVisible ->
                if (isDetailsVisible && token != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AsyncImage(
                            model = token.image,
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                        )
                        Text(
                            text = token.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = token.symbol.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.token_details),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateUp) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
fun TokenHeaderSection(token: TokenDetail) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AsyncImage(
                model = token.image,
                contentDescription = token.name,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(4.dp)
                    .clip(CircleShape)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = token.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = token.symbol.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = stringResource(R.string.rank_label, token.marketCapRank),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PriceAndChartGroup(
    token: TokenDetail,
    chartState: Result<ChartData>,
    selectedDuration: ChartDuration,
    onDurationSelected: (ChartDuration) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PriceSection(token = token)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        PriceChart(
            chartState = chartState,
            selectedDuration = selectedDuration,
            onDurationSelected = onDurationSelected,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
fun PriceSection(token: TokenDetail) {
    val currencyState = LocalCurrency.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val initialFontSize = MaterialTheme.typography.displaySmall.fontSize
        var fontSize by remember { mutableStateOf(initialFontSize) }
        var readyToDraw by remember { mutableStateOf(false) }

        Text(
            text = token.currentPrice.formatAsCurrency(
                currencyState.usdToRate,
                currencyState.currency
            ),
            style = MaterialTheme.typography.displaySmall.copy(
                fontSize = fontSize,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1,
            softWrap = false,
            onTextLayout = { textLayoutResult ->
                if (textLayoutResult.didOverflowWidth && fontSize > 24.sp) {
                    fontSize *= 0.9f
                } else {
                    readyToDraw = true
                }
            },
            modifier = Modifier.alpha(if (readyToDraw) 1f else 0f),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val isPositive = token.priceChangePercentage24h >= 0
            Icon(
                imageVector = if (isPositive) Icons.AutoMirrored.Outlined.TrendingUp else Icons.AutoMirrored.Outlined.TrendingDown,
                contentDescription = null,
                tint = if (isPositive) MaterialTheme.colorScheme.success else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "${if (isPositive) "+" else ""}${token.priceChangePercentage24h.formatTwoDecimals()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isPositive) MaterialTheme.colorScheme.success else MaterialTheme.colorScheme.error
            )
            Text(
                text = stringResource(R.string.label_24h),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MarketDataSection(token: TokenDetail) {
    val currencyState = LocalCurrency.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.market_stats),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Grid Stats
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatItem(
                    label = stringResource(R.string.market_cap),
                    value = token.marketCap.formatAsCurrency(currencyState.usdToRate, currencyState.currency),
                    modifier = Modifier.weight(1f),
                    subValue = if (token.totalSupply != null && token.totalSupply > 0) {
                        "${(token.circulatingSupply / token.totalSupply * 100).toInt()}% ${stringResource(R.string.of_supply)}"
                    } else null
                )
                StatItem(
                    label = stringResource(R.string.volume_24h),
                    value = token.totalVolume.formatAsCurrency(currencyState.usdToRate, currencyState.currency),
                    modifier = Modifier.weight(1f),
                    subValue = if (token.marketCap > 0) {
                        "${(token.totalVolume / token.marketCap * 100).formatTwoDecimals()}% ${stringResource(R.string.of_market_cap)}"
                    } else null
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatItem(
                    label = "24h High",
                    value = token.high24h.formatAsCurrency(currencyState.usdToRate, currencyState.currency),
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.ArrowUpward,
                    iconColor = MaterialTheme.colorScheme.success
                )
                StatItem(
                    label = "24h Low",
                    value = token.low24h.formatAsCurrency(currencyState.usdToRate, currencyState.currency),
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.ArrowDownward,
                    iconColor = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(20.dp))

            // Supply Info
            Text(
                text = stringResource(R.string.supply_info),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            StatRow(stringResource(R.string.circulating_supply), formatSupply(token.circulatingSupply))
            token.totalSupply?.let { StatRow(stringResource(R.string.total_supply), formatSupply(it)) }
            token.maxSupply?.let { StatRow(stringResource(R.string.max_supply), formatSupply(it)) }

            if (token.totalSupply != null && token.totalSupply > 0) {
                val percent = (token.circulatingSupply / token.totalSupply * 100).toFloat()
                Spacer(modifier = Modifier.height(16.dp))
                SupplyProgressBar(percentage = percent, label = stringResource(R.string.circulating_total))
            }
        }
    }
}

@Composable
fun AboutSection(token: TokenDetail) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.about_token, token.name),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            AboutContent(token = token)
        }
    }
}

@Composable
fun LinksAndTagsSection(token: TokenDetail) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Tags Container
            if (token.categories.isNotEmpty()) {
                Text(
                    text = "Project Tags",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        token.categories.take(8).forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Links Section
            Text(
                text = "Official Channels",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val context = LocalContext.current
                token.website?.let { url ->
                    LinkIcon(Icons.Outlined.Language, stringResource(R.string.official_website)) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                    }
                }
                token.twitter?.let { url ->
                    LinkIcon(Icons.Outlined.Close, "Twitter") {
                        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                    }
                }
                token.github?.let { url ->
                    LinkIcon(Icons.Outlined.Code, "GitHub") {
                        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                    }
                }
                token.telegram?.let { url ->
                    LinkIcon(Icons.AutoMirrored.Outlined.Send, "Telegram") {
                        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                    }
                }
            }
        }
    }
}

@Composable
fun AboutContent(token: TokenDetail) {
    var expanded by remember { mutableStateOf(false) }
    val cleanDescription = remember(token.description) {
        token.description?.replace(Regex("<[^>]*>"), "") ?: ""
    }

    Column(modifier = Modifier.animateContentSize()) {
        Text(
            text = cleanDescription,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 18.sp
        )

        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(if (expanded) stringResource(R.string.read_less) else stringResource(R.string.read_more))
        }
    }
}

@Composable
fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    subValue: String? = null
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, modifier = Modifier.size(12.dp), tint = iconColor)
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.sp
            )
        }
        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
        
        if (subValue != null) {
            Text(
                text = subValue,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(0.7f),
                maxLines = 1,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun SentimentCard(token: TokenDetail) {
    val up = token.sentimentUp ?: 50.0
    val down = token.sentimentDown ?: 50.0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.community_sentiment),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.bullish),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.success
                )
                Text(
                    text = stringResource(R.string.bearish),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(up.toFloat() / 100f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.success)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${up.formatTwoDecimals()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${down.formatTwoDecimals()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            ),
            maxLines = 1,
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun LinkIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(48.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun NewsSection(
    newsState: Result<List<NewsArticle>>,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Article,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.latest_news),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (newsState) {
                is Result.Loading -> {
                    repeat(3) { ShimmerNewsItem() }
                }

                is Result.Error -> {
                    TextButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.try_again))
                    }
                }

                is Result.Success -> {
                    val articles = newsState.data
                    if (articles.isEmpty()) {
                        Text(
                            stringResource(R.string.no_news_available),
                            modifier = Modifier.padding(vertical = 16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        articles.take(3).forEach { article ->
                            NewsItem(article = article)
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
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
    val currencyState = LocalCurrency.current

    Card(
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.price_chart),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                // Duration selector
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ChartDuration.entries.filter { it != ChartDuration.MAX }.forEach { duration ->
                        val isSelected = selectedDuration == duration
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onDurationSelected(duration) },
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Text(
                                text = duration.label,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                when (chartState) {
                    is Result.Loading -> ChartLoadingState()
                    is Result.Error -> Box(
                        Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.failed_to_load_chart),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    is Result.Success -> {
                        val chartData = chartState.data
                        if (chartData.prices.isNotEmpty()) {
                            val convertedPrices =
                                remember(chartData.prices, currencyState.usdToRate) {
                                    chartData.prices.map { it.copy(price = it.price * currencyState.usdToRate) }
                                }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                            ) {
                                PriceLineChart(
                                    pricePoints = convertedPrices,
                                    currency = currencyState.currency,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                val firstPrice = convertedPrices.first().price
                                val lastPrice = convertedPrices.last().price
                                val priceChange = lastPrice - firstPrice
                                val priceChangePercent =
                                    if (firstPrice != 0.0) (priceChange / firstPrice) * 100 else 0.0

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = stringResource(R.string.open),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = firstPrice.formatAsCurrency(1.0, currencyState.currency),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = stringResource(R.string.change),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            val isPositive = priceChange >= 0
                                            Icon(
                                                imageVector = if (isPositive) Icons.AutoMirrored.Outlined.TrendingUp else Icons.AutoMirrored.Outlined.TrendingDown,
                                                contentDescription = null,
                                                tint = if (isPositive) MaterialTheme.colorScheme.success else MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "${if (isPositive) "+" else ""}${priceChangePercent.formatTwoDecimals()}%",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isPositive) MaterialTheme.colorScheme.success else MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = stringResource(R.string.close),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = lastPrice.formatAsCurrency(1.0, currencyState.currency),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TokenDetailSkeleton(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // Header Skeleton
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.size(72.dp).clip(CircleShape).shimmer())
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.width(160.dp).height(28.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.width(60.dp).height(18.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                        Box(modifier = Modifier.width(60.dp).height(18.dp).clip(RoundedCornerShape(16.dp)).shimmer())
                    }
                }
            }
        }

        // Price Skeleton
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(modifier = Modifier.width(200.dp).height(40.dp).clip(RoundedCornerShape(8.dp)).shimmer())
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.width(100.dp).height(20.dp).clip(RoundedCornerShape(4.dp)).shimmer())
            }
        }

        // Chart Skeleton
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(332.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.width(80.dp).height(20.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                        Box(modifier = Modifier.width(140.dp).height(32.dp).clip(RoundedCornerShape(8.dp)).shimmer())
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(220.dp).shimmer())
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        repeat(3) { Box(modifier = Modifier.width(80.dp).height(28.dp).clip(RoundedCornerShape(8.dp)).shimmer()) }
                    }
                }
            }
        }

        // Sentiment Skeleton
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Box(modifier = Modifier.width(140.dp).height(20.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Box(modifier = Modifier.width(60.dp).height(16.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                        Box(modifier = Modifier.width(60.dp).height(16.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).shimmer())
                }
            }
        }

        // Market Data Skeleton
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(334.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Box(modifier = Modifier.width(100.dp).height(18.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp)).shimmer())
                        Box(modifier = Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp)).shimmer())
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    repeat(3) {
                        Box(modifier = Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartLoadingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .shimmer()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .shimmer()
                )
            }
        }
    }
}

@Composable
fun ShimmerNewsItem() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .shimmer(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {}
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
fun NewsItem(article: NewsArticle) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, article.url.toUri())) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = 0.7f
            )
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = article.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    article.source,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    formatRelativeTime(article.publishedAt),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
fun SupplyProgressBar(percentage: Float, label: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(percentage / 100f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

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
        else -> DateTimeFormatter.ofPattern("MMM d")
            .format(published.atZone(ZoneId.systemDefault()))
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.Error,
            null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.try_again)) }
    }
}
