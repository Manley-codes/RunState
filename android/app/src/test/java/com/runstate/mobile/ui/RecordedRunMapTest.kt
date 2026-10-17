package com.runstate.mobile.ui

import com.google.gson.JsonParser
import com.runstate.mobile.location.GpsMapCoordinate
import com.runstate.mobile.location.GpsMapEvidence
import com.runstate.mobile.location.GpsMapMarker
import com.runstate.mobile.location.GpsMapMarkerState
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class RecordedRunMapTest {
    private val coordinate = GpsMapCoordinate(41.0, -87.0)

    @Test
    fun tileStyleUsesOneDeclaredVectorSourceAndOneFontWithoutSprites() {
        val style = JsonParser.parseString(RecordedMapConfig.styleJson).asJsonObject
        assertEquals(8, style["version"].asInt)
        assertEquals(RecordedMapConfig.GLYPHS_URL, style["glyphs"].asString)
        assertFalse(style.has("sprite"))
        val sources = style["sources"].asJsonObject
        assertEquals(setOf("streets"), sources.keySet())
        val vector = sources["streets"].asJsonObject
        assertEquals("vector", vector["type"].asString)
        assertEquals(RecordedMapConfig.TILE_JSON_URL, vector["url"].asString)
        assertTrue(vector["attribution"].asString.contains("OpenStreetMap"))
        assertTrue(vector["attribution"].asString.contains("OpenMapTiles"))
        style["layers"].asJsonArray.map { it.asJsonObject }.forEach { layer ->
            assertFalse(layer["type"].asString == "raster")
            if (layer.has("source")) assertEquals("streets", layer["source"].asString)
            if (layer["type"].asString == "symbol") {
                assertEquals(listOf("Noto Sans Regular"),
                    layer["layout"].asJsonObject["text-font"].asJsonArray.map { it.asString })
            }
        }
        assertEquals(0, RecordedMapConfig.PREFETCH_ZOOM_DELTA)
        assertTrue(RecordedMapConfig.USER_AGENT.startsWith("RunState/"))
    }

    @Test
    fun nativeChecksCanRenderWithoutAnyRemoteMapResources() {
        val style = JsonParser.parseString(RecordedMapConfig.styleJson(loadStreetTiles = false)).asJsonObject
        assertTrue(style["sources"].asJsonObject.keySet().isEmpty())
        assertEquals("background", style["layers"].asJsonArray.single().asJsonObject["type"].asString)
        assertFalse(style.has("glyphs"))
        assertFalse(style.has("sprite"))
        assertFalse(style.toString().contains("https://"))
        assertFalse(style.toString().contains("http://"))
    }

    @Test
    fun tileRequestIdentifiesRunStateAndPreservesCacheValidation() {
        listOf(
            RecordedMapConfig.TILE_JSON_URL,
            "https://tiles.openfreemap.org/planet/20261011_080001_pt/14/100/200.pbf",
            "https://tiles.openfreemap.org/fonts/Noto%20Sans%20Regular/0-255.pbf"
        ).forEach { url ->
            val request = Request.Builder().url(url)
                .header("If-None-Match", "resource-version")
                .header("If-Modified-Since", "Thu, 15 Oct 2026 12:00:00 GMT")
                .header("User-Agent", "generic").build()
            val configured = recordedMapRequest(request)
            assertEquals("resource-version", configured.header("If-None-Match"))
            assertEquals(request.header("If-Modified-Since"), configured.header("If-Modified-Since"))
            assertNull(configured.header("Cache-Control"))
            assertEquals(RecordedMapConfig.USER_AGENT, configured.header("User-Agent"))
            assertEquals(request.url, configured.url)
        }
    }

    @Test
    fun undeclaredHostsCleartextAndDataBearingRequestsAreRejected() {
        listOf(
            "http://tiles.openfreemap.org/planet",
            "https://unexpected.example/planet",
            "https://tiles.openfreemap.org/planet?run=private",
            "https://tiles.openfreemap.org/planet#private",
            "https://user:secret@tiles.openfreemap.org/planet",
            "https://tiles.openfreemap.org:444/planet",
            "https://tiles.openfreemap.org/record-route",
            "https://tile.openstreetmap.org/14/100/200.png",
            "https://tiles.openfreemap.org/styles/liberty",
            "https://tiles.openfreemap.org/sprites/ofm_f384/ofm.png",
            "https://tiles.openfreemap.org/fonts/Noto%20Sans%20Bold/0-255.pbf"
        ).forEach { url ->
            assertThrows(url, IOException::class.java) {
                recordedMapRequest(Request.Builder().url(url).build())
            }
        }
        assertThrows(IOException::class.java) {
            recordedMapRequest(Request.Builder().url(RecordedMapConfig.TILE_JSON_URL)
                .post("{}".toRequestBody()).build())
        }
    }

    @Test
    fun landscapeColorsBelongToRealFeatureLayersAndPathsStayDistinctFromWater() {
        val layers = JsonParser.parseString(RecordedMapConfig.styleJson).asJsonObject["layers"]
            .asJsonArray.map { it.asJsonObject }
        val ground = layers.single { it["type"].asString == "background" }
        val buildings = layers.first { it["source-layer"]?.asString == "building" && it["type"].asString == "fill" }
        val water = layers.first { it["source-layer"]?.asString == "water" && it["type"].asString == "fill" }
        assertEquals(3, setOf(
            ground["paint"].asJsonObject["background-color"].asString,
            buildings["paint"].asJsonObject["fill-color"].asString,
            water["paint"].asJsonObject["fill-color"].asString
        ).size)
        assertTrue(layers.any { it["source-layer"]?.asString == "landcover" && it["type"].asString == "fill" })
        assertTrue(layers.any { it["source-layer"]?.asString == "landuse" && it["type"].asString == "fill" })
        assertTrue(layers.any { it["source-layer"]?.asString == "waterway" && it["type"].asString == "line" })
        assertTrue(layers.any {
            it["source-layer"]?.asString == "transportation" && it["type"].asString == "line" &&
                it["filter"].toString().contains("\"path\"") && it["paint"].asJsonObject.has("line-dasharray")
        })
        assertTrue(layers.any { it["source-layer"]?.asString == "transportation_name" && it["type"].asString == "symbol" })
        assertTrue(layers.any { it["source-layer"]?.asString == "place" && it["type"].asString == "symbol" })
    }

    @Test
    fun vectorRequestsUseOnlyValidTileCoordinatesAndAWeeklySourcePath() {
        val base = "https://tiles.openfreemap.org/planet/20261011_080001_pt/"
        listOf("0/0/0.pbf", "14/16383/16383.pbf").forEach { suffix ->
            assertEquals(base + suffix, recordedMapRequest(Request.Builder().url(base + suffix).build()).url.toString())
        }
        listOf(
            "0/1/0.pbf", "0/0/1.pbf", "14/16384/0.pbf", "14/0/16384.pbf",
            "15/0/0.pbf", "-1/0/0.pbf", "14/-1/0.pbf", "14/0/-1.pbf",
            "14/999999999999999999999/0.pbf", "14/0/0.png"
        ).map { base + it }.plus(
            "https://tiles.openfreemap.org/planet/unexpected/14/100/200.pbf"
        ).forEach { url ->
            assertThrows(url, IOException::class.java) {
                recordedMapRequest(Request.Builder().url(url).build())
            }
        }
    }

    @Test
    fun glyphRequestsAreLimitedToAlignedUnicodeBlocksForTheDeclaredFont() {
        val base = "https://tiles.openfreemap.org/fonts/Noto%20Sans%20Regular/"
        listOf("0-255.pbf", "256-511.pbf", "65280-65535.pbf").forEach { range ->
            assertEquals(base + range, recordedMapRequest(Request.Builder().url(base + range).build()).url.toString())
        }
        listOf("1-256.pbf", "0-256.pbf", "256-510.pbf", "65536-65791.pbf",
            "0-999999999999999999999.pbf", "-256--1.pbf").forEach { range ->
            assertThrows(range, IOException::class.java) {
                recordedMapRequest(Request.Builder().url(base + range).build())
            }
        }
    }

    @Test
    fun missingGpsAndSampleEvidenceNeverInventACameraLocation() {
        val waiting = GpsMapEvidence("run", true)
        assertNull(recordedMapAnchor(waiting))
        assertTrue(recordedMapStatus(waiting).contains("Waiting"))
        val sample = waiting.copy(isGpsRun = false, sections = listOf(listOf(coordinate)))
        assertNull(recordedMapAnchor(sample))
        assertTrue(recordedMapStatus(sample).contains("Sample"))
        val unavailable = waiting.copy(sections = listOf(listOf(coordinate)), evidenceUnavailable = true)
        assertNull(recordedMapAnchor(unavailable))
        assertTrue(recordedMapStatus(unavailable).contains("unavailable"))
    }

    @Test
    fun currentPausedAndOldLocationsHaveDifferentUserFacingStatus() {
        val current = GpsMapEvidence("run", true,
            marker = GpsMapMarker(coordinate, 5.0, GpsMapMarkerState.CURRENT))
        assertEquals(coordinate, recordedMapAnchor(current))
        assertEquals("Current recorded location", recordedMapStatus(current))
        val paused = current.copy(marker = current.marker!!.copy(state = GpsMapMarkerState.PAUSED))
        assertEquals("Paused · last recorded location", recordedMapStatus(paused))
        val old = current.copy(marker = current.marker.copy(state = GpsMapMarkerState.LAST_KNOWN))
        assertTrue(recordedMapStatus(old).contains("current position is unknown"))
    }

    @Test
    fun previewIsNeverDescribedAsARecordedRun() {
        val preview = GpsMapEvidence("foreground-gps-preview", true,
            marker = GpsMapMarker(coordinate, 5.0, GpsMapMarkerState.CURRENT))
        assertEquals("Current GPS location · run not started", recordedMapStatus(preview))
        assertFalse(recordedMapStatus(preview).contains("recorded"))
        assertEquals("Last known location · waiting for a fresh GPS fix",
            recordedMapStatus(preview.copy(marker = preview.marker!!.copy(state = GpsMapMarkerState.LAST_KNOWN))))
        assertEquals("Finding your GPS location · run not started", recordedMapStatus(preview.copy(marker = null)))
    }

    @Test
    fun routeOnlyEvidenceCanCenterWithoutClaimingCurrentPosition() {
        val evidence = GpsMapEvidence("run", true,
            sections = listOf(listOf(GpsMapCoordinate(40.0, -87.0)), emptyList(), listOf(coordinate)))
        assertEquals(coordinate, recordedMapAnchor(evidence))
        assertEquals("Recorded route · current position is unknown", recordedMapStatus(evidence))
    }

    @Test
    fun newUnanchoredRunReplacesPreviewMarkerAndRouteWithEmptySources() {
        val preview = GpsMapEvidence("ready-preview", true,
            sections = listOf(listOf(coordinate)),
            marker = GpsMapMarker(coordinate, 5.0, GpsMapMarkerState.CURRENT))
        assertEquals(coordinate, recordedMapOverlay(preview).marker!!.coordinate)

        val startedRun = GpsMapEvidence("started-run", true)
        val replacement = recordedMapOverlay(startedRun)
        assertEquals("started-run", replacement.ownerId)
        assertTrue(replacement.sections.isEmpty())
        assertNull(replacement.marker)
        assertNull(recordedMapAnchor(startedRun))
    }

    @Test
    fun missingUnavailableOrSampleEvidenceClearsEveryGeographicOverlay() {
        val recorded = GpsMapEvidence("run", true, sections = listOf(listOf(coordinate)),
            marker = GpsMapMarker(coordinate, 5.0, GpsMapMarkerState.CURRENT))
        listOf(null, recorded.copy(evidenceUnavailable = true), recorded.copy(isGpsRun = false))
            .forEach { evidence ->
                val overlay = recordedMapOverlay(evidence)
                assertTrue(overlay.sections.isEmpty())
                assertNull(overlay.marker)
                assertNull(recordedMapAnchor(evidence))
            }
        assertEquals("Waiting for a trustworthy GPS location.", recordedMapStatus(null))
    }

    @Test
    fun replacementRunKeepsOnlyItsOwnIndependentSections() {
        val firstSection = listOf(GpsMapCoordinate(41.0, -87.0), GpsMapCoordinate(41.01, -87.0))
        val secondSection = listOf(GpsMapCoordinate(41.02, -87.0))
        val evidence = GpsMapEvidence("new-owner", true, listOf(firstSection, secondSection))
        val overlay = recordedMapOverlay(evidence)
        assertEquals("new-owner", overlay.ownerId)
        assertEquals(listOf(firstSection, secondSection), overlay.sections)
        assertNull(overlay.marker)
    }
}
