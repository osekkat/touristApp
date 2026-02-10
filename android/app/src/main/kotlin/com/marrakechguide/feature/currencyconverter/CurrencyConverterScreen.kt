package com.marrakechguide.feature.currencyconverter

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import com.marrakechguide.ui.theme.Spacing

/**
 * Currency Converter screen for quick MAD ↔ Home Currency conversion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyConverterScreen(
    viewModel: CurrencyConverterViewModel = remember { CurrencyConverterViewModel() }
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var madFieldFocused by remember { mutableStateOf(false) }
    var homeFieldFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadPreferences(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Currency Converter") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            // Rate info card
            RateInfoCard(
                currency = uiState.selectedCurrency,
                rate = uiState.exchangeRate
            )

            // Converter inputs
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // MAD input
                CurrencyInputField(
                    label = "Moroccan Dirham",
                    currencyCode = "MAD",
                    flag = "\uD83C\uDDF2\uD83C\uDDE6",
                    value = uiState.madAmount,
                    onValueChange = { value ->
                        viewModel.setMadAmount(value)
                        if (madFieldFocused) {
                            viewModel.convertFromMAD()
                        }
                    },
                    isFocused = madFieldFocused,
                    onFocusChange = { madFieldFocused = it }
                )

                // Swap indicator
                Icon(
                    imageVector = Icons.Default.SwapVert,
                    contentDescription = "Swap",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Home currency input
                CurrencyInputField(
                    label = uiState.selectedCurrency.name,
                    currencyCode = uiState.selectedCurrency.code,
                    flag = uiState.selectedCurrency.flag,
                    value = uiState.homeAmount,
                    onValueChange = { value ->
                        viewModel.setHomeAmount(value)
                        if (homeFieldFocused) {
                            viewModel.convertFromHome()
                        }
                    },
                    isFocused = homeFieldFocused,
                    onFocusChange = { homeFieldFocused = it }
                )
            }

            // Quick amounts
            QuickAmountsGrid(
                onAmountSelected = { amount ->
                    viewModel.setMadAmount(amount.toString())
                    viewModel.convertFromMAD()
                }
            )

            // Currency selector
            CurrencySelector(
                selectedCurrency = uiState.selectedCurrency,
                onClick = { showCurrencyPicker = true }
            )
        }
    }

    // Currency picker dialog
    if (showCurrencyPicker) {
        CurrencyPickerDialog(
            currencies = CurrencyConverterViewModel.supportedCurrencies,
            selectedCurrency = uiState.selectedCurrency,
            onCurrencySelected = { currency ->
                viewModel.selectCurrency(currency)
                viewModel.savePreferences(context)
                showCurrencyPicker = false
            },
            onDismiss = { showCurrencyPicker = false }
        )
    }
}

/**
 * ViewModel for Currency Converter.
 */
class CurrencyConverterViewModel : ViewModel() {
    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(CurrencyUiState())
    val uiState: kotlinx.coroutines.flow.StateFlow<CurrencyUiState> = _uiState

    companion object {
        val supportedCurrencies = listOf(
            Currency("usd", "USD", "US Dollar", "\uD83C\uDDFA\uD83C\uDDF8", 10.0),
            Currency("eur", "EUR", "Euro", "\uD83C\uDDEA\uD83C\uDDFA", 11.0),
            Currency("gbp", "GBP", "British Pound", "\uD83C\uDDEC\uD83C\uDDE7", 12.5),
            Currency("cad", "CAD", "Canadian Dollar", "\uD83C\uDDE8\uD83C\uDDE6", 7.5),
            Currency("aud", "AUD", "Australian Dollar", "\uD83C\uDDE6\uD83C\uDDFA", 6.5),
            Currency("chf", "CHF", "Swiss Franc", "\uD83C\uDDE8\uD83C\uDDED", 11.5)
        )
    }

    fun setMadAmount(value: String) {
        _uiState.value = _uiState.value.copy(madAmount = value)
    }

    fun setHomeAmount(value: String) {
        _uiState.value = _uiState.value.copy(homeAmount = value)
    }

    fun convertFromMAD() {
        val mad = _uiState.value.madAmount.replace(",", ".").toDoubleOrNull() ?: return
        val home = mad / _uiState.value.exchangeRate
        _uiState.value = _uiState.value.copy(homeAmount = String.format("%.2f", home))
    }

    fun convertFromHome() {
        val home = _uiState.value.homeAmount.replace(",", ".").toDoubleOrNull() ?: return
        val mad = home * _uiState.value.exchangeRate
        _uiState.value = _uiState.value.copy(madAmount = String.format("%.0f", mad))
    }

    fun selectCurrency(currency: Currency) {
        _uiState.value = _uiState.value.copy(
            selectedCurrency = currency,
            exchangeRate = currency.defaultRate
        )
        convertFromMAD()
    }

    fun loadPreferences(context: Context) {
        val prefs = context.getSharedPreferences("currency_prefs", Context.MODE_PRIVATE)
        val currencyCode = prefs.getString("homeCurrency", "USD") ?: "USD"
        val savedRate = prefs.getFloat("exchangeRate", 0f).toDouble()

        val currency = supportedCurrencies.find { it.code == currencyCode } ?: supportedCurrencies.first()
        val rate = if (savedRate > 0) savedRate else currency.defaultRate

        _uiState.value = _uiState.value.copy(
            selectedCurrency = currency,
            exchangeRate = rate
        )
    }

    fun savePreferences(context: Context) {
        val prefs = context.getSharedPreferences("currency_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("homeCurrency", _uiState.value.selectedCurrency.code)
            .putFloat("exchangeRate", _uiState.value.exchangeRate.toFloat())
            .apply()
    }
}

data class CurrencyUiState(
    val madAmount: String = "",
    val homeAmount: String = "",
    val selectedCurrency: Currency = CurrencyConverterViewModel.supportedCurrencies.first(),
    val exchangeRate: Double = 10.0
)

data class Currency(
    val id: String,
    val code: String,
    val name: String,
    val flag: String,
    val defaultRate: Double
)

@Composable
private fun RateInfoCard(
    currency: Currency,
    rate: Double
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            .padding(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Text(
            text = "Current Rate",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "1 ${currency.code} = ${String.format("%.1f", rate)} MAD",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Default rate \u2022 Update in Settings",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CurrencyInputField(
    label: String,
    currencyCode: String,
    flag: String,
    value: String,
    onValueChange: (String) -> Unit,
    isFocused: Boolean,
    onFocusChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(flag, fontSize = 20.sp)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .then(
                    if (isFocused) Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(12.dp)
                    ) else Modifier
                )
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { onFocusChange(it.isFocused) },
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                placeholder = { Text("0", fontSize = 32.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            Text(
                text = currencyCode,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuickAmountsGrid(
    onAmountSelected: (Int) -> Unit
) {
    val quickAmounts = listOf(50, 100, 200, 500, 1000, 2000)

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = "Quick Convert",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.height(100.dp)
        ) {
            items(quickAmounts) { amount ->
                Surface(
                    onClick = { onAmountSelected(amount) },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "$amount MAD",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.sm),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrencySelector(
    selectedCurrency: Currency,
    onClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = "Home Currency",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(selectedCurrency.flag, fontSize = 24.sp)
                    Text(
                        text = selectedCurrency.name,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedCurrency.code,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select currency",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrencyPickerDialog(
    currencies: List<Currency>,
    selectedCurrency: Currency,
    onCurrencySelected: (Currency) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Currency") },
        text = {
            Column {
                currencies.forEach { currency ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCurrencySelected(currency) }
                            .padding(vertical = Spacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(currency.flag, fontSize = 20.sp)
                            Column {
                                Text(
                                    text = currency.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "1 ${currency.code} = ${currency.defaultRate} MAD",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (currency.code == selectedCurrency.code) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
