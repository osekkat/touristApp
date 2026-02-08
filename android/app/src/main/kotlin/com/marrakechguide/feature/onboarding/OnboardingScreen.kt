package com.marrakechguide.feature.onboarding

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marrakechguide.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// MARK: - Data Models

enum class OnboardingStep(val index: Int, val title: String) {
    WELCOME(0, "Welcome"),
    OFFLINE_PROMISE(1, "Works Offline"),
    DOWNLOADS(2, "Get Ready"),
    READINESS_CHECK(3, "Ready Check"),
    DEMO(4, "Quick Demo"),
    PRIVACY(5, "Your Privacy");

    val progress: Float get() = (index + 1).toFloat() / entries.size
}

enum class AppLanguage(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    FRENCH("fr", "Français")
}

enum class HomeCurrency(val code: String, val displayName: String, val symbol: String) {
    USD("USD", "US Dollar", "$"),
    EUR("EUR", "Euro", "€"),
    GBP("GBP", "British Pound", "£"),
    CAD("CAD", "Canadian Dollar", "C$"),
    AUD("AUD", "Australian Dollar", "A$")
}

data class ReadinessItem(
    val title: String,
    val isReady: Boolean = false,
    val count: Int? = null
)

// MARK: - ViewModel

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("onboarding", Context.MODE_PRIVATE)
    private val onboardingCompleteKey = "onboarding_complete"

    private val _currentStep = MutableStateFlow(OnboardingStep.WELCOME)
    val currentStep: StateFlow<OnboardingStep> = _currentStep.asStateFlow()

    private val _selectedLanguage = MutableStateFlow(AppLanguage.ENGLISH)
    val selectedLanguage: StateFlow<AppLanguage> = _selectedLanguage.asStateFlow()

    private val _selectedCurrency = MutableStateFlow(HomeCurrency.USD)
    val selectedCurrency: StateFlow<HomeCurrency> = _selectedCurrency.asStateFlow()

    // Downloads
    private val _isDownloadingBasePack = MutableStateFlow(false)
    val isDownloadingBasePack: StateFlow<Boolean> = _isDownloadingBasePack.asStateFlow()

    private val _basePackProgress = MutableStateFlow(0f)
    val basePackProgress: StateFlow<Float> = _basePackProgress.asStateFlow()

    private val _basePackDownloaded = MutableStateFlow(false)
    val basePackDownloaded: StateFlow<Boolean> = _basePackDownloaded.asStateFlow()

    // Readiness Check
    private val _readinessItems = MutableStateFlow<List<ReadinessItem>>(emptyList())
    val readinessItems: StateFlow<List<ReadinessItem>> = _readinessItems.asStateFlow()

    private val _isCheckingReadiness = MutableStateFlow(false)
    val isCheckingReadiness: StateFlow<Boolean> = _isCheckingReadiness.asStateFlow()

    private val _readinessComplete = MutableStateFlow(false)
    val readinessComplete: StateFlow<Boolean> = _readinessComplete.asStateFlow()

    // Demo
    private val _demoStep = MutableStateFlow(0)
    val demoStep: StateFlow<Int> = _demoStep.asStateFlow()

    // Completion
    private val _isComplete = MutableStateFlow(prefs.getBoolean(onboardingCompleteKey, false))
    val isComplete: StateFlow<Boolean> = _isComplete.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        _selectedLanguage.value = language
    }

    fun setCurrency(currency: HomeCurrency) {
        _selectedCurrency.value = currency
    }

    fun canGoBack(): Boolean = _currentStep.value.index > 0

    fun canGoNext(): Boolean = when (_currentStep.value) {
        OnboardingStep.READINESS_CHECK ->
            _readinessComplete.value || _readinessItems.value.all { it.isReady }
        else -> true
    }

    fun nextButtonTitle(): String = when (_currentStep.value) {
        OnboardingStep.PRIVACY -> "Get Started"
        OnboardingStep.DOWNLOADS -> if (!_basePackDownloaded.value) "Skip for Now" else "Continue"
        else -> "Continue"
    }

    fun goToNextStep() {
        val currentIndex = _currentStep.value.index
        if (currentIndex + 1 < OnboardingStep.entries.size) {
            _currentStep.value = OnboardingStep.entries[currentIndex + 1]

            // Auto-start readiness check
            if (_currentStep.value == OnboardingStep.READINESS_CHECK) {
                performReadinessCheck()
            }
        } else {
            completeOnboarding()
        }
    }

    fun goToPreviousStep() {
        val currentIndex = _currentStep.value.index
        if (currentIndex > 0) {
            _currentStep.value = OnboardingStep.entries[currentIndex - 1]
        }
    }

    fun skipToEnd() {
        completeOnboarding()
    }

    fun downloadBasePack() {
        if (_basePackDownloaded.value) return

        viewModelScope.launch {
            _isDownloadingBasePack.value = true
            _basePackProgress.value = 0f

            for (i in 1..10) {
                delay(200)
                _basePackProgress.value = i / 10f
            }

            _isDownloadingBasePack.value = false
            _basePackDownloaded.value = true
        }
    }

    fun performReadinessCheck() {
        viewModelScope.launch {
            _isCheckingReadiness.value = true
            _readinessComplete.value = false

            _readinessItems.value = listOf(
                ReadinessItem("Places loaded"),
                ReadinessItem("Price cards loaded"),
                ReadinessItem("Phrases loaded"),
                ReadinessItem("Search ready")
            )

            for (i in _readinessItems.value.indices) {
                delay(400)
                _readinessItems.value = _readinessItems.value.mapIndexed { index, item ->
                    if (index == i) {
                        item.copy(
                            isReady = true,
                            count = when (i) {
                                0 -> 47
                                1 -> 23
                                2 -> 85
                                else -> null
                            }
                        )
                    } else item
                }
            }

            _isCheckingReadiness.value = false
            _readinessComplete.value = true
        }
    }

    fun advanceDemo() {
        if (_demoStep.value < 3) {
            _demoStep.value += 1
        }
    }

    private fun completeOnboarding() {
        prefs.edit().putBoolean(onboardingCompleteKey, true).apply()
        _isComplete.value = true
    }

    fun resetOnboarding() {
        prefs.edit().putBoolean(onboardingCompleteKey, false).apply()
        _isComplete.value = false
        _currentStep.value = OnboardingStep.WELCOME
        _basePackDownloaded.value = false
        _basePackProgress.value = 0f
        _readinessItems.value = emptyList()
        _readinessComplete.value = false
        _demoStep.value = 0
    }

    companion object {
        fun shouldShowOnboarding(context: Context): Boolean {
            val prefs = context.getSharedPreferences("onboarding", Context.MODE_PRIVATE)
            return !prefs.getBoolean("onboarding_complete", false)
        }
    }
}

// MARK: - Main Screen

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit
) {
    val currentStep by viewModel.currentStep.collectAsState()
    val isComplete by viewModel.isComplete.collectAsState()

    LaunchedEffect(isComplete) {
        if (isComplete) {
            onComplete()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Progress indicator
        LinearProgressIndicator(
            progress = { currentStep.progress },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        // Step content
        AnimatedContent(
            targetState = currentStep,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            transitionSpec = {
                if (targetState.index > initialState.index) {
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                } else {
                    slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                }
            },
            label = "step_animation"
        ) { step ->
            when (step) {
                OnboardingStep.WELCOME -> WelcomeStep(viewModel)
                OnboardingStep.OFFLINE_PROMISE -> OfflinePromiseStep()
                OnboardingStep.DOWNLOADS -> DownloadsStep(viewModel)
                OnboardingStep.READINESS_CHECK -> ReadinessCheckStep(viewModel)
                OnboardingStep.DEMO -> DemoStep(viewModel)
                OnboardingStep.PRIVACY -> PrivacyStep()
            }
        }

        // Navigation buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (viewModel.canGoBack()) {
                OutlinedButton(onClick = { viewModel.goToPreviousStep() }) {
                    Text("Back")
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (currentStep != OnboardingStep.PRIVACY) {
                    TextButton(onClick = { viewModel.skipToEnd() }) {
                        Text("Skip")
                    }
                }

                Button(
                    onClick = { viewModel.goToNextStep() },
                    enabled = viewModel.canGoNext()
                ) {
                    Text(viewModel.nextButtonTitle())
                }
            }
        }
    }
}

// MARK: - Step 1: Welcome

@Composable
private fun WelcomeStep(viewModel: OnboardingViewModel) {
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val selectedCurrency by viewModel.selectedCurrency.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(Spacing.xl))

        Icon(
            imageVector = Icons.Default.AccountBalance,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(Spacing.lg))

        Text(
            text = "Welcome to Marrakech",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Your offline guide to the Red City",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(Spacing.xl))

        // Settings card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text(
                    text = "Language",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    AppLanguage.entries.forEach { language ->
                        FilterChip(
                            selected = selectedLanguage == language,
                            onClick = { viewModel.setLanguage(language) },
                            label = { Text(language.displayName) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.sm))

                Text(
                    text = "Home Currency",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    HomeCurrency.entries.take(4).forEach { currency ->
                        FilterChip(
                            selected = selectedCurrency == currency,
                            onClick = { viewModel.setCurrency(currency) },
                            label = { Text(currency.code) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Text(
                    text = "Used for price comparisons. Stored locally on your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.weight(1f))
    }
}

// MARK: - Step 2: Offline Promise

@Composable
private fun OfflinePromiseStep() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(Spacing.xl))

        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AirplanemodeActive,
                contentDescription = null,
                modifier = Modifier.size(50.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(Spacing.lg))

        Text(
            text = "Works Without Internet",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Navigate the Medina with confidence, even offline",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(Spacing.xl))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(Spacing.md)) {
                OfflineFeatureRow(
                    icon = Icons.Default.CheckCircle,
                    iconTint = Color(0xFF4CAF50),
                    title = "Works Offline",
                    items = listOf("Places & directions", "Price checks", "Phrasebook", "Tips & culture")
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.md))

                OfflineFeatureRow(
                    icon = Icons.Default.Wifi,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    title = "Needs Internet",
                    items = listOf("Content updates (optional)", "Weather (optional)")
                )
            }
        }

        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun OfflineFeatureRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    items: List<String>
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(28.dp)
        )

        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            items.forEach { item ->
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// MARK: - Step 3: Downloads

@Composable
private fun DownloadsStep(viewModel: OnboardingViewModel) {
    val isDownloading by viewModel.isDownloadingBasePack.collectAsState()
    val progress by viewModel.basePackProgress.collectAsState()
    val isDownloaded by viewModel.basePackDownloaded.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(Spacing.xl))

        Icon(
            imageVector = Icons.Default.CloudDownload,
            contentDescription = null,
            modifier = Modifier.size(60.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(Spacing.lg))

        Text(
            text = "Get Ready for Offline",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Download essential content for your trip",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(Spacing.xl))

        // Base pack card
        DownloadPackCard(
            title = "Base Pack",
            subtitle = "Places, prices, tips, phrases",
            size = "12 MB",
            isIncluded = true,
            isDownloaded = isDownloaded,
            isDownloading = isDownloading,
            progress = progress,
            onDownload = { viewModel.downloadBasePack() }
        )

        Spacer(Modifier.height(Spacing.md))

        // Coming soon pack
        Box {
            DownloadPackCard(
                title = "Medina Map Pack",
                subtitle = "Offline navigation maps",
                size = "45 MB",
                isIncluded = false,
                isDownloaded = false,
                isDownloading = false,
                progress = 0f,
                onDownload = {},
                enabled = false
            )

            Text(
                text = "Coming Soon",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.sm)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        RoundedCornerShape(Spacing.sm)
                    )
                    .padding(horizontal = Spacing.sm, vertical = 2.dp)
            )
        }

        Spacer(Modifier.height(Spacing.md))

        Text(
            text = "You can download more content later from Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun DownloadPackCard(
    title: String,
    subtitle: String,
    size: String,
    isIncluded: Boolean,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    progress: Float,
    onDownload: () -> Unit,
    enabled: Boolean = true
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!enabled) Modifier else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (enabled) 1f else 0.6f)
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (isIncluded) {
                            Text(
                                text = "Included",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier
                                    .background(Color(0xFF4CAF50), RoundedCornerShape(4.dp))
                                    .padding(horizontal = Spacing.sm, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = size,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when {
                isDownloading -> {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                isDownloaded -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Downloaded",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
                isIncluded && enabled -> {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Download Now")
                    }
                }
            }
        }
    }
}

// MARK: - Step 4: Readiness Check

@Composable
private fun ReadinessCheckStep(viewModel: OnboardingViewModel) {
    val readinessItems by viewModel.readinessItems.collectAsState()
    val isChecking by viewModel.isCheckingReadiness.collectAsState()
    val isComplete by viewModel.readinessComplete.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(Spacing.xl))

        Icon(
            imageVector = Icons.Default.VerifiedUser,
            contentDescription = null,
            modifier = Modifier.size(60.dp),
            tint = if (isComplete) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(Spacing.lg))

        Text(
            text = "Ready Check",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = if (isComplete) "Everything looks good!" else "Verifying offline readiness...",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(Spacing.xl))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                if (isChecking && readinessItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.lg),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                readinessItems.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (item.isReady) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }

                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }

                        item.count?.let { count ->
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (isComplete) {
            Spacer(Modifier.height(Spacing.lg))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AirplanemodeActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Try enabling Airplane Mode to test offline features!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))
    }
}

// MARK: - Step 5: Demo

@Composable
private fun DemoStep(viewModel: OnboardingViewModel) {
    val demoStep by viewModel.demoStep.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(Spacing.lg))

        Text(
            text = "Quick Demo",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "See how to check if a price is fair",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(Spacing.xl))

        // Demo scenario
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.padding(Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Icon(
                    imageVector = Icons.Default.LocalTaxi,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )

                Column {
                    Text(
                        text = "Taxi from Airport",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Driver quotes: 200 MAD",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.md))

        // Demo results
        if (demoStep >= 1) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Fair Price Range",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "120-180 MAD",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (demoStep >= 2) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFF9800),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "200 MAD is above fair price",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFFF9800)
                            )
                        }
                    }

                    if (demoStep >= 3) {
                        Text(
                            text = "Counter: \"150 dirhams?\" and walk away if needed.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    RoundedCornerShape(Spacing.sm)
                                )
                                .padding(Spacing.sm)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.md))

        if (demoStep < 3) {
            val buttonText = listOf("Show Price Check", "Show Verdict", "Show Counter Tip")[demoStep]
            OutlinedButton(onClick = { viewModel.advanceDemo() }) {
                Text(buttonText)
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50)
                )
                Text(
                    text = "That's how it works!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.weight(1f))
    }
}

// MARK: - Step 6: Privacy

@Composable
private fun PrivacyStep() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(Spacing.xl))

        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = null,
            modifier = Modifier.size(60.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(Spacing.lg))

        Text(
            text = "Your Privacy Matters",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "We built this app with privacy first",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(Spacing.xl))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                PrivacyPointRow(
                    icon = Icons.Default.PersonOff,
                    title = "No Accounts",
                    description = "No sign-up, no login, no tracking"
                )

                HorizontalDivider()

                PrivacyPointRow(
                    icon = Icons.Default.SpeakerNotesOff,
                    title = "No Ads",
                    description = "No advertisements, ever"
                )

                HorizontalDivider()

                PrivacyPointRow(
                    icon = Icons.Default.LocationOn,
                    title = "Location Stays on Device",
                    description = "Used only when you ask, never sent anywhere"
                )

                HorizontalDivider()

                PrivacyPointRow(
                    icon = Icons.Default.PhoneAndroid,
                    title = "Data Stays Local",
                    description = "All your favorites and settings stored on your phone"
                )
            }
        }

        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun PrivacyPointRow(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )

        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
