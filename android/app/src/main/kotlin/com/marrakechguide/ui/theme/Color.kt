package com.marrakechguide.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Design tokens for the Marrakech Guide app.
 *
 * The visual identity is rooted in Marrakech's warm terracotta palette,
 * evoking the clay walls and earthy tones of the ancient medina.
 */

// MARK: - Primary Colors (Terracotta)

val Terracotta50 = Color(0xFFFDF5F2)
val Terracotta100 = Color(0xFFFAE6DE)
val Terracotta200 = Color(0xFFF5C9B8)
val Terracotta300 = Color(0xFFED9F7F)
val Terracotta400 = Color(0xFFE57A4F)
val Terracotta500 = Color(0xFFD4572B)  // Primary
val Terracotta600 = Color(0xFFB84421)
val Terracotta700 = Color(0xFF8F341A)
val Terracotta800 = Color(0xFF6A2714)
val Terracotta900 = Color(0xFF4A1C0F)

// MARK: - Neutral Colors

val Neutral50 = Color(0xFFFAFAFA)
val Neutral100 = Color(0xFFF5F5F5)
val Neutral200 = Color(0xFFE5E5E5)
val Neutral300 = Color(0xFFD4D4D4)
val Neutral400 = Color(0xFFA3A3A3)
val Neutral500 = Color(0xFF737373)
val Neutral600 = Color(0xFF525252)
val Neutral700 = Color(0xFF404040)
val Neutral800 = Color(0xFF262626)
val Neutral900 = Color(0xFF171717)

// MARK: - Semantic Colors

val SemanticSuccess = Color(0xFF22C55E)
val SemanticWarning = Color(0xFFEAB308)
val SemanticError = Color(0xFFEF4444)
val SemanticInfo = Color(0xFF3B82F6)

// MARK: - Fairness Meter Colors

val FairnessLow = SemanticWarning      // Suspiciously cheap
val FairnessFair = SemanticSuccess     // Within expected range
val FairnessHigh = Terracotta400       // Slightly high but negotiable
val FairnessVeryHigh = SemanticError   // Tourist trap pricing

// MARK: - Legacy Aliases (for backwards compatibility during migration)

val MarrakechTerracotta = Terracotta500
val MarrakechSand = Terracotta50
val MarrakechInk = Neutral900
val MarrakechOlive = Color(0xFF7A8B4A)
val MarrakechSky = Color(0xFF7BB6C9)
