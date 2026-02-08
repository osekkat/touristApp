package com.marrakechguide.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.marrakechguide.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * Standard screen state model used by all feature screens.
 */
sealed class ScreenState<out T> {
    data object Loading : ScreenState<Nothing>()
    data class Content<T>(val data: T) : ScreenState<T>()
    data class Refreshing<T>(val data: T) : ScreenState<T>()
    data class Offline<T>(val cachedData: T?) : ScreenState<T>()
    data class Error(
        val throwable: Throwable,
        val message: String = throwable.localizedMessage ?: "Something went wrong"
    ) : ScreenState<Nothing>()
}

data class ErrorAction(
    val label: String,
    val onClick: () -> Unit,
)

@Composable
fun ContentCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardModifier = modifier.fillMaxWidth()
    val shape = RoundedCornerShape(12.dp)
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)

    if (onClick != null) {
        Card(
            modifier = cardModifier,
            onClick = onClick,
            shape = shape,
            colors = colors,
        ) {
            Column(modifier = Modifier.padding(Spacing.md)) {
                if (!title.isNullOrBlank()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                }
                content()
            }
        }
    } else {
        Card(
            modifier = cardModifier,
            shape = shape,
            colors = colors,
        ) {
            Column(modifier = Modifier.padding(Spacing.md)) {
                if (!title.isNullOrBlank()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                }
                content()
            }
        }
    }
}

@Composable
fun CategoryChip(
    text: String,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = text) },
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
    )
}

@Composable
fun PriceTag(
    minMad: Int?,
    maxMad: Int?,
    modifier: Modifier = Modifier,
) {
    Text(
        text = formatPriceRange(minMad = minMad, maxMad = maxMad),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

@Composable
fun SectionHeader(
    title: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (!actionText.isNullOrBlank()) {
            TextButton(
                onClick = { onAction?.invoke() },
                enabled = onAction != null,
            ) {
                Text(actionText)
            }
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    alternativeActions: List<ErrorAction> = emptyList(),
) {
    ErrorContent(
        message = message,
        onRetry = onRetry,
        alternativeActions = alternativeActions,
        modifier = modifier,
    )
}

@Composable
fun SkeletonLoader(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                brush = shimmerBrush(),
                shape = RoundedCornerShape(8.dp),
            )
    )
}

private fun formatPriceRange(minMad: Int?, maxMad: Int?): String {
    return when {
        minMad != null && maxMad != null && minMad == maxMad -> "~$minMad MAD"
        minMad != null && maxMad != null -> "$minMad-$maxMad MAD"
        minMad != null -> "$minMad+ MAD"
        maxMad != null -> "Up to $maxMad MAD"
        else -> "Price unavailable"
    }
}

@Composable
fun LoadingContent(
    modifier: Modifier = Modifier,
    skeleton: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "Loading"
                liveRegion = LiveRegionMode.Polite
            }
    ) {
        skeleton()
    }
}

@Composable
fun OfflineBanner(
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    message: String = "Offline • Core guide still works",
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (onDismiss != null) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss offline message",
                    )
                }
            }
        }
    }
}

@Composable
fun ErrorContent(
    message: String,
    onRetry: (() -> Unit)?,
    alternativeActions: List<ErrorAction> = emptyList(),
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg)
            .semantics {
                contentDescription = "Error state"
                liveRegion = LiveRegionMode.Polite
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = Spacing.sm),
        )
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(Spacing.lg))

        if (onRetry != null) {
            Button(onClick = onRetry) {
                Text("Retry")
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
        }

        alternativeActions.forEach { action ->
            OutlinedButton(onClick = action.onClick) {
                Text(action.label)
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ScreenStateContent(
    state: ScreenState<T>,
    modifier: Modifier = Modifier,
    onRefresh: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    alternativeErrorActions: List<ErrorAction> = emptyList(),
    loading: @Composable () -> Unit = { ListItemSkeleton() },
    content: @Composable (T) -> Unit,
) {
    when (state) {
        ScreenState.Loading -> {
            LoadingContent(
                modifier = modifier,
                skeleton = loading,
            )
        }

        is ScreenState.Error -> {
            ErrorContent(
                message = state.message,
                onRetry = onRetry,
                alternativeActions = alternativeErrorActions,
                modifier = modifier,
            )
        }

        is ScreenState.Content -> {
            RefreshContainer(
                isRefreshing = false,
                onRefresh = onRefresh,
                modifier = modifier,
            ) {
                content(state.data)
            }
        }

        is ScreenState.Refreshing -> {
            Column(modifier = modifier.fillMaxSize()) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                RefreshContainer(
                    isRefreshing = true,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    content(state.data)
                }
            }
        }

        is ScreenState.Offline -> {
            Column(modifier = modifier.fillMaxSize()) {
                OfflineBanner()
                val cachedData = state.cachedData
                if (cachedData != null) {
                    RefreshContainer(
                        isRefreshing = false,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        content(cachedData)
                    }
                } else {
                    ErrorContent(
                        message = "Offline and no cached data is available yet.",
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefreshContainer(
    isRefreshing: Boolean,
    onRefresh: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (onRefresh == null) {
        Box(modifier = modifier.fillMaxSize()) {
            content()
        }
        return
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        content()
    }
}

@Composable
fun ListItemSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 5,
) {
    val brush = shimmerBrush()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.md),
    ) {
        repeat(rows) {
            CardSkeleton(brush = brush)
            Spacer(modifier = Modifier.height(Spacing.sm))
        }
    }
}

@Composable
fun CardSkeleton(
    modifier: Modifier = Modifier,
    brush: Brush = shimmerBrush(),
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(20.dp)
                    .background(brush, RoundedCornerShape(6.dp))
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .background(brush, RoundedCornerShape(6.dp))
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Spacer(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(14.dp)
                    .background(brush, RoundedCornerShape(6.dp))
            )
        }
    }
}

@Composable
fun DetailSkeleton(
    modifier: Modifier = Modifier,
) {
    val brush = shimmerBrush()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.md),
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(brush, RoundedCornerShape(12.dp))
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        Spacer(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(28.dp)
                .background(brush, RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .background(brush, RoundedCornerShape(6.dp))
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Spacer(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(16.dp)
                .background(brush, RoundedCornerShape(6.dp))
        )
    }
}

@Composable
private fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "skeleton_shimmer")
    val offset by transition.animateFloat(
        initialValue = -600f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "skeleton_shimmer_offset",
    )

    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface

    return Brush.linearGradient(
        colors = listOf(base.copy(alpha = 0.65f), highlight.copy(alpha = 0.95f), base.copy(alpha = 0.65f)),
        start = Offset(offset, 0f),
        end = Offset(offset + 280f, 280f),
    )
}

/**
 * Temporary placeholder implementation for current feature shells.
 * Feature view models can replace this with real state wiring incrementally.
 */
@Composable
fun PlaceholderStateScreen(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    var state by remember {
        mutableStateOf<ScreenState<Unit>>(ScreenState.Loading)
    }

    LaunchedEffect(Unit) {
        delay(200)
        state = ScreenState.Content(Unit)
    }

    LaunchedEffect(state) {
        if (state is ScreenState.Refreshing) {
            delay(350)
            state = ScreenState.Content(Unit)
        }
    }

    ScreenStateContent(
        state = state,
        modifier = modifier,
        onRefresh = {
            if (state is ScreenState.Content || state is ScreenState.Offline) {
                state = ScreenState.Refreshing(Unit)
            }
        },
        onRetry = {
            state = ScreenState.Content(Unit)
        },
        loading = { ListItemSkeleton() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
