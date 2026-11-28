package com.runstate.mobile.trails

import org.junit.Assert.*
import org.junit.Test
import java.net.URLDecoder

class TrailFiltersTest {
    private val trail = TrailPlace("test", "Bayou Trail", city = "Houston", state = "TX",
        center = TrailCoordinate(29.7, -95.4))

    @Test fun unknownMetadataDoesNotPretendToMeetSpecificFilters() {
        assertTrue(TrailFilters().matches(trail))
        assertFalse(TrailFilters(difficulty = TrailDifficulty.EASY).matches(trail))
        assertFalse(TrailFilters(surface = TrailSurface.DIRT).matches(trail))
        assertFalse(TrailFilters(elevation = TrailElevation.LOW).matches(trail))
    }
    @Test fun filtersCombineAndRespectElevationBoundaries() {
        val known = trail.copy(difficulty = TrailDifficulty.EASY, surfaces = setOf(TrailSurface.PAVED),
            elevationGainMeters = 300 * 0.3048)
        assertTrue(TrailFilters(TrailDifficulty.EASY, TrailSurface.PAVED, TrailElevation.MEDIUM).matches(known))
        assertFalse(TrailFilters(elevation = TrailElevation.LOW).matches(known))
        assertTrue(TrailFilters(elevation = TrailElevation.HIGH).matches(known.copy(elevationGainMeters = 400.0)))
        assertFalse(TrailFilters(elevation = TrailElevation.LOW).matches(known.copy(elevationGainMeters = Double.NaN)))
    }
    @Test fun multiWordSearchMatchesCityAndNameWithoutDuplicates() {
        assertEquals(listOf(trail), searchTrails(listOf(trail, trail), " HOUSTON bayou ", TrailFilters()))
        assertTrue(searchTrails(listOf(trail), "Dallas", TrailFilters()).isEmpty())
    }
    @Test fun directionsUseVerifiedEntranceNotMapCenter() {
        val named = URLDecoder.decode(trailNavigationUri(trail), "UTF-8")
        assertTrue(named.contains("Bayou Trail, Houston, TX, USA"))
        assertTrue(named.startsWith("geo:29.7,-95.4?q="))
        assertFalse(named.substringAfter("?q=").contains("29.7"))
        val entrance = trail.copy(trailhead = TrailCoordinate(29.8, -95.5))
        assertTrue(URLDecoder.decode(trailNavigationUri(entrance), "UTF-8").contains("29.8,-95.5"))
        assertFalse(trailNavigationUri(trail.copy(trailhead = TrailCoordinate(999.0, 0.0))).contains("999"))
    }
}
