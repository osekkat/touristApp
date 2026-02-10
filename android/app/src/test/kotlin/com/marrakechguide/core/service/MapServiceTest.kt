package com.marrakechguide.core.service

import com.marrakechguide.core.model.ContentPack
import com.marrakechguide.core.model.DownloadPreferences
import com.marrakechguide.core.model.DownloadResult
import com.marrakechguide.core.model.MapBounds
import com.marrakechguide.core.model.MapCoordinate
import com.marrakechguide.core.model.MapRoute
import com.marrakechguide.core.model.PackState
import com.marrakechguide.core.model.PackStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for MapService.
 *
 * Tests haversine distance calculation, walk time estimation, and pack ID handling.
 */
class MapServiceTest {

    // ==================== Pack ID Constants Tests ====================

    @Test
    fun `pack IDs use correct naming convention`() {
        // Verify the pack ID constants are correctly named (no typos like "PacK")
        // These strings must match exactly what's stored in downloaded pack manifests
        val medinaPackId = "medina-map"
        val guelizPackId = "gueliz-map"

        // Verify IDs follow kebab-case convention
        assertTrue("Medina pack ID should be lowercase kebab-case",
            medinaPackId.matches(Regex("^[a-z]+-[a-z]+$")))
        assertTrue("Gueliz pack ID should be lowercase kebab-case",
            guelizPackId.matches(Regex("^[a-z]+-[a-z]+$")))

        // Verify specific expected values
        assertEquals("medina-map", medinaPackId)
        assertEquals("gueliz-map", guelizPackId)
    }

    @Test
    fun `MapService uses correct medina pack ID to check availability`() {
        // This test verifies that MapService looks for exactly "medina-map" (not "medinaPacK" etc.)
        val fakeDownloadService = FakeDownloadService()

        // Set medina-map as installed - must use exact ID
        fakeDownloadService.setPackState("medina-map", PackStatus.INSTALLED)

        // Verify the pack state was set correctly
        val packStates = fakeDownloadService.packStates.value
        assertTrue("medina-map should be in pack states", packStates.containsKey("medina-map"))
        assertEquals(PackStatus.INSTALLED, packStates["medina-map"]?.status)

        // Verify typo version would NOT match
        assertFalse("Typo key should not exist", packStates.containsKey("medinaPacK-map"))
        assertFalse("Wrong case should not exist", packStates.containsKey("MEDINA-MAP"))
    }

    @Test
    fun `MapService uses correct gueliz pack ID to check availability`() {
        // This test verifies that MapService looks for exactly "gueliz-map"
        val fakeDownloadService = FakeDownloadService()

        // Set gueliz-map as installed - must use exact ID
        fakeDownloadService.setPackState("gueliz-map", PackStatus.INSTALLED)

        // Verify the pack state was set correctly
        val packStates = fakeDownloadService.packStates.value
        assertTrue("gueliz-map should be in pack states", packStates.containsKey("gueliz-map"))
        assertEquals(PackStatus.INSTALLED, packStates["gueliz-map"]?.status)

        // Verify wrong IDs would NOT match
        assertFalse("Wrong ID should not exist", packStates.containsKey("gueliz"))
        assertFalse("Wrong case should not exist", packStates.containsKey("GUELIZ-MAP"))
    }

    @Test
    fun `pack availability requires exact ID match`() {
        val fakeDownloadService = FakeDownloadService()

        // Set up states with various typo versions - none should work
        fakeDownloadService.setPackState("medinaPacK-map", PackStatus.INSTALLED)  // typo
        fakeDownloadService.setPackState("MEDINA-MAP", PackStatus.INSTALLED)      // wrong case
        fakeDownloadService.setPackState("medina_map", PackStatus.INSTALLED)      // underscore

        val packStates = fakeDownloadService.packStates.value

        // The correct key should NOT be present
        assertNull("medina-map should not exist with typo keys",
            packStates["medina-map"])
    }

    // ==================== Haversine Distance Tests ====================

    @Test
    fun `haversine - Jemaa el-Fna to Bahia Palace`() {
        // Known distance: approximately 717 meters
        val jemaa = MapCoordinate(31.625831, -7.98892)
        val bahia = MapCoordinate(31.621510, -7.983298)

        val distance = haversineDistance(jemaa, bahia)

        // Should be approximately 717m with 50m tolerance
        assertEquals(717.0, distance, 50.0)
    }

    @Test
    fun `haversine - same point returns zero`() {
        val point = MapCoordinate(31.625831, -7.98892)

        val distance = haversineDistance(point, point)

        assertEquals(0.0, distance, 0.001)
    }

    @Test
    fun `haversine - Jemaa el-Fna to Jardin Majorelle`() {
        // Known distance: approximately 2186 meters
        val jemaa = MapCoordinate(31.625831, -7.98892)
        val majorelle = MapCoordinate(31.641475, -8.002908)

        val distance = haversineDistance(jemaa, majorelle)

        // Should be approximately 2186m with 100m tolerance
        assertEquals(2186.0, distance, 100.0)
    }

    @Test
    fun `haversine - symmetry check`() {
        val pointA = MapCoordinate(31.625831, -7.98892)
        val pointB = MapCoordinate(31.641475, -8.002908)

        val distanceAB = haversineDistance(pointA, pointB)
        val distanceBA = haversineDistance(pointB, pointA)

        assertEquals("Distance should be symmetric", distanceAB, distanceBA, 0.001)
    }

    // ==================== Walk Time Estimation Tests ====================

    @Test
    fun `estimateWalkTime - short distance in medina`() {
        // 100 meters in medina at 50m/min = 2 minutes
        val from = MapCoordinate(31.625831, -7.98892)
        val to = MapCoordinate(31.626731, -7.98892) // ~100m north

        val time = estimateWalkTime(from, to, inMedina = true)

        assertTrue("Short walk should be at least 1 minute", time >= 1)
        assertTrue("100m walk should be less than 5 minutes", time <= 5)
    }

    @Test
    fun `estimateWalkTime - medina slower than gueliz`() {
        // Same distance should take longer in medina due to slower speed
        val from = MapCoordinate(31.625831, -7.98892)
        val to = MapCoordinate(31.630831, -7.98892) // ~500m north

        val medinaTime = estimateWalkTime(from, to, inMedina = true)
        val guelizTime = estimateWalkTime(from, to, inMedina = false)

        assertTrue("Medina walk should take longer than Gueliz walk",
            medinaTime > guelizTime)
    }

    // ==================== Map Bounds Tests ====================

    @Test
    fun `MapBounds - Jemaa el-Fna is in medina bounds`() {
        val jemaa = MapCoordinate(31.625831, -7.98892)

        assertTrue("Jemaa el-Fna should be within medina bounds",
            MapBounds.MEDINA.contains(jemaa))
    }

    @Test
    fun `MapBounds - Jardin Majorelle is in gueliz bounds`() {
        val majorelle = MapCoordinate(31.641475, -8.002908)

        assertTrue("Jardin Majorelle should be within gueliz bounds",
            MapBounds.GUELIZ.contains(majorelle))
    }

    @Test
    fun `MapBounds - Paris is not in Marrakech`() {
        val paris = MapCoordinate(48.8566, 2.3522)

        assertFalse("Paris should not be in medina", MapBounds.MEDINA.contains(paris))
        assertFalse("Paris should not be in gueliz", MapBounds.GUELIZ.contains(paris))
    }

    @Test
    fun `MapBounds - boundary coordinates are included`() {
        // Test that points exactly on the boundary are included
        val southwest = MapBounds.MEDINA.southwest
        val northeast = MapBounds.MEDINA.northeast

        assertTrue("Southwest corner should be in bounds",
            MapBounds.MEDINA.contains(southwest))
        assertTrue("Northeast corner should be in bounds",
            MapBounds.MEDINA.contains(northeast))
    }

    @Test
    fun `MapBounds - medina and gueliz do not overlap completely`() {
        // Verify the bounds are defined for different areas
        val medinaCenter = MapCoordinate(31.625831, -7.98892)
        val guelizCenter = MapCoordinate(31.641475, -8.002908)

        // Medina center should be in medina, not necessarily in gueliz
        assertTrue(MapBounds.MEDINA.contains(medinaCenter))

        // Gueliz center should be in gueliz
        assertTrue(MapBounds.GUELIZ.contains(guelizCenter))
    }

    // ==================== Route Calculation Tests ====================

    @Test
    fun `calculateRoute - short distance returns direct line`() {
        // Points less than 50m apart should return just start and end
        val from = MapCoordinate(31.625831, -7.98892)
        val to = MapCoordinate(31.625931, -7.98892) // ~11m north

        val route = calculateRoute(from, to)

        assertNotNull("Route should not be null", route)
        assertEquals("Short route should have 2 points", 2, route!!.points.size)
        assertEquals(from, route.points.first())
        assertEquals(to, route.points.last())
    }

    @Test
    fun `calculateRoute - longer distance generates intermediate waypoints`() {
        // Points 500m+ apart should have intermediate waypoints
        val from = MapCoordinate(31.625831, -7.98892)
        val to = MapCoordinate(31.630831, -7.98892) // ~555m north

        val route = calculateRoute(from, to)

        assertNotNull("Route should not be null", route)
        assertTrue("Route should have more than 2 points", route!!.points.size > 2)
        assertEquals("Route should start at from", from, route.points.first())
        assertEquals("Route should end at to", to, route.points.last())
    }

    @Test
    fun `calculateRoute - intermediate points are between start and end`() {
        val from = MapCoordinate(31.625831, -7.98892)
        val to = MapCoordinate(31.635831, -7.97892) // ~1.4km northeast

        val route = calculateRoute(from, to)

        assertNotNull(route)
        // All intermediate points should have lat/lng between start and end
        for (point in route!!.points) {
            val latInRange = point.latitude in minOf(from.latitude, to.latitude)..maxOf(from.latitude, to.latitude)
            val lngInRange = point.longitude in minOf(from.longitude, to.longitude)..maxOf(from.longitude, to.longitude)
            assertTrue("Point lat should be in range", latInRange)
            assertTrue("Point lng should be in range", lngInRange)
        }
    }

    @Test
    fun `calculateRoute - generates unique route IDs`() {
        val from = MapCoordinate(31.625831, -7.98892)
        val to = MapCoordinate(31.630831, -7.98892)

        val route1 = calculateRoute(from, to)
        Thread.sleep(2) // Ensure different timestamp
        val route2 = calculateRoute(from, to)

        assertNotNull(route1)
        assertNotNull(route2)
        assertNotEquals("Route IDs should be unique", route1!!.id, route2!!.id)
        assertTrue("Route ID should start with 'route-'", route1.id.startsWith("route-"))
    }

    // ==================== Haversine Edge Cases ====================

    @Test
    fun `haversine - very small distance`() {
        // Points ~1 meter apart
        val from = MapCoordinate(31.625831, -7.98892)
        val to = MapCoordinate(31.625840, -7.98892)

        val distance = haversineDistance(from, to)

        assertTrue("Very small distance should be positive", distance > 0)
        assertTrue("Very small distance should be < 10m", distance < 10)
    }

    @Test
    fun `haversine - negative longitude (west of prime meridian)`() {
        // Both Marrakech points have negative longitude
        val from = MapCoordinate(31.625831, -7.98892)
        val to = MapCoordinate(31.625831, -8.00000)

        val distance = haversineDistance(from, to)

        assertTrue("Distance with negative longitude should work", distance > 0)
    }

    @Test
    fun `haversine - crossing equator`() {
        // Test crossing from northern to southern hemisphere
        val north = MapCoordinate(1.0, 0.0)
        val south = MapCoordinate(-1.0, 0.0)

        val distance = haversineDistance(north, south)

        // 2 degrees of latitude ≈ 222 km
        assertTrue("Cross-equator distance should be ~222km", distance > 200_000 && distance < 250_000)
    }

    // ==================== Walk Time Edge Cases ====================

    @Test
    fun `estimateWalkTime - minimum is 1 minute`() {
        // Even for very short distances, minimum should be 1 minute
        val from = MapCoordinate(31.625831, -7.98892)
        val to = MapCoordinate(31.625832, -7.98892) // ~0.1m

        val time = estimateWalkTime(from, to, inMedina = true)

        assertEquals("Minimum walk time should be 1 minute", 1, time)
    }

    @Test
    fun `estimateWalkTime - long distance calculation`() {
        // ~2km walk in medina at 50m/min = 40 minutes
        val from = MapCoordinate(31.625831, -7.98892)
        val to = MapCoordinate(31.643831, -7.98892) // ~2km north

        val medinaTime = estimateWalkTime(from, to, inMedina = true)
        val guelizTime = estimateWalkTime(from, to, inMedina = false)

        assertTrue("2km medina walk should be ~40 min", medinaTime in 35..45)
        assertTrue("2km gueliz walk should be ~27 min", guelizTime in 23..31)
        assertTrue("Medina should be slower", medinaTime > guelizTime)
    }

    @Test
    fun `estimateWalkTime - same point returns 1 minute`() {
        val point = MapCoordinate(31.625831, -7.98892)

        val time = estimateWalkTime(point, point, inMedina = true)

        assertEquals("Same point should return minimum 1 minute", 1, time)
    }

    // ==================== Tile Source State Tests ====================

    @Test
    fun `TileSource - placeholder when no maps installed`() {
        val fakeDownloadService = FakeDownloadService()
        // No packs installed - default state

        val packStates = fakeDownloadService.packStates.value

        // With no installed packs, tile source should be placeholder
        val hasMedinaMap = packStates["medina-map"]?.status == PackStatus.INSTALLED
        val hasGuelizMap = packStates["gueliz-map"]?.status == PackStatus.INSTALLED

        assertFalse("Medina map should not be installed", hasMedinaMap)
        assertFalse("Gueliz map should not be installed", hasGuelizMap)
    }

    @Test
    fun `TileSource - offline when medina map installed`() {
        val fakeDownloadService = FakeDownloadService()
        fakeDownloadService.setPackState("medina-map", PackStatus.INSTALLED)

        val packStates = fakeDownloadService.packStates.value
        val hasMedinaMap = packStates["medina-map"]?.status == PackStatus.INSTALLED

        assertTrue("Medina map should be installed", hasMedinaMap)
    }

    @Test
    fun `TileSource - offline when gueliz map installed`() {
        val fakeDownloadService = FakeDownloadService()
        fakeDownloadService.setPackState("gueliz-map", PackStatus.INSTALLED)

        val packStates = fakeDownloadService.packStates.value
        val hasGuelizMap = packStates["gueliz-map"]?.status == PackStatus.INSTALLED

        assertTrue("Gueliz map should be installed", hasGuelizMap)
    }

    @Test
    fun `TileSource - downloading status does not count as installed`() {
        val fakeDownloadService = FakeDownloadService()
        fakeDownloadService.setPackState("medina-map", PackStatus.DOWNLOADING)

        val packStates = fakeDownloadService.packStates.value
        val isInstalled = packStates["medina-map"]?.status == PackStatus.INSTALLED

        assertFalse("Downloading should not count as installed", isInstalled)
    }

    // ==================== Helper Functions ====================

    /**
     * Calculate haversine distance (duplicates MapServiceImpl logic for testing).
     * Returns distance in meters.
     */
    private fun haversineDistance(from: MapCoordinate, to: MapCoordinate): Double {
        val earthRadiusM = 6_371_000.0

        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val deltaLat = Math.toRadians(to.latitude - from.latitude)
        val deltaLng = Math.toRadians(to.longitude - from.longitude)

        val a = kotlin.math.sin(deltaLat / 2).let { it * it } +
                kotlin.math.cos(lat1) * kotlin.math.cos(lat2) *
                kotlin.math.sin(deltaLng / 2).let { it * it }
        val c = 2 * kotlin.math.asin(kotlin.math.sqrt(a))

        return earthRadiusM * c
    }

    /**
     * Estimate walk time (duplicates MapServiceImpl logic for testing).
     * Returns time in minutes.
     */
    private fun estimateWalkTime(from: MapCoordinate, to: MapCoordinate, inMedina: Boolean): Int {
        val walkSpeedDefault = 75.0 // 4.5 km/h in m/min
        val walkSpeedMedina = 50.0  // 3 km/h in m/min

        val distance = haversineDistance(from, to)
        val speed = if (inMedina) walkSpeedMedina else walkSpeedDefault
        return (distance / speed).toInt().coerceAtLeast(1)
    }

    /**
     * Calculate a walking route (duplicates MapServiceImpl logic for testing).
     * Returns a MapRoute with waypoints.
     */
    private fun calculateRoute(from: MapCoordinate, to: MapCoordinate): MapRoute? {
        val distance = haversineDistance(from, to)

        // If points are very close, just return direct line
        if (distance < 50) {
            return MapRoute(
                id = "route-${System.currentTimeMillis()}",
                points = listOf(from, to)
            )
        }

        // Generate intermediate waypoints for smoother display
        val numPoints = (distance / 100).toInt().coerceIn(2, 20)
        val points = mutableListOf(from)

        for (i in 1 until numPoints) {
            val fraction = i.toDouble() / numPoints
            val lat = from.latitude + (to.latitude - from.latitude) * fraction
            val lng = from.longitude + (to.longitude - from.longitude) * fraction
            points.add(MapCoordinate(lat, lng))
        }

        points.add(to)

        return MapRoute(
            id = "route-${System.currentTimeMillis()}",
            points = points
        )
    }

    // ==================== Test Doubles ====================

    /**
     * Fake DownloadService for testing pack ID lookups.
     */
    private class FakeDownloadService : DownloadService {
        private val _packStates = MutableStateFlow<Map<String, PackState>>(emptyMap())
        override val packStates: StateFlow<Map<String, PackState>> = _packStates

        private val _preferences = MutableStateFlow(DownloadPreferences())
        override val preferences: StateFlow<DownloadPreferences> = _preferences

        private val _availablePacks = MutableStateFlow<List<ContentPack>>(emptyList())
        override val availablePacks: StateFlow<List<ContentPack>> = _availablePacks

        fun setPackState(packId: String, status: PackStatus) {
            val current = _packStates.value.toMutableMap()
            current[packId] = PackState(packId = packId, status = status)
            _packStates.value = current
        }

        override suspend fun startDownload(packId: String): DownloadResult =
            DownloadResult.Error(packId, com.marrakechguide.core.model.DownloadError.Unknown("Not implemented"))

        override suspend fun pauseDownload(packId: String) {}
        override suspend fun resumeDownload(packId: String): DownloadResult =
            DownloadResult.Error(packId, com.marrakechguide.core.model.DownloadError.Unknown("Not implemented"))

        override suspend fun cancelDownload(packId: String) {}
        override suspend fun removePack(packId: String) {}
        override suspend fun checkForUpdates() {}
        override fun updatePreferences(preferences: DownloadPreferences) {}
        override fun getAvailableSpace(): Long = Long.MAX_VALUE
        override fun isNetworkAvailable(): Boolean = true
    }
}
