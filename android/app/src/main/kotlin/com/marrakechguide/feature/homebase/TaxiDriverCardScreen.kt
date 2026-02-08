package com.marrakechguide.feature.homebase

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.marrakechguide.core.repository.HomeBase
import com.marrakechguide.ui.theme.MarrakechGuideTheme
import com.marrakechguide.ui.theme.MarrakechOrange

/**
 * Full-screen card optimized for showing to taxi drivers.
 *
 * Features:
 * - Large Arabic text (RTL)
 * - Latin name below
 * - Address if available
 * - "Take me here" phrase in Darija
 * - High contrast, keeps screen awake
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaxiDriverCardScreen(
    homeBase: HomeBase,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Keep screen awake
    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "Done",
                            color = MarrakechOrange
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { shareCard(context, homeBase) }) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share",
                            tint = MarrakechOrange
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.weight(1f))

                // Arabic name (large, RTL)
                arabicTransliteration(homeBase.name)?.let { arabicName ->
                    Text(
                        text = arabicName,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Latin name
                Text(
                    text = homeBase.name,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
                    textAlign = TextAlign.Center
                )

                // Address if available
                homeBase.address?.let { address ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = address,
                        fontSize = 24.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Divider(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .padding(vertical = 20.dp)
                )

                // Darija phrase
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Arabic
                    Text(
                        text = "من فضلك، ديني لهنا",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Medium,
                        color = MarrakechOrange,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Latin transliteration
                    Text(
                        text = "Mn fadlik, dini l'hna",
                        fontSize = 20.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // English
                    Text(
                        text = "Please take me here",
                        fontSize = 18.sp,
                        color = Color.DarkGray,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Tip
                Text(
                    text = "Show this to the taxi driver",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 40.dp)
                )
            }
        }
    }
}

/**
 * Attempt to provide Arabic transliteration of the name.
 * In a real app, this would come from the database.
 */
private fun arabicTransliteration(name: String): String? {
    val lowercaseName = name.lowercase()

    return when {
        lowercaseName.contains("riad") -> {
            val parts = name.replace(Regex("(?i)riad\\s*"), "")
            "رياض $parts"
        }
        lowercaseName.contains("hotel") -> {
            val parts = name.replace(Regex("(?i)hotel\\s*"), "")
            "فندق $parts"
        }
        lowercaseName.contains("dar") -> {
            val parts = name.replace(Regex("(?i)dar\\s*"), "")
            "دار $parts"
        }
        else -> null
    }
}

private fun shareCard(context: Context, homeBase: HomeBase) {
    var shareText = homeBase.name

    homeBase.address?.let { address ->
        shareText += "\n$address"
    }

    shareText += "\n\nمن فضلك، ديني لهنا"
    shareText += "\n(Please take me here)"

    // Add coordinates for maps
    val mapsUrl = "https://maps.google.com/?q=${homeBase.lat},${homeBase.lng}"
    shareText += "\n\n$mapsUrl"

    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, shareText)
        type = "text/plain"
    }

    val shareIntent = Intent.createChooser(sendIntent, "Share location")
    context.startActivity(shareIntent)
}

// MARK: - Previews

@Preview(showBackground = true)
@Composable
private fun TaxiDriverCardPreview() {
    MarrakechGuideTheme {
        TaxiDriverCardScreen(
            homeBase = HomeBase(
                name = "Riad Dar Maya",
                lat = 31.6295,
                lng = -7.9912,
                address = "12 Derb Sidi Bouloukate, Medina"
            ),
            onDismiss = {}
        )
    }
}

@Preview(showBackground = true, name = "Hotel")
@Composable
private fun TaxiDriverCardHotelPreview() {
    MarrakechGuideTheme {
        TaxiDriverCardScreen(
            homeBase = HomeBase(
                name = "Hotel La Mamounia",
                lat = 31.6234,
                lng = -7.9956,
                address = "Avenue Bab Jdid"
            ),
            onDismiss = {}
        )
    }
}

@Preview(showBackground = true, name = "No Address")
@Composable
private fun TaxiDriverCardNoAddressPreview() {
    MarrakechGuideTheme {
        TaxiDriverCardScreen(
            homeBase = HomeBase(
                name = "Dar Anika",
                lat = 31.6300,
                lng = -7.9900,
                address = null
            ),
            onDismiss = {}
        )
    }
}
