package com.marrakechguide.feature.quote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marrakechguide.core.engine.PricingEngine
import com.marrakechguide.core.model.ContextModifier
import com.marrakechguide.core.model.NegotiationScript
import com.marrakechguide.core.model.PriceCardDetail
import com.marrakechguide.core.repository.PriceCardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Price card category for quick selection.
 */
enum class PriceCategory(val displayName: String, val dbValue: String) {
    TAXI("Taxi", "taxi"),
    HAMMAM("Hammam", "hammam"),
    SOUKS("Souks", "souks"),
    FOOD("Food", "food"),
    GUIDES("Guide", "guides"),
    ACTIVITIES("Activities", "activities")
}

/**
 * Steps in the Quote → Action flow.
 */
enum class QuoteStep {
    CATEGORY,      // Select category
    PRICE_CARD,    // Select specific price card
    INPUT,         // Enter quote amount
    RESULT         // View fairness result
}

/**
 * Modifier selection state.
 */
data class ModifierSelection(
    val modifier: ContextModifier,
    val isSelected: Boolean
)

/**
 * UI state for the Quote → Action screen.
 */
data class QuoteUiState(
    val step: QuoteStep = QuoteStep.CATEGORY,
    val isLoading: Boolean = false,
    val error: String? = null,

    // Category selection
    val selectedCategory: PriceCategory? = null,

    // Price card selection
    val priceCards: List<PriceCardDetail> = emptyList(),
    val selectedPriceCard: PriceCardDetail? = null,

    // Quote input
    val quotedAmount: String = "",
    val quantity: Int = 1,
    val modifierSelections: List<ModifierSelection> = emptyList(),

    // Result
    val result: PricingEngine.Output? = null,
    val scripts: List<NegotiationScript> = emptyList()
)

/**
 * ViewModel for the Quote → Action feature.
 * Handles the flow of checking price fairness.
 */
@HiltViewModel
class QuoteActionViewModel @Inject constructor(
    private val priceCardRepository: PriceCardRepository
) : ViewModel() {
    private companion object {
        private val minusSigns = setOf('-', '−', '﹣', '－')
        private const val arabicDecimalSeparator = '٫'
        private const val arabicThousandSeparator = '٬'
        private const val arabicComma = '،'
        private const val fullWidthDecimalSeparator = '．'
        private const val fullWidthCommaSeparator = '，'
        private const val ideographicDecimalSeparator = '。'
        private const val halfWidthIdeographicCommaSeparator = '､'
    }

    private val _uiState = MutableStateFlow(QuoteUiState())
    val uiState: StateFlow<QuoteUiState> = _uiState.asStateFlow()
    private var activeLoadJob: Job? = null
    private var activeLoadVersion: Long = 0

    private fun beginLoad(): Long {
        activeLoadVersion += 1
        return activeLoadVersion
    }

    private fun isStaleLoad(version: Long): Boolean {
        return version != activeLoadVersion
    }

    private fun Throwable.messageOrFallback(fallback: String): String {
        val normalizedMessage = message?.trim().orEmpty()
        return if (normalizedMessage.isNotEmpty()) normalizedMessage else fallback
    }

    /**
     * Selects a price category and loads matching price cards.
     */
    fun selectCategory(category: PriceCategory) {
        activeLoadJob?.cancel()
        val loadVersion = beginLoad()
        activeLoadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    step = QuoteStep.CATEGORY,
                    selectedCategory = category,
                    selectedPriceCard = null,
                    priceCards = emptyList(),
                    modifierSelections = emptyList(),
                    quotedAmount = "",
                    quantity = 1,
                    result = null,
                    scripts = emptyList()
                )
            }

            try {
                val cards = priceCardRepository.getPriceCardsByCategoryOnce(category.dbValue)
                if (isStaleLoad(loadVersion)) {
                    return@launch
                }

                if (cards.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            step = QuoteStep.CATEGORY,
                            error = "No price cards available for this category"
                        )
                    }
                } else if (cards.size == 1) {
                    // Keep card list in sync so back-navigation policy remains correct.
                    _uiState.update { it.copy(priceCards = cards) }
                    selectPriceCard(cards.first())
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            priceCards = cards,
                            step = QuoteStep.PRICE_CARD
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isStaleLoad(loadVersion)) {
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.messageOrFallback("Unable to load price cards")
                    )
                }
            }
        }
    }

    /**
     * Selects a specific price card for evaluation.
     */
    fun selectPriceCard(card: PriceCardDetail) {
        val modifierSelections = card.contextModifiers.map { modifier ->
            ModifierSelection(
                modifier = modifier,
                isSelected = false
            )
        }

        _uiState.update {
            it.copy(
                isLoading = false,
                error = null,
                selectedPriceCard = card,
                modifierSelections = modifierSelections,
                scripts = card.negotiationScripts,
                quotedAmount = "",
                quantity = 1,
                result = null,
                step = QuoteStep.INPUT
            )
        }
    }

    /**
     * Updates the quoted amount input.
     */
    fun updateQuotedAmount(amount: String) {
        val filtered = sanitizeQuotedAmount(amount)
        _uiState.update { it.copy(quotedAmount = filtered) }
    }

    /**
     * Updates the quantity.
     */
    fun updateQuantity(quantity: Int) {
        if (quantity >= 1) {
            _uiState.update { it.copy(quantity = quantity) }
        }
    }

    /**
     * Toggles a modifier selection.
     */
    fun toggleModifier(modifierId: String) {
        _uiState.update { state ->
            val updated = state.modifierSelections.map { selection ->
                if (selection.modifier.id == modifierId) {
                    selection.copy(isSelected = !selection.isSelected)
                } else {
                    selection
                }
            }
            state.copy(modifierSelections = updated)
        }
    }

    /**
     * Evaluates the quoted price and shows the result.
     */
    fun evaluate() {
        val state = _uiState.value
        val card = state.selectedPriceCard ?: return
        val quotedMad = parseValidQuotedAmount(state.quotedAmount) ?: return

        // Convert selected modifiers to PricingEngine format
        val modifiers = state.modifierSelections
            .filter { it.isSelected }
            .map { selection ->
                PricingEngine.ContextModifier(
                    factorMin = selection.modifier.factorMin ?: 1.0,
                    factorMax = selection.modifier.factorMax ?: 1.0
                )
            }

        val input = PricingEngine.Input(
            expectedCostMinMad = card.expectedCostMinMad.toDouble(),
            expectedCostMaxMad = card.expectedCostMaxMad.toDouble(),
            quotedMad = quotedMad,
            modifiers = modifiers,
            quantity = state.quantity,
            fairnessLowMultiplier = card.fairnessLowMultiplier ?: 0.75,
            fairnessHighMultiplier = card.fairnessHighMultiplier ?: 1.25
        )

        val result = PricingEngine.evaluate(input)

        _uiState.update {
            it.copy(result = result, step = QuoteStep.RESULT)
        }
    }

    /**
     * Returns to the previous step.
     */
    fun goBack() {
        _uiState.update { state ->
            val previousStep = when (state.step) {
                QuoteStep.CATEGORY -> QuoteStep.CATEGORY
                QuoteStep.PRICE_CARD -> QuoteStep.CATEGORY
                QuoteStep.INPUT -> {
                    if (state.priceCards.size <= 1) QuoteStep.CATEGORY
                    else QuoteStep.PRICE_CARD
                }
                QuoteStep.RESULT -> QuoteStep.INPUT
            }
            state.copy(step = previousStep, result = null)
        }
    }

    /**
     * Resets to start a new evaluation.
     */
    fun reset() {
        activeLoadJob?.cancel()
        activeLoadJob = null
        activeLoadVersion += 1
        _uiState.value = QuoteUiState()
    }

    /**
     * Starts evaluation with a specific price card (from deep link or price card detail).
     */
    fun startWithPriceCard(cardId: String) {
        val normalizedCardId = cardId.trim()
        activeLoadJob?.cancel()
        val loadVersion = beginLoad()
        activeLoadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    step = QuoteStep.CATEGORY,
                    selectedCategory = null,
                    selectedPriceCard = null,
                    priceCards = emptyList(),
                    modifierSelections = emptyList(),
                    quotedAmount = "",
                    quantity = 1,
                    result = null,
                    scripts = emptyList()
                )
            }
            if (normalizedCardId.isEmpty()) {
                if (isStaleLoad(loadVersion)) {
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        step = QuoteStep.CATEGORY,
                        error = "Price card not found"
                    )
                }
                return@launch
            }

            try {
                val card = priceCardRepository.getPriceCard(normalizedCardId)
                if (isStaleLoad(loadVersion)) {
                    return@launch
                }
                if (card != null) {
                    selectPriceCard(card)
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            step = QuoteStep.CATEGORY,
                            error = "Price card not found"
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isStaleLoad(loadVersion)) {
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        step = QuoteStep.CATEGORY,
                        error = e.messageOrFallback("Unable to load price card")
                    )
                }
            }
        }
    }

    /**
     * Checks if the evaluate button should be enabled.
     */
    fun canEvaluate(): Boolean {
        val state = _uiState.value
        return state.selectedPriceCard != null &&
               parseValidQuotedAmount(state.quotedAmount) != null
    }

    private fun parseValidQuotedAmount(rawAmount: String): Double? {
        val parsed = rawAmount.toDoubleOrNull() ?: return null
        return parsed.takeIf { it.isFinite() && it > 0.0 }
    }

    private fun sanitizeQuotedAmount(rawAmount: String): String {
        val hasLeadingMinus = rawAmount.firstOrNull { !it.isWhitespace() } in minusSigns
        fun withSign(value: String): String {
            return if (hasLeadingMinus) "-$value" else value
        }

        val filtered = buildString {
            rawAmount.forEach { character ->
                when (character) {
                    '.', arabicDecimalSeparator, fullWidthDecimalSeparator, ideographicDecimalSeparator -> append('.')
                    ',', arabicThousandSeparator, arabicComma, fullWidthCommaSeparator, halfWidthIdeographicCommaSeparator -> append(',')
                    else -> {
                        val normalizedDigit = Character.digit(character, 10)
                        if (normalizedDigit in 0..9) {
                            append(('0'.code + normalizedDigit).toChar())
                        }
                    }
                }
            }
        }
        if (filtered.isEmpty()) {
            return if (hasLeadingMinus) "-" else ""
        }

        val lastDot = filtered.lastIndexOf('.')
        val lastComma = filtered.lastIndexOf(',')
        val hasDot = lastDot >= 0
        val hasComma = lastComma >= 0

        // Mixed separators usually mean grouped thousands + decimal marker.
        if (hasDot && hasComma) {
            val decimalIndex = maxOf(lastDot, lastComma)
            return withSign(buildString {
                filtered.forEachIndexed { index, character ->
                    when {
                        character.isDigit() -> append(character)
                        index == decimalIndex -> append('.')
                    }
                }
            })
        }

        val separator = when {
            hasDot -> '.'
            hasComma -> ','
            else -> return withSign(filtered)
        }

        val separatorCount = filtered.count { it == separator }
        val groups = filtered.split(separator)
        val firstGroup = groups.firstOrNull().orEmpty()
        val secondGroup = groups.getOrNull(1).orEmpty()
        val firstGroupHasNonZeroDigit = firstGroup.any { character -> character != '0' }
        val firstGroupHasLeadingZero = firstGroup.length > 1 && firstGroup.first() == '0'
        val singleSeparatorLeadingZeroThousands = separatorCount == 1 &&
            firstGroupHasLeadingZero &&
            secondGroup == "000"
        val firstGroupLooksGrouped = firstGroup.isNotEmpty() &&
            firstGroup.length <= 3 &&
            (
                separatorCount > 1 ||
                    (
                        firstGroupHasNonZeroDigit &&
                            (!firstGroupHasLeadingZero || singleSeparatorLeadingZeroThousands)
                        )
                )

        val looksLikeThousandsGrouping = separatorCount >= 1 &&
            groups.all { part -> part.all { character -> character.isDigit() } } &&
            firstGroupLooksGrouped &&
            groups.drop(1).all { part -> part.length == 3 }

        if (looksLikeThousandsGrouping) {
            return withSign(filtered.filter { character -> character.isDigit() })
        }

        // Fallback: keep a single separator and normalize it to '.'
        var hasDecimalPoint = false
        return withSign(buildString {
            filtered.forEach { character ->
                when {
                    character.isDigit() -> append(character)
                    character == separator && !hasDecimalPoint -> {
                        append('.')
                        hasDecimalPoint = true
                    }
                }
            }
        })
    }
}
