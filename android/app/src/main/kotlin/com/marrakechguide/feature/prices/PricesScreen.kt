package com.marrakechguide.feature.prices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.marrakechguide.ui.components.ContentCard
import com.marrakechguide.ui.components.PriceTag
import com.marrakechguide.ui.theme.Spacing
import java.util.Locale

@Composable
fun PricesScreen(
    onCheckQuote: () -> Unit = {},
    onOpenCardDetail: (String) -> Unit = {},
) {
    val cards = PriceCardPreviewCatalog.cards

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onCheckQuote) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Check a quote",
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            cards.forEach { card ->
                ContentCard(
                    title = card.title,
                    onClick = { onOpenCardDetail(card.id) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = card.category.replaceFirstChar { first ->
                                first.titlecase(Locale.US)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        PriceTag(
                            minMad = card.expectedCostMinMad,
                            maxMad = card.expectedCostMaxMad,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PriceCardDetailScreen(
    cardId: String?,
    onCheckQuote: (String?) -> Unit = {},
) {
    val card = PriceCardPreviewCatalog.cards.firstOrNull { it.id == cardId }

    if (card == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.md),
        ) {
            Text(
                text = "Price card not found.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        ContentCard(title = card.title) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = card.category.replaceFirstChar { first ->
                            first.titlecase(Locale.US)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    PriceTag(
                        minMad = card.expectedCostMinMad,
                        maxMad = card.expectedCostMaxMad,
                    )
                }

                if (card.expectedCostNotes.isNotBlank()) {
                    Text(
                        text = card.expectedCostNotes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = "Last reviewed: ${card.expectedCostUpdatedAt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Button(
            onClick = { onCheckQuote(card.id) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Check this Quote")
        }
    }
}

data class PriceCardPreview(
    val id: String,
    val title: String,
    val category: String,
    val expectedCostMinMad: Int,
    val expectedCostMaxMad: Int,
    val expectedCostNotes: String,
    val expectedCostUpdatedAt: String,
)

private object PriceCardPreviewCatalog {
    val cards = listOf(
        PriceCardPreview(
            id = "price-taxi-airport-marrakech-center",
            title = "Taxi: airport to Marrakech center",
            category = "taxi",
            expectedCostMinMad = 120,
            expectedCostMaxMad = 220,
            expectedCostNotes = "Night-time or heavy luggage may move to top of range.",
            expectedCostUpdatedAt = "2026-02-07",
        ),
        PriceCardPreview(
            id = "price-taxi-medina-short-ride",
            title = "Taxi: short intra-city ride",
            category = "taxi",
            expectedCostMinMad = 20,
            expectedCostMaxMad = 50,
            expectedCostNotes = "Dense medina edges may increase travel time.",
            expectedCostUpdatedAt = "2026-02-07",
        ),
        PriceCardPreview(
            id = "price-hammam-local-basic",
            title = "Hammam: local basic",
            category = "hammam",
            expectedCostMinMad = 120,
            expectedCostMaxMad = 300,
            expectedCostNotes = "Range for basic local-style experiences.",
            expectedCostUpdatedAt = "2026-02-07",
        ),
        PriceCardPreview(
            id = "price-hammam-tourist-spa",
            title = "Hammam: tourist spa package",
            category = "hammam",
            expectedCostMinMad = 350,
            expectedCostMaxMad = 900,
            expectedCostNotes = "Premium spas and bundles can exceed this range.",
            expectedCostUpdatedAt = "2026-02-07",
        ),
        PriceCardPreview(
            id = "price-activity-city-half-day",
            title = "Private half-day city tour",
            category = "guides",
            expectedCostMinMad = 350,
            expectedCostMaxMad = 900,
            expectedCostNotes = "Specialist routes and language options raise the price.",
            expectedCostUpdatedAt = "2026-02-07",
        ),
    )
}
