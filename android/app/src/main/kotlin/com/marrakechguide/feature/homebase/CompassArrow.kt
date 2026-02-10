package com.marrakechguide.feature.homebase

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marrakechguide.core.service.HeadingConfidence
import com.marrakechguide.ui.theme.MarrakechGuideTheme

/**
 * Animated compass arrow that points toward a destination.
 *
 * Features:
 * - Smooth rotation animation
 * - Confidence indicator ring
 * - Cardinal direction markers
 * - Unavailable state overlay
 */
@Composable
fun CompassArrow(
    rotationDegrees: Double,
    confidence: HeadingConfidence,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    showConfidenceRing: Boolean = true
) {
    // Check system Reduce Motion / animation scale settings
    val context = LocalContext.current
    val reduceMotion = remember {
        val animatorDurationScale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        animatorDurationScale == 0f
    }

    val animatedRotation by animateFloatAsState(
        targetValue = rotationDegrees.toFloat(),
        animationSpec = if (reduceMotion) snap() else tween(durationMillis = 150),
        label = "arrow_rotation"
    )

    val confidenceColor = when (confidence) {
        HeadingConfidence.GOOD -> Color(0xFF4CAF50) // Green
        HeadingConfidence.WEAK -> Color(0xFFFF9800) // Orange
        HeadingConfidence.UNAVAILABLE -> Color(0xFF9E9E9E) // Gray
    }

    // Generate accessibility description
    val accessibilityDescription = remember(confidence, rotationDegrees) {
        when (confidence) {
            HeadingConfidence.UNAVAILABLE -> "Compass unavailable"
            HeadingConfidence.WEAK -> "Compass pointing ${directionFromRotation(rotationDegrees)}, heading may be inaccurate"
            HeadingConfidence.GOOD -> "Compass pointing ${directionFromRotation(rotationDegrees)}"
        }
    }

    Box(
        modifier = modifier
            .size(size + 20.dp)
            .clearAndSetSemantics {
                contentDescription = accessibilityDescription
            },
        contentAlignment = Alignment.Center
    ) {
        // Confidence ring
        if (showConfidenceRing) {
            Canvas(modifier = Modifier.size(size + 20.dp)) {
                val ringWidth = 8.dp.toPx()
                val radius = (size.toPx() + 20.dp.toPx()) / 2 - ringWidth / 2

                // Outer ring (faded)
                drawCircle(
                    color = confidenceColor.copy(alpha = 0.3f),
                    radius = radius,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = ringWidth)
                )

                // Inner ring
                drawCircle(
                    color = confidenceColor,
                    radius = radius,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                )
            }
        }

        // Main compass circle
        Box(
            modifier = Modifier
                .size(size)
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            // Cardinal markers
            CardinalMarkers(size = size)

            // Arrow (width: 0.35, height: 0.6 to match iOS)
            Canvas(
                modifier = Modifier
                    .size(width = size * 0.35f, height = size * 0.6f)
                    .rotate(animatedRotation)
            ) {
                drawArrow(this.size.width, this.size.height)
            }

            // Center dot
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            )

            // Unavailable overlay
            if (confidence == HeadingConfidence.UNAVAILABLE) {
                UnavailableOverlay(size = size)
            }
        }
    }
}

@Composable
private fun CardinalMarkers(size: Dp) {
    Box(modifier = Modifier.size(size)) {
        // North
        Text(
            text = "N",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // East
        Text(
            text = "E",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.CenterEnd)
        )

        // South
        Text(
            text = "S",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // West
        Text(
            text = "W",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.CenterStart)
        )
    }
}

@Composable
private fun UnavailableOverlay(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.LocationOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "Heading unavailable",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun DrawScope.drawArrow(width: Float, height: Float) {
    val arrowPath = Path().apply {
        // Arrow pointing up
        moveTo(width / 2, 0f)                    // Top point
        lineTo(width, height * 0.4f)             // Right wing
        lineTo(width * 0.6f, height * 0.4f)      // Right inner
        lineTo(width * 0.6f, height)             // Right tail
        lineTo(width * 0.4f, height)             // Left tail
        lineTo(width * 0.4f, height * 0.4f)      // Left inner
        lineTo(0f, height * 0.4f)                // Left wing
        close()
    }

    val gradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFFF9800), // Orange
            Color(0xFFF44336)  // Red
        )
    )

    drawPath(
        path = arrowPath,
        brush = gradient,
        style = Fill
    )
}

/**
 * Convert rotation degrees to cardinal direction text for accessibility.
 * Note: Kotlin's mod() always returns a non-negative value for positive divisor.
 *
 * @VisibleForTesting - exposed as internal for unit testing
 */
internal fun directionFromRotation(rotationDegrees: Double): String {
    val degrees = rotationDegrees.mod(360.0) // Always in [0, 360)

    return when {
        degrees >= 337.5 || degrees < 22.5 -> "north"
        degrees >= 22.5 && degrees < 67.5 -> "northeast"
        degrees >= 67.5 && degrees < 112.5 -> "east"
        degrees >= 112.5 && degrees < 157.5 -> "southeast"
        degrees >= 157.5 && degrees < 202.5 -> "south"
        degrees >= 202.5 && degrees < 247.5 -> "southwest"
        degrees >= 247.5 && degrees < 292.5 -> "west"
        degrees >= 292.5 && degrees < 337.5 -> "northwest"
        else -> "unknown direction"
    }
}

// MARK: - Previews

@Preview(showBackground = true)
@Composable
private fun CompassArrowGoodPreview() {
    MarrakechGuideTheme {
        CompassArrow(
            rotationDegrees = 45.0,
            confidence = HeadingConfidence.GOOD
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CompassArrowWeakPreview() {
    MarrakechGuideTheme {
        CompassArrow(
            rotationDegrees = 120.0,
            confidence = HeadingConfidence.WEAK
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CompassArrowUnavailablePreview() {
    MarrakechGuideTheme {
        CompassArrow(
            rotationDegrees = 0.0,
            confidence = HeadingConfidence.UNAVAILABLE
        )
    }
}
