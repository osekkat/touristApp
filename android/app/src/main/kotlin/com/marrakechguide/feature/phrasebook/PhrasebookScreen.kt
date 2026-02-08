package com.marrakechguide.feature.phrasebook

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marrakechguide.core.model.Phrase
import com.marrakechguide.core.repository.PhraseRepository
import com.marrakechguide.ui.components.ListItemSkeleton
import com.marrakechguide.ui.components.ErrorState
import com.marrakechguide.ui.components.EmptyState
import com.marrakechguide.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phrasebook screen showing Darija phrases organized by category.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhrasebookScreen(
    viewModel: PhrasebookViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedPhrase by remember { mutableStateOf<Phrase?>(null) }
    var showLargeTextMode by remember { mutableStateOf(false) }
    var phraseForLargeText by remember { mutableStateOf<Phrase?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadPhrases()
    }

    // Large text mode full screen
    if (showLargeTextMode && phraseForLargeText != null) {
        LargeTextModeScreen(
            phrase = phraseForLargeText!!,
            onDismiss = {
                showLargeTextMode = false
                phraseForLargeText = null
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Phrasebook") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar
            SearchBar(
                query = searchQuery,
                onQueryChange = {
                    searchQuery = it
                    viewModel.search(it)
                },
                onSearch = { viewModel.search(it) },
                active = false,
                onActiveChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                placeholder = { Text("Search phrases...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            viewModel.search("")
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                }
            ) {}

            // Category filter chips (when not searching)
            if (searchQuery.isEmpty()) {
                PhraseCategoryBar(
                    categories = viewModel.categories,
                    selectedCategory = uiState.selectedCategory,
                    onCategorySelected = { viewModel.selectCategory(it) }
                )
            }

            // Content based on state
            when {
                uiState.isLoading -> {
                    ListItemSkeleton(
                        modifier = Modifier.fillMaxSize(),
                        rows = 8
                    )
                }
                uiState.error != null -> {
                    ErrorState(
                        message = uiState.error ?: "An error occurred",
                        onRetry = { viewModel.loadPhrases() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                uiState.phrases.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Default.Translate,
                        title = "No Phrases Found",
                        message = if (searchQuery.isEmpty()) {
                            "No phrases available in this category."
                        } else {
                            "No phrases matching \"$searchQuery\""
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    PhraseList(
                        phrases = uiState.phrases,
                        onPhraseClick = { phrase ->
                            selectedPhrase = phrase
                        }
                    )
                }
            }
        }
    }

    // Phrase detail bottom sheet
    selectedPhrase?.let { phrase ->
        PhraseDetailSheet(
            phrase = phrase,
            onDismiss = { selectedPhrase = null },
            onShowLargeText = {
                phraseForLargeText = phrase
                selectedPhrase = null
                showLargeTextMode = true
            }
        )
    }
}

/**
 * ViewModel for Phrasebook screen.
 */
@HiltViewModel
class PhrasebookViewModel @Inject constructor(
    private val phraseRepository: PhraseRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(PhrasebookUiState())
    val uiState: StateFlow<PhrasebookUiState> = _uiState.asStateFlow()

    val categories = listOf("All", "Greetings", "Shopping", "Directions", "Food", "Courtesy", "Numbers", "Emergency", "Communication")

    private var allPhrases: List<Phrase> = emptyList()
    private var searchQuery = ""

    fun loadPhrases() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                allPhrases = phraseRepository.getAllPhrasesOnce()
                applyFilters()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load phrases: ${e.message}"
                )
            }
        }
    }

    fun selectCategory(category: String?) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
        applyFilters()
    }

    fun search(query: String) {
        searchQuery = query.trim()
        if (searchQuery.isNotEmpty()) {
            viewModelScope.launch {
                try {
                    val results = phraseRepository.searchPhrases(searchQuery, 30)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        phrases = results
                    )
                } catch (e: Exception) {
                    // Silent fail for search
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        phrases = emptyList()
                    )
                }
            }
        } else {
            applyFilters()
        }
    }

    private fun applyFilters() {
        var filtered = allPhrases

        // Apply category filter
        val category = _uiState.value.selectedCategory
        if (category != null && category != "All") {
            val categoryLower = category.lowercase()
            filtered = filtered.filter { phrase ->
                phrase.category?.lowercase()?.contains(categoryLower) == true
            }
        }

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            phrases = filtered
        )
    }
}

data class PhrasebookUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val phrases: List<Phrase> = emptyList(),
    val selectedCategory: String? = null
)

@Composable
private fun PhraseCategoryBar(
    categories: List<String>,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        categories.forEach { category ->
            val isSelected = selectedCategory == category || (selectedCategory == null && category == "All")
            FilterChip(
                selected = isSelected,
                onClick = { onCategorySelected(if (category == "All") null else category) },
                label = { Text(category) }
            )
        }
    }
}

@Composable
private fun PhraseList(
    phrases: List<Phrase>,
    onPhraseClick: (Phrase) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        items(phrases, key = { it.id }) { phrase ->
            PhraseCard(
                phrase = phrase,
                onClick = { onPhraseClick(phrase) }
            )
        }
    }
}

@Composable
private fun PhraseCard(
    phrase: Phrase,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            // Latin transliteration (primary)
            phrase.latin?.let { latin ->
                Text(
                    text = latin,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Arabic (secondary, RTL)
            phrase.arabic?.let { arabic ->
                Text(
                    text = arabic,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // English meaning
            phrase.english?.let { english ->
                Text(
                    text = english,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Category badge
            phrase.category?.let { category ->
                Text(
                    text = category.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhraseDetailSheet(
    phrase: Phrase,
    onDismiss: () -> Unit,
    onShowLargeText: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            // Latin (large, primary)
            phrase.latin?.let { latin ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        text = "Darija (Latin)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = latin,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Arabic (RTL)
            phrase.arabic?.let { arabic ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Text(
                        text = "Arabic",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = arabic,
                        style = MaterialTheme.typography.headlineLarge,
                        textAlign = TextAlign.End
                    )
                }
            }

            // English
            phrase.english?.let { english ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        text = "English",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = english,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            HorizontalDivider()

            // Actions
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(
                    onClick = onShowLargeText,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PersonSearch, contentDescription = null)
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text("Show to Driver")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    OutlinedButton(
                        onClick = {
                            phrase.latin?.let {
                                clipboardManager.setText(AnnotatedString(it))
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("Latin")
                    }

                    OutlinedButton(
                        onClick = {
                            phrase.arabic?.let {
                                clipboardManager.setText(AnnotatedString(it))
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("Arabic")
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))
        }
    }
}

@Composable
private fun LargeTextModeScreen(
    phrase: Phrase,
    onDismiss: () -> Unit
) {
    val view = LocalView.current

    // Keep screen on
    DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    BackHandler {
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .clickable { onDismiss() }
            .padding(Spacing.lg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Arabic (very large)
            phrase.arabic?.let { arabic ->
                Text(
                    text = arabic,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xl))

            // Latin (below)
            phrase.latin?.let { latin ->
                Text(
                    text = latin,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // English (small)
            phrase.english?.let { english ->
                Text(
                    text = english,
                    fontSize = 24.sp,
                    color = Color.DarkGray,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xl))

            // Close hint
            Text(
                text = "Tap to Close",
                fontSize = 16.sp,
                color = Color.Blue
            )
        }
    }
}
