package com.marrakechguide.feature.quote

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.marrakechguide.core.engine.PricingEngine
import com.marrakechguide.core.model.NegotiationScript
import com.marrakechguide.core.model.PriceCardDetail
import com.marrakechguide.ui.components.ListItemSkeleton
import com.marrakechguide.ui.theme.CornerRadius
import com.marrakechguide.ui.theme.FairnessFair
import com.marrakechguide.ui.theme.FairnessHigh
import com.marrakechguide.ui.theme.FairnessLow
import com.marrakechguide.ui.theme.FairnessVeryHigh
import com.marrakechguide.ui.theme.Spacing
import com.marrakechguide.ui.theme.Terracotta500
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal object QuoteActionStartWithCardPolicy {
    fun shouldStart(
        initialPriceCardId: String?,
        selectedPriceCardId: String?,
        isLoading: Boolean,
        hasHandledInitialCardLoad: Boolean
    ): Boolean {
        return !hasHandledInitialCardLoad &&
            !initialPriceCardId.isNullOrBlank() &&
            selectedPriceCardId != initialPriceCardId &&
            !isLoading
    }
}

internal object QuoteActionModifierImpactFormatter {
    fun format(factorMin: Double?, factorMax: Double?): String {
        val deltaMin = percentDelta(factorMin)
        val deltaMax = percentDelta(factorMax)
        val lower = min(deltaMin, deltaMax)
        val upper = max(deltaMin, deltaMax)

        return if (lower == upper) {
            signedPercent(lower)
        } else {
            "${signedPercent(lower)} to ${signedPercent(upper)}"
        }
    }

    private fun percentDelta(factor: Double?): Int {
        val normalizedFactor = factor?.takeIf { it.isFinite() } ?: 1.0
        val value = (normalizedFactor - 1.0) * 100.0
        val clampedValue = value.coerceIn(
            Int.MIN_VALUE.toDouble(),
            Int.MAX_VALUE.toDouble()
        )
        return clampedValue.roundToInt()
    }

    private fun signedPercent(value: Int): String {
        val sign = if (value > 0) "+" else ""
        return "$sign$value%"
    }
}

internal object QuoteActionStepDataPolicy {
    fun hasInputData(selectedPriceCard: PriceCardDetail?): Boolean {
        return selectedPriceCard != null
    }

    fun hasResultData(
        selectedPriceCard: PriceCardDetail?,
        result: PricingEngine.Output?
    ): Boolean {
        return selectedPriceCard != null && result != null
    }
}

internal object QuoteActionQuantityPolicy {
    private val perTokenRegex = Regex(
        pattern = """(?<![\p{L}\p{N}])per(?![\p{L}\p{N}])""",
        option = RegexOption.IGNORE_CASE
    )

    fun shouldShowQuantity(unit: String?): Boolean {
        return unit?.let { perTokenRegex.containsMatchIn(it) } == true
    }
}

internal object QuoteActionAmountFormatter {
    private const val unavailableAmount = "N/A"

    fun format(amount: Double): String {
        if (!amount.isFinite()) {
            return unavailableAmount
        }

        return BigDecimal.valueOf(amount)
            .setScale(2, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
    }

    fun shouldShowAdjustedRange(
        adjustedMin: Double,
        adjustedMax: Double,
        expectedMin: Int,
        expectedMax: Int
    ): Boolean {
        return format(adjustedMin) != expectedMin.toString() ||
            format(adjustedMax) != expectedMax.toString()
    }
}

/**
 * Quote → Action Screen - Price fairness checker.
 * The killer differentiator feature for checking if quoted prices are fair.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuoteActionScreen(
    viewModel: QuoteActionViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    initialPriceCardId: String? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    var hasHandledInitialCardLoad by remember(initialPriceCardId) { mutableStateOf(false) }

    // Avoid firing navigation initialization during composition.
    LaunchedEffect(
        initialPriceCardId,
        uiState.selectedPriceCard?.id,
        uiState.isLoading,
        hasHandledInitialCardLoad
    ) {
        val cardId = initialPriceCardId?.trim()?.takeIf { it.isNotEmpty() }
        if (hasHandledInitialCardLoad || cardId == null) {
            return@LaunchedEffect
        }

        if (uiState.selectedPriceCard?.id == cardId) {
            hasHandledInitialCardLoad = true
            return@LaunchedEffect
        }

        if (QuoteActionStartWithCardPolicy.shouldStart(
                initialPriceCardId = cardId,
                selectedPriceCardId = uiState.selectedPriceCard?.id,
                isLoading = uiState.isLoading,
                hasHandledInitialCardLoad = hasHandledInitialCardLoad
            )
        ) {
            hasHandledInitialCardLoad = true
            viewModel.startWithPriceCard(cardId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(getTitle(uiState.step)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.step == QuoteStep.CATEGORY) {
                            onNavigateBack()
                        } else {
                            viewModel.goBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            ListItemSkeleton(modifier = Modifier.padding(paddingValues))
        } else {
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
            ) {
                uiState.error?.let { message ->
                    ErrorBanner(
                        message = message,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                    )
                }

                AnimatedContent(
                    targetState = uiState.step,
                    transitionSpec = {
                        if (targetState.ordinal > initialState.ordinal) {
                            (slideInHorizontally { it } + fadeIn()) togetherWith
                                (slideOutHorizontally { -it } + fadeOut())
                        } else {
                            (slideInHorizontally { -it } + fadeIn()) togetherWith
                                (slideOutHorizontally { it } + fadeOut())
                        }
                    },
                    label = "step_transition",
                    modifier = Modifier.weight(1f)
                ) { step ->
                    when (step) {
                        QuoteStep.CATEGORY -> CategorySelectionContent(
                            onSelectCategory = viewModel::selectCategory
                        )
                        QuoteStep.PRICE_CARD -> PriceCardSelectionContent(
                            priceCards = uiState.priceCards,
                            onSelectCard = viewModel::selectPriceCard
                        )
                        QuoteStep.INPUT -> {
                            val selectedPriceCard = uiState.selectedPriceCard
                            if (!QuoteActionStepDataPolicy.hasInputData(selectedPriceCard)) {
                                MissingStepDataContent(
                                    message = "Select an item to continue.",
                                    actionLabel = "Choose Item",
                                    onAction = viewModel::goBack
                                )
                            } else {
                                QuoteInputContent(
                                    priceCard = requireNotNull(selectedPriceCard),
                                    quotedAmount = uiState.quotedAmount,
                                    quantity = uiState.quantity,
                                    modifierSelections = uiState.modifierSelections,
                                    onAmountChange = viewModel::updateQuotedAmount,
                                    onQuantityChange = viewModel::updateQuantity,
                                    onToggleModifier = viewModel::toggleModifier,
                                    onEvaluate = viewModel::evaluate,
                                    canEvaluate = viewModel.canEvaluate()
                                )
                            }
                        }
                        QuoteStep.RESULT -> {
                            val selectedPriceCard = uiState.selectedPriceCard
                            val result = uiState.result
                            if (!QuoteActionStepDataPolicy.hasResultData(selectedPriceCard, result)) {
                                MissingStepDataContent(
                                    message = "Could not load the result. Please check the price again.",
                                    actionLabel = "Start Over",
                                    onAction = viewModel::reset
                                )
                            } else {
                                FairnessResultContent(
                                    priceCard = requireNotNull(selectedPriceCard),
                                    quotedAmount = uiState.quotedAmount.toDoubleOrNull() ?: 0.0,
                                    result = requireNotNull(result),
                                    scripts = uiState.scripts,
                                    onCheckAnother = viewModel::reset
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
private fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(CornerRadius.md)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun MissingStepDataContent(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.md),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ErrorBanner(
            message = message,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        OutlinedButton(onClick = onAction) {
            Text(actionLabel)
        }
    }
}

private fun getTitle(step: QuoteStep): String {
    return when (step) {
        QuoteStep.CATEGORY -> "Check a Price"
        QuoteStep.PRICE_CARD -> "Select Item"
        QuoteStep.INPUT -> "Enter Quote"
        QuoteStep.RESULT -> "Result"
    }
}

// MARK: - Category Selection

@Composable
private fun CategorySelectionContent(
    onSelectCategory: (PriceCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text(
            text = "What are you checking?",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = Spacing.sm)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            items(PriceCategory.entries) { category ->
                CategoryCard(
                    category = category,
                    onClick = { onSelectCategory(category) }
                )
            }
        }
    }
}

@Composable
private fun CategoryCard(
    category: PriceCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Category: ${category.displayName}" },
        shape = RoundedCornerShape(CornerRadius.lg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = getCategoryIcon(category),
                contentDescription = null,
                tint = Terracotta500,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = category.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun getCategoryIcon(category: PriceCategory): ImageVector {
    return when (category) {
        PriceCategory.TAXI -> Icons.Default.DirectionsCar
        PriceCategory.HAMMAM -> Icons.Default.Spa
        PriceCategory.SOUKS -> Icons.Default.ShoppingBag
        PriceCategory.FOOD -> Icons.Default.Restaurant
        PriceCategory.GUIDES -> Icons.Default.SupportAgent
        PriceCategory.ACTIVITIES -> Icons.Default.Hiking
    }
}

// MARK: - Price Card Selection

@Composable
private fun PriceCardSelectionContent(
    priceCards: List<PriceCardDetail>,
    onSelectCard: (PriceCardDetail) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        items(priceCards) { card ->
            PriceCardRow(
                card = card,
                onClick = { onSelectCard(card) }
            )
        }
    }
}

@Composable
private fun PriceCardRow(
    card: PriceCardDetail,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accessibilityLabel = "${card.title}, ${card.expectedCostMinMad} to ${card.expectedCostMaxMad} Moroccan dirhams ${card.unit ?: ""}"
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = accessibilityLabel },
        shape = RoundedCornerShape(CornerRadius.md)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${card.expectedCostMinMad}-${card.expectedCostMaxMad} MAD ${card.unit ?: ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// MARK: - Quote Input

@Composable
private fun QuoteInputContent(
    priceCard: PriceCardDetail,
    quotedAmount: String,
    quantity: Int,
    modifierSelections: List<ModifierSelection>,
    onAmountChange: (String) -> Unit,
    onQuantityChange: (Int) -> Unit,
    onToggleModifier: (String) -> Unit,
    onEvaluate: () -> Unit,
    canEvaluate: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        // Price card info
        Card(
            shape = RoundedCornerShape(CornerRadius.md),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(Spacing.md)) {
                Text(
                    text = priceCard.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Expected: ${priceCard.expectedCostMinMad}-${priceCard.expectedCostMaxMad} MAD",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Terracotta500
                )
            }
        }

        // Quote input
        Column {
            Text(
                text = "Enter quoted price",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(bottom = Spacing.sm)
            )
            OutlinedTextField(
                value = quotedAmount,
                onValueChange = onAmountChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("0") },
                suffix = { Text("MAD") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (canEvaluate) onEvaluate() }
                ),
                singleLine = true,
                shape = RoundedCornerShape(CornerRadius.md)
            )
        }

        // Quantity (if unit allows)
        if (QuoteActionQuantityPolicy.shouldShowQuantity(priceCard.unit)) {
            QuantitySelector(
                quantity = quantity,
                unit = priceCard.unit,
                onQuantityChange = onQuantityChange
            )
        }

        // Modifiers
        if (modifierSelections.isNotEmpty()) {
            Column {
                Text(
                    text = "Context (affects expected price)",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = Spacing.sm)
                )
                modifierSelections.forEach { selection ->
                    ModifierToggle(
                        selection = selection,
                        onToggle = { onToggleModifier(selection.modifier.id) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Evaluate button
        Button(
            onClick = onEvaluate,
            enabled = canEvaluate,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Terracotta500),
            shape = RoundedCornerShape(CornerRadius.md)
        ) {
            Text("Check Fairness", modifier = Modifier.padding(vertical = Spacing.xs))
        }
    }
}

@Composable
private fun QuantitySelector(
    quantity: Int,
    unit: String?,
    onQuantityChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Quantity",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = Spacing.sm)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            FilledIconButton(
                onClick = { onQuantityChange(quantity - 1) },
                enabled = quantity > 1,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease")
            }

            Text(
                text = "$quantity",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(48.dp),
                textAlign = TextAlign.Center
            )

            FilledIconButton(
                onClick = { onQuantityChange(quantity + 1) },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase")
            }

            unit?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ModifierToggle(
    selection: ModifierSelection,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = selection.modifier.label,
                style = MaterialTheme.typography.bodyMedium
            )
            val impact = QuoteActionModifierImpactFormatter.format(
                factorMin = selection.modifier.factorMin,
                factorMax = selection.modifier.factorMax
            )
            Text(
                text = impact,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = selection.isSelected,
            onCheckedChange = { onToggle() }
        )
    }
}

// MARK: - Fairness Result

@Composable
private fun FairnessResultContent(
    priceCard: PriceCardDetail,
    quotedAmount: Double,
    result: PricingEngine.Output,
    scripts: List<NegotiationScript>,
    onCheckAnother: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        // Fairness Meter
        FairnessMeter(
            fairness = result.fairness,
            quotedAmount = quotedAmount
        )

        // Price Comparison
        PriceComparisonCard(
            priceCard = priceCard,
            quotedAmount = quotedAmount,
            result = result
        )

        // Verdict & Action
        VerdictCard(fairness = result.fairness, result = result)

        // Negotiation Scripts (if fair or high)
        if (scripts.isNotEmpty() &&
            (result.fairness == PricingEngine.FairnessLevel.FAIR ||
             result.fairness == PricingEngine.FairnessLevel.HIGH)) {
            NegotiationScriptsCard(scripts = scripts)
        }

        // Warning for low prices
        if (result.fairness == PricingEngine.FairnessLevel.LOW) {
            LowPriceWarning()
        }

        // Walk away guidance for very high
        if (result.fairness == PricingEngine.FairnessLevel.VERY_HIGH) {
            WalkAwayGuidance(priceCard = priceCard)
        }

        Spacer(modifier = Modifier.weight(1f))

        // Check another button
        OutlinedButton(
            onClick = onCheckAnother,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CornerRadius.md)
        ) {
            Text("Check Another Price")
        }
    }
}

@Composable
private fun FairnessMeter(
    fairness: PricingEngine.FairnessLevel,
    quotedAmount: Double,
    modifier: Modifier = Modifier
) {
    val (color, label) = when (fairness) {
        PricingEngine.FairnessLevel.LOW -> FairnessLow to "Suspiciously Cheap"
        PricingEngine.FairnessLevel.FAIR -> FairnessFair to "Fair Price"
        PricingEngine.FairnessLevel.HIGH -> FairnessHigh to "Slightly High"
        PricingEngine.FairnessLevel.VERY_HIGH -> FairnessVeryHigh to "Too Expensive"
    }

    val quotedDisplayAmount = QuoteActionAmountFormatter.format(quotedAmount)
    val accessibilityDescription = "Fairness result: $label for quote of $quotedDisplayAmount Moroccan dirhams"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = accessibilityDescription
                liveRegion = LiveRegionMode.Polite
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Large circular indicator
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f))
                .border(4.dp, color, CircleShape)
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = quotedDisplayAmount,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    text = "MAD",
                    style = MaterialTheme.typography.labelMedium,
                    color = color
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.clearAndSetSemantics { }
        )

        // Horizontal meter bar
        Spacer(modifier = Modifier.height(Spacing.md))
        FairnessBar(fairness = fairness)
    }
}

@Composable
private fun FairnessBar(
    fairness: PricingEngine.FairnessLevel,
    modifier: Modifier = Modifier
) {
    val segments = listOf(
        FairnessLow to "Low",
        FairnessFair to "Fair",
        FairnessHigh to "High",
        FairnessVeryHigh to "Very High"
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        segments.forEachIndexed { index, (color, _) ->
            val isActive = index == fairness.ordinal
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(if (isActive) 12.dp else 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isActive) color else color.copy(alpha = 0.3f))
            )
        }
    }
}

@Composable
private fun PriceComparisonCard(
    priceCard: PriceCardDetail,
    quotedAmount: Double,
    result: PricingEngine.Output,
    modifier: Modifier = Modifier
) {
    val adjustedMinDisplay = QuoteActionAmountFormatter.format(result.adjustedMin)
    val adjustedMaxDisplay = QuoteActionAmountFormatter.format(result.adjustedMax)
    val quotedDisplayAmount = QuoteActionAmountFormatter.format(quotedAmount)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            ComparisonRow(
                label = "Expected",
                value = "${priceCard.expectedCostMinMad}-${priceCard.expectedCostMaxMad} MAD"
            )
            if (QuoteActionAmountFormatter.shouldShowAdjustedRange(
                    adjustedMin = result.adjustedMin,
                    adjustedMax = result.adjustedMax,
                    expectedMin = priceCard.expectedCostMinMad,
                    expectedMax = priceCard.expectedCostMaxMad
                )
            ) {
                ComparisonRow(
                    label = "Adjusted",
                    value = "$adjustedMinDisplay-$adjustedMaxDisplay MAD",
                    isHighlighted = true
                )
            }
            ComparisonRow(
                label = "Your quote",
                value = "$quotedDisplayAmount MAD",
                isBold = true
            )
        }
    }
}

@Composable
private fun ComparisonRow(
    label: String,
    value: String,
    isHighlighted: Boolean = false,
    isBold: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isHighlighted) Terracotta500 else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = if (isHighlighted) Terracotta500 else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun VerdictCard(
    fairness: PricingEngine.FairnessLevel,
    result: PricingEngine.Output,
    modifier: Modifier = Modifier
) {
    val action = PricingEngine.suggestedAction(fairness)
    val showCounter = fairness == PricingEngine.FairnessLevel.HIGH ||
                      fairness == PricingEngine.FairnessLevel.VERY_HIGH
    val counterMinDisplay = QuoteActionAmountFormatter.format(result.counterMin)
    val counterMaxDisplay = QuoteActionAmountFormatter.format(result.counterMax)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.md)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = action,
                style = MaterialTheme.typography.bodyLarge
            )
            if (showCounter) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachMoney,
                        contentDescription = null,
                        tint = FairnessFair,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Try: $counterMinDisplay-$counterMaxDisplay MAD",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = FairnessFair
                    )
                }
            }
        }
    }
}

@Composable
private fun NegotiationScriptsCard(
    scripts: List<NegotiationScript>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.md)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = "Negotiation Phrases",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            scripts.forEach { script ->
                ScriptItem(script = script)
            }
        }
    }
}

@Composable
private fun ScriptItem(
    script: NegotiationScript,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Text(
            text = script.darijaLatin,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        script.darijaArabic?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text(
            text = script.english,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LowPriceWarning(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.md),
        colors = CardDefaults.cardColors(
            containerColor = FairnessLow.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = FairnessLow
            )
            Column {
                Text(
                    text = "Verify before agreeing",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "A price this low may indicate:\n- Lower quality service\n- Hidden fees\n- A potential scam\n\nAsk clarifying questions first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WalkAwayGuidance(
    priceCard: PriceCardDetail,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.md),
        colors = CardDefaults.cardColors(
            containerColor = FairnessVeryHigh.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = FairnessVeryHigh
                )
                Text(
                    text = "Consider walking away",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (priceCard.whatToDoInstead.isNotEmpty()) {
                Text(
                    text = "Alternatives:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
                priceCard.whatToDoInstead.forEach { alternative ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = FairnessFair,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = alternative,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
