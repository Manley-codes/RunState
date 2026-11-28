package com.runstate.mobile.trails

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException

class TrailRepositoryTest {
    @Test
    fun `bundled trail catalog retains original places and offers sourced filter coverage`() {
        val trails = TrailDataParser.curated(File("src/main/assets/trails/curated_trails.json").readText())
        assertTrue(trails.size >= 70)
        assertTrue(trails.map { it.id }.containsAll(listOf("memorial", "hermann", "buffalo_bayou", "terry_hershey")))
        assertTrue(trails.filter { it.city == "Houston" }.size >= 16)
        assertTrue(trails.count { it.photo != null } >= 40)
        assertTrue(TrailElevation.entries.all { bucket -> trails.any { TrailFilters(elevation = bucket).matches(it) } })
        assertTrue(TrailDifficulty.entries.all { difficulty -> trails.any { it.difficulty == difficulty } })
        assertTrue(trails.all { it.center.valid && it.website.startsWith("https://") })
        assertTrue(trails.all { it.photo == null || (it.photo.credit.isNotBlank() && it.photo.sourceUrl.startsWith("https://") && it.photo.license.isNotBlank()) })
    }

    @Test
    fun `source line coordinates use longitude first and unknown details stay unknown`() {
        val trail = TrailDataParser.usgs(collection(feature())).trails.single()
        assertEquals(TrailCoordinate(29.76, -95.37), trail.routeLines.single().first())
        assertNull(trail.difficulty)
        assertNull(trail.elevationGainMeters)
        assertNull(trail.lengthMeters)
        assertNull(trail.trailhead)
        assertNull(trail.photo)
        assertTrue(trail.surfaces.isEmpty())
        assertTrue(trail.isSegment)
    }

    @Test
    fun `malformed points split a line rather than creating an invented connecting line`() {
        val geometry = """{"type":"LineString","coordinates":[[-95.37,29.76],[-95.36,29.76],[200,95],[-95.34,29.76],[-95.33,29.76]]}"""
        val trail = TrailDataParser.usgs(collection(feature(geometry = geometry))).trails.single()
        assertEquals(listOf(2, 2), trail.routeLines.map { it.size })
        assertEquals(-95.36, trail.routeLines.first().last().longitude, 0.0)
        assertEquals(-95.34, trail.routeLines.last().first().longitude, 0.0)
    }

    @Test
    fun `multilines stay disconnected and repeated points do not become fake lengths`() {
        val geometry = """{"type":"MultiLineString","coordinates":[[[-95.37,29.76],[-95.37,29.76],[-95.36,29.76]],[[-95.20,29.80],[-95.19,29.80]]]}"""
        val trail = TrailDataParser.usgs(collection(feature(geometry = geometry))).trails.single()
        assertEquals(listOf(2, 2), trail.routeLines.map { it.size })
        assertNull(trail.lengthMeters)
    }

    @Test
    fun `water snow explicit pedestrian prohibitions and unnamed noise are excluded`() {
        val json = collection(
            feature(id = "1", kind = "Water Trail"), feature(id = "2", kind = "Snow Trail"),
            feature(id = "3", pedestrian = "No"), feature(id = "4", name = "Unnamed Trail"),
            feature(id = "5", name = ""), feature(id = "6", pedestrian = "Unknown")
        )
        assertEquals(listOf("usgs:6"), TrailDataParser.usgs(json).trails.map { it.id })
    }

    @Test
    fun `broad unpaved categories never become guessed dirt or gravel`() {
        val result = TrailDataParser.usgs(collection(feature(id = "1", surface = "Unpaved Native"), feature(id = "2", surface = "Paved")))
        assertTrue(result.trails[0].surfaces.isEmpty())
        assertEquals(setOf(TrailSurface.PAVED), result.trails[1].surfaces)
    }

    @Test
    fun `transfer flags and source errors are not mistaken for complete empty results`() {
        assertTrue(TrailDataParser.usgs("""{"type":"FeatureCollection","features":[],"properties":{"exceededTransferLimit":true}}""").moreAvailable)
        assertThrows(IOException::class.java) { TrailDataParser.usgs("""{"error":{"code":500}}""") }
        assertThrows(IOException::class.java) { TrailDataParser.usgs("""{"type":"FeatureCollection"}""") }
    }

    @Test
    fun `overlong geometry is skipped and coverage is marked incomplete`() {
        val points = (0..8_000).joinToString(",") { "[-95.37,29.76]" }
        val result = TrailDataParser.usgs(collection(feature(geometry = """{"type":"LineString","coordinates":[$points]}""")))
        assertTrue(result.trails.isEmpty())
        assertTrue(result.moreAvailable)
    }

    @Test
    fun `curated parser tolerates missing optional data but rejects invalid coordinates and unsafe links`() {
        val json = """[
          {"id":"valid","name":"A park","center":{"latitude":29.76,"longitude":-95.37},"website":"http://example.com","difficulty":"invented","surfaces":["PAVED","unverified"],"elevationGainMeters":-3},
          {"id":"bad","name":"Bad park","center":{"latitude":95,"longitude":-95}},
          {"id":"missing","name":"Missing center"}
        ]"""
        val place = TrailDataParser.curated(json).single()
        assertEquals("valid", place.id)
        assertEquals("", place.website)
        assertEquals(setOf(TrailSurface.PAVED), place.surfaces)
        assertNull(place.difficulty)
        assertNull(place.elevationGainMeters)
    }

    @Test
    fun `curated segments remain separate through cache serialization format`() {
        val json = """{"trails":[{"id":"test","name":"Trail","center":{"latitude":29.76,"longitude":-95.37},"routeLines":[[{"latitude":29.76,"longitude":-95.37},{"latitude":29.77,"longitude":-95.37}],[{"latitude":29.80,"longitude":-95.20},{"latitude":29.81,"longitude":-95.20}]],"isSegment":true}]}"""
        val original = TrailDataParser.curated(json).single()
        val restored = TrailDataParser.curated(com.google.gson.Gson().toJson(listOf(original))).single()
        assertEquals(original, restored)
        assertEquals(2, restored.routeLines.size)
    }

    @Test
    fun `cities preserve same name across states and search accepts a state suffix`() {
        val cities = TrailDataParser.cities("Portland\tME\t43.66\t-70.25\nPortland\tOR\t45.52\t-122.68\nHouston\tTX\t29.76\t-95.37")
        assertEquals(listOf("ME", "OR"), TrailDataParser.searchCities(cities, "Portland").map { it.state })
        assertEquals("OR", TrailDataParser.searchCities(cities, "Portland, OR").single().state)
        assertTrue(TrailDataParser.searchCities(cities, "P").isEmpty())
    }

    @Test
    fun `city parsing skips malformed records and search ignores accent differences`() {
        val cities = TrailDataParser.cities("# source\nSan José\tCA\t37.33\t-121.89\nInvalid\tTX\tNaN\t-95\nMissing\tTX")
        assertEquals("San José", TrailDataParser.searchCities(cities, "San Jose").single().name)
        assertEquals(1, cities.size)
    }

    @Test
    fun `nearby never falls back to Houston when another city has no entries`() {
        val houston = TrailPlace("houston", "Local trail", center = TrailCoordinate(29.76, -95.37))
        assertTrue(TrailDataParser.nearby(listOf(houston), TrailCoordinate(47.61, -122.33), 40.0).isEmpty())
    }

    @Test
    fun `same named connected segments combine without joining or merging distant namesakes`() {
        val a = TrailDataParser.usgs(collection(feature(id = "a"))).trails.single()
        val b = a.copy(id = "b", routeLines = listOf(listOf(TrailCoordinate(29.761, -95.37), TrailCoordinate(29.762, -95.37))))
        val far = a.copy(id = "far", center = TrailCoordinate(30.0, -95.37), routeLines = listOf(listOf(TrailCoordinate(30.0, -95.37), TrailCoordinate(30.001, -95.37))))
        val result = TrailDataParser.combineSegments(listOf(a, b, far))
        assertEquals(2, result.size)
        assertEquals(2, result.first().routeLines.size)
        assertNull(result.first().lengthMeters)
    }

    @Test
    fun `bundled census cities include nationwide places and unambiguous Houston Texas`() {
        val asset = File("src/main/assets/trails/us_cities.tsv")
        assertTrue("Census city asset missing", asset.isFile)
        val cities = TrailDataParser.cities(asset.readText())
        assertTrue(cities.size >= 30_000)
        assertEquals("TX", TrailDataParser.searchCities(cities, "Houston TX").first().state)
        assertTrue(TrailDataParser.searchCities(cities, "Honolulu HI").isNotEmpty())
        assertTrue(TrailDataParser.searchCities(cities, "Anchorage AK").isNotEmpty())
        assertTrue(TrailDataParser.searchCities(cities, "Portland").map { it.state }.containsAll(listOf("OR", "ME")))
    }

    private fun collection(vararg features: String): String = """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""

    private fun feature(
        id: String = "test", name: String = "Example Trail", kind: String = "Terra Trail", surface: String = "Unknown",
        pedestrian: String = "Unknown",
        geometry: String = """{"type":"LineString","coordinates":[[-95.37,29.76],[-95.37,29.761]]}"""
    ): String = """{"type":"Feature","properties":{"permanentidentifier":"$id","name":"$name","trailtype":"$kind","trailsurface":"$surface","hikerpedestrian":"$pedestrian","sourceoriginator":"Official park agency"},"geometry":$geometry}"""
}
