package com.runstate.mobile.ui

import com.google.gson.JsonParser
import com.runstate.mobile.trails.TrailCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailVisualsTest {
    private val first = TrailCoordinate(29.76, -95.46)
    private val second = TrailCoordinate(29.77, -95.45)
    private val third = TrailCoordinate(29.78, -95.44)
    private val fourth = TrailCoordinate(29.79, -95.43)

    @Test
    fun sourceSegmentsStaySeparateAndInvalidCoordinatesBreakRatherThanBridge() {
        val lines = trailSnapshotLines(listOf(
            listOf(first, second), listOf(third, fourth),
            listOf(first, second, TrailCoordinate(Double.NaN, -95.4), third, fourth)
        ))
        assertEquals(listOf(listOf(first, second), listOf(third, fourth),
            listOf(first, second), listOf(third, fourth)), lines)
        assertTrue(trailSnapshotLines(listOf(listOf(first))).isEmpty())
    }

    @Test
    fun oversizedEvidenceIsOmittedRatherThanPresentedAsACompleteShortenedRoute() {
        assertTrue(trailSnapshotLines(listOf(List(20_001) { first })).isEmpty())
    }

    @Test
    fun snapshotUsesDeclaredMapTilesAndRealSeparateGeoJsonLines() {
        val style = JsonParser.parseString(trailSnapshotStyle(listOf(
            listOf(first, second), listOf(third, fourth)))).asJsonObject
        val sources = style.getAsJsonObject("sources")
        assertEquals(RecordedMapConfig.TILE_JSON_URL, sources.getAsJsonObject("streets")["url"].asString)
        val features = sources.getAsJsonObject("trail-discovery").getAsJsonObject("data")
            .getAsJsonArray("features")
        assertEquals(2, features.size())
        val coordinate = features[0].asJsonObject.getAsJsonObject("geometry")
            .getAsJsonArray("coordinates")[0].asJsonArray
        assertEquals(first.longitude, coordinate[0].asDouble, 0.0)
        assertEquals(first.latitude, coordinate[1].asDouble, 0.0)
        assertFalse(style.toString().contains("runstate-route"))
    }

    @Test
    fun remotePhotosOnlyUseBoundedLoaderForWikimediaImageFiles() {
        assertTrue(allowedTrailPhotoUrl("https://upload.wikimedia.org/wikipedia/commons/thumb/a/a1/Park.jpg/960px-Park.jpg"))
        assertTrue(allowedTrailPhotoUrl("https://thumb.wikimedia.org/wikipedia/commons/thumb/a/a1/Park.jpg/960px-Park.jpg"))
        listOf(
            "http://upload.wikimedia.org/wikipedia/commons/a/a1/Park.jpg",
            "https://upload.wikimedia.org.evil.example/wikipedia/commons/a/a1/Park.jpg",
            "https://user:password@upload.wikimedia.org/wikipedia/commons/a/a1/Park.jpg",
            "https://upload.wikimedia.org/wikipedia/commons/a/a1/Park.jpg?tracking=1",
            "https://upload.wikimedia.org/wikipedia/commons/a/a1/Park.svg",
            "https://127.0.0.1/wikipedia/commons/a/a1/Park.jpg"
        ).forEach { assertFalse(it, allowedTrailPhotoUrl(it)) }
    }
}
