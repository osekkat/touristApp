package com.marrakechguide.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marrakechguide.core.model.MapCamera
import com.marrakechguide.core.model.MapCoordinate
import com.marrakechguide.core.model.MapMarker
import com.marrakechguide.core.model.MapRoute
import com.marrakechguide.core.model.MapViewState
import com.marrakechguide.core.model.MarkerCategory
import com.marrakechguide.core.model.TileSource
import com.marrakechguide.ui.theme.Spacing
import kotlin.math.cos
import kotlin.math.sin

/**
 * Offline-capable map view component.
 *
 * This is a placeholder implementation that renders:
 * - A grid background representing the map area
 * - Markers for places
 * - Routes as polylines
 * - User location dot
 *
 * In production, this would be replaced with MapLibre or similar.
 */
@Composable
fun MapView(
    state: MapViewState,
    modifier: Modifier = Modifier,
    tileSource: TileSource = TileSource.PLACEHOLDER,
    onCameraChange: (MapCamera) -> Unit = {},
    onMarkerClick: (MapMarker) -> Unit = {},
    onMyLocationClick: () -> Unit = {},
    onDownloadMapClick: () -> Unit = {}
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .background(Color(0xFFF5F5DC)) // Beige map background
    ) {
        // Map canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                        // Update camera based on pan
                        val latDelta = -dragAmount.y / 10000.0
                        val lngDelta = dragAmount.x / 10000.0
                        onCameraChange(
                            state.camera.copy(
                                center = MapCoordinate(
                                    latitude = state.camera.center.latitude + latDelta,
                                    longitude = state.camera.center.longitude + lngDelta
                                )
                            )
                        )
                    }
                }
        ) {
            val centerX = size.width / 2 + offsetX
            val centerY = size.height / 2 + offsetY

            // Draw grid lines (simulating streets)
            val gridColor = Color(0xFFE0DDD0)
            val gridSpacing = 50f
            for (x in 0..size.width.toInt() step gridSpacing.toInt()) {
                drawLine(
                    color = gridColor,
                    start = Offset(x.toFloat(), 0f),
                    end = Offset(x.toFloat(), size.height),
                    strokeWidth = 1f
                )
            }
            for (y in 0..size.height.toInt() step gridSpacing.toInt()) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y.toFloat()),
                    end = Offset(size.width, y.toFloat()),
                    strokeWidth = 1f
                )
            }

            // Draw routes
            state.routes.forEach { route ->
                if (route.points.size >= 2) {
                    val path = Path()
                    val firstPoint = coordinateToScreen(
                        route.points[0],
                        state.camera.center,
                        centerX,
                        centerY,
                        state.camera.zoom
                    )
                    path.moveTo(firstPoint.x, firstPoint.y)

                    route.points.drop(1).forEach { point ->
                        val screenPoint = coordinateToScreen(
                            point,
                            state.camera.center,
                            centerX,
                            centerY,
                            state.camera.zoom
                        )
                        path.lineTo(screenPoint.x, screenPoint.y)
                    }

                    drawPath(
                        path = path,
                        color = Color(0xFF4A90D9),
                        style = Stroke(width = route.width * 2)
                    )
                }
            }

            // Draw markers
            state.markers.forEach { marker ->
                val screenPos = coordinateToScreen(
                    marker.coordinate,
                    state.camera.center,
                    centerX,
                    centerY,
                    state.camera.zoom
                )

                val markerColor = when (marker.category) {
                    MarkerCategory.HOME_BASE -> Color(0xFF4CAF50) // Green
                    MarkerCategory.ROUTE_STOP -> Color(0xFF2196F3) // Blue
                    MarkerCategory.RESTAURANT, MarkerCategory.CAFE -> Color(0xFFFF9800) // Orange
                    MarkerCategory.LANDMARK, MarkerCategory.MUSEUM -> Color(0xFF9C27B0) // Purple
                    else -> Color(0xFFE53935) // Red
                }

                // Draw marker pin
                drawCircle(
                    color = markerColor,
                    radius = if (marker.isSelected) 16f else 12f,
                    center = screenPos
                )
                drawCircle(
                    color = Color.White,
                    radius = if (marker.isSelected) 8f else 6f,
                    center = screenPos
                )
            }

            // Draw user location
            state.userLocation?.let { location ->
                val userPos = coordinateToScreen(
                    location,
                    state.camera.center,
                    centerX,
                    centerY,
                    state.camera.zoom
                )

                // Accuracy circle
                drawCircle(
                    color = Color(0x334A90D9),
                    radius = 30f,
                    center = userPos
                )

                // User dot
                drawCircle(
                    color = Color(0xFF4A90D9),
                    radius = 10f,
                    center = userPos
                )
                drawCircle(
                    color = Color.White,
                    radius = 6f,
                    center = userPos
                )

                // Heading indicator
                state.userHeading?.let { heading ->
                    rotate(heading.toFloat(), userPos) {
                        val trianglePath = Path().apply {
                            moveTo(userPos.x, userPos.y - 20f)
                            lineTo(userPos.x - 6f, userPos.y - 8f)
                            lineTo(userPos.x + 6f, userPos.y - 8f)
                            close()
                        }
                        drawPath(
                            path = trianglePath,
                            color = Color(0xFF4A90D9)
                        )
                    }
                }
            }
        }

        // Loading indicator
        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
            )
        }

        // Offline map prompt
        if (tileSource == TileSource.PLACEHOLDER && !state.isLoading) {
            OfflineMapPrompt(
                onDownloadClick = onDownloadMapClick,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = Spacing.lg)
            )
        }

        // Map controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Spacing.md)
        ) {
            // My location button
            FilledIconButton(
                onClick = onMyLocationClick,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Icon(
                    Icons.Default.MyLocation,
                    contentDescription = "My location",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Error message
        state.error?.let { error ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(Spacing.md),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(Spacing.sm),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun OfflineMapPrompt(
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        onClick = onDownloadClick
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CloudDownload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(Spacing.sm))
            Column {
                Text(
                    text = "Offline Maps Available",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Download for navigation without internet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Convert a geographic coordinate to screen position.
 */
private fun coordinateToScreen(
    coordinate: MapCoordinate,
    center: MapCoordinate,
    centerX: Float,
    centerY: Float,
    zoom: Double
): Offset {
    val scale = zoom * 10000
    val x = centerX + ((coordinate.longitude - center.longitude) * scale).toFloat()
    val y = centerY - ((coordinate.latitude - center.latitude) * scale).toFloat()
    return Offset(x, y)
}

/**
 * Simple compass indicator component.
 */
@Composable
fun CompassIndicator(
    bearing: Double,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(40.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(32.dp)) {
                rotate(-bearing.toFloat()) {
                    // North indicator
                    drawLine(
                        color = Color.Red,
                        start = Offset(center.x, center.y - 12f),
                        end = Offset(center.x, center.y + 4f),
                        strokeWidth = 3f
                    )
                    // South indicator
                    drawLine(
                        color = Color.Gray,
                        start = Offset(center.x, center.y + 4f),
                        end = Offset(center.x, center.y + 12f),
                        strokeWidth = 3f
                    )
                }
            }
        }
    }
}
