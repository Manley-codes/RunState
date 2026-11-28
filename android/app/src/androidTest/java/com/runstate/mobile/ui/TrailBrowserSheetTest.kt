package com.runstate.mobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.runstate.mobile.data.local.DistanceUnit
import com.runstate.mobile.trails.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Synthetic catalog and discovery responses; never uses GPS, map tiles, a provider or run storage. */
@RunWith(AndroidJUnit4::class)
class TrailBrowserSheetTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val houston = TrailCoordinate(29.76, -95.37)
    private val portland = TrailCoordinate(45.52, -122.68)
    private val photo = TrailPhoto(
        resourceName = "trail_memorial_photo", credit = "Fixture photographer", license = "CC BY 2.0",
        sourceUrl = "https://commons.wikimedia.org/wiki/File:Fixture.jpg", licenseUrl = "https://creativecommons.org/licenses/by/2.0/"
    )
    private val easy = TrailPlace("easy", "Bayou path", city = "Houston", state = "TX", center = houston,
        website = "https://example.org/park", photo = photo, difficulty = TrailDifficulty.EASY,
        surfaces = setOf(TrailSurface.PAVED), elevationGainMeters = 12.0,
        routeLines = listOf(listOf(houston, TrailCoordinate(29.761, -95.37))))
    private val unknown = TrailPlace("unknown", "Unlisted path", city = "Houston", state = "TX", center = houston)

    @Test
    fun trailNameSearchAndSelectedCityUseTheirOwnResults() {
        val requests = mutableListOf<TrailCoordinate>()
        val oregon = TrailPlace("oregon", "Forest path", city = "Portland", state = "OR", center = portland)
        compose.setContent {
            MaterialTheme {
                TrailBrowserSheet(null, houston, null, true, DistanceUnit.MILES, {}, allowNetwork = false,
                    catalogOverride = listOf(easy, unknown, oregon),
                    discoverOverride = { center -> requests += center; TrailDiscoveryResult(if (center.latitude > 40) listOf(oregon) else listOf(easy, unknown)) },
                    citiesOverride = { query -> if (query.contains("Portland", true)) listOf(TrailCity("Portland", "OR", portland)) else emptyList() })
            }
        }
        compose.onNodeWithTag(TRAIL_SEARCH_TAG).performTextInput("Bayou")
        compose.onNodeWithTag(START_TRAIL_LIST_ITEM_TAG_PREFIX + "easy").assertIsDisplayed()
        compose.onNodeWithTag(START_TRAIL_LIST_ITEM_TAG_PREFIX + "unknown").assertDoesNotExist()
        compose.onNodeWithTag(TRAIL_SEARCH_TAG).performTextReplacement("Portland")
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("trailCity:Portland, OR").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("trailCity:Portland, OR").performClick()
        compose.onNodeWithTag("trailArea").assertTextEquals("Portland, OR")
        compose.waitUntil(5_000) { requests.any { it == portland } }
        compose.onNodeWithTag(START_TRAIL_LIST_ITEM_TAG_PREFIX + "oregon").assertIsDisplayed()
        compose.onNodeWithTag(START_TRAIL_LIST_ITEM_TAG_PREFIX + "easy").assertDoesNotExist()
        compose.onNodeWithText("More places around Houston", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("startExpandMap").assertDoesNotExist()
    }

    @Test
    fun specificDifficultySurfaceAndElevationExcludeUnknownDetailsUntilReset() {
        compose.setContent {
            MaterialTheme {
                TrailBrowserSheet(null, null, null, true, DistanceUnit.MILES, {}, allowNetwork = false,
                    catalogOverride = listOf(unknown, easy), citiesOverride = { emptyList() })
            }
        }
        for (label in listOf("Easy", "Paved", "Under 300 ft")) {
            compose.onNodeWithTag(TRAIL_FILTER_TAG).performClick()
            compose.onNodeWithText(label).performScrollTo().performClick()
            compose.onNodeWithText("Show trails").performClick()
            compose.onNodeWithTag(START_TRAIL_LIST_ITEM_TAG_PREFIX + "easy").assertIsDisplayed()
            compose.onNodeWithTag(START_TRAIL_LIST_ITEM_TAG_PREFIX + "unknown").assertDoesNotExist()
            compose.onNodeWithTag(TRAIL_FILTER_TAG).performClick()
            compose.onNodeWithText("Reset").performClick()
            compose.onNodeWithText("Show trails").performClick()
            compose.onNodeWithTag(START_TRAIL_LIST_ITEM_TAG_PREFIX + "unknown").assertIsDisplayed()
        }
    }

    @Test
    fun detailShowsFullPhotoWithBottomRightMapAndDirectionsOnlyCallsItsDestination() {
        val destination = AtomicReference<TrailPlace?>(null)
        val calls = AtomicInteger()
        compose.setContent {
            MaterialTheme {
                TrailBrowserSheet(easy.id, null, null, true, DistanceUnit.MILES, {}, allowNetwork = false,
                    catalogOverride = listOf(easy), citiesOverride = { emptyList() },
                    onDirectionsOverride = { destination.set(it); calls.incrementAndGet() })
            }
        }
        val hero = compose.onNodeWithTag("trailPhotoHero:easy").assertIsDisplayed().getUnclippedBoundsInRoot()
        val inset = compose.onNodeWithTag("trailMapThumbnail:easy").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("The inset must remain inside the photo", inset.left >= hero.left && inset.top >= hero.top && inset.right <= hero.right && inset.bottom <= hero.bottom)
        assertTrue("The small map belongs in the lower-right corner", inset.left > (hero.left + hero.right) / 2 && inset.top > hero.top)
        assertTrue("The main photograph must remain larger than its map", hero.right - hero.left > (inset.right - inset.left) * 2)
        compose.onNodeWithContentDescription("Bayou path photograph").assertIsDisplayed()
        compose.onNodeWithText("Photo license").assertDoesNotExist()
        compose.onNodeWithText("Area overview", substring = true).assertDoesNotExist()
        compose.onNodeWithTag(START_TRAIL_USE_TAG).assertDoesNotExist()
        compose.onNodeWithTag(START_RUN_BUTTON_TAG).assertDoesNotExist()
        compose.onNodeWithTag("trailParkInformation").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(TRAIL_DIRECTIONS_TAG).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, calls.get()); assertEquals(easy, destination.get()) }
        compose.onNodeWithTag(START_TRAIL_SHEET_TAG).assertIsDisplayed()
        compose.onNodeWithTag("trailBack").performClick()
        compose.onNodeWithTag(START_TRAILS_LIST_TAG).assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, calls.get()) }
    }

    @Test
    fun creditsStayAccessibleWithoutClutteringTrailDetails() {
        compose.setContent {
            MaterialTheme {
                TrailBrowserSheet(easy.id, null, null, true, DistanceUnit.MILES, {}, allowNetwork = false,
                    catalogOverride = listOf(easy), citiesOverride = { emptyList() })
            }
        }
        compose.onNodeWithText("Fixture photographer", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Trail and photo credits").performClick()
        compose.onNodeWithText("Trail & photo credits").assertIsDisplayed()
        compose.onNodeWithText("Fixture photographer · CC BY 2.0").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Photo license").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Original photo").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun offlineResultsRemainUsableAndRetryReplacesTheFailure() {
        val calls = AtomicInteger()
        compose.setContent {
            MaterialTheme {
                TrailBrowserSheet(null, houston, null, true, DistanceUnit.MILES, {}, allowNetwork = false,
                    catalogOverride = listOf(easy), citiesOverride = { emptyList() },
                    discoverOverride = { TrailDiscoveryResult(listOf(easy), networkUnavailable = calls.incrementAndGet() == 1) })
            }
        }
        compose.waitUntil(5_000) { calls.get() == 1 }
        compose.onNodeWithText("Showing saved trails. Nearby search is unavailable.").assertIsDisplayed()
        compose.onNodeWithTag(START_TRAIL_LIST_ITEM_TAG_PREFIX + "easy").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        compose.waitUntil(5_000) { calls.get() == 2 }
        compose.onNodeWithText("Showing saved trails. Nearby search is unavailable.").assertDoesNotExist()
        compose.onNodeWithTag(START_TRAIL_LIST_ITEM_TAG_PREFIX + "easy").performClick()
        compose.onNodeWithTag(START_TRAIL_SHEET_TAG).assertIsDisplayed()
    }

    @Test
    fun locationIsRequestedOnlyByNearMeAndDisabledActionsCannotInvokeIt() {
        var enabled by mutableStateOf(true)
        var locationRequests = 0
        compose.setContent {
            MaterialTheme {
                TrailBrowserSheet(null, null, { locationRequests++ }, enabled, DistanceUnit.MILES, {}, allowNetwork = false,
                    catalogOverride = listOf(easy), citiesOverride = { emptyList() })
            }
        }
        compose.runOnIdle { assertEquals(0, locationRequests) }
        compose.onNodeWithTag("trailNearby").performClick()
        compose.runOnIdle { assertEquals(1, locationRequests); enabled = false }
        compose.onNodeWithTag("trailNearby").assertIsNotEnabled().performTouchInput { click() }
        compose.onNodeWithTag(TRAIL_FILTER_TAG).assertIsNotEnabled()
        compose.onNodeWithTag(TRAIL_SEARCH_TAG).assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, locationRequests) }
    }

    @Test
    fun absentNearbyCoverageShowsEmptyStateRatherThanUnrelatedHoustonTrails() {
        compose.setContent {
            MaterialTheme {
                TrailBrowserSheet(null, portland, null, true, DistanceUnit.MILES, {}, allowNetwork = false,
                    catalogOverride = listOf(easy), citiesOverride = { emptyList() },
                    discoverOverride = { TrailDiscoveryResult(emptyList()) })
            }
        }
        compose.onNodeWithText("No matching trails").assertIsDisplayed()
        compose.onNodeWithTag(START_TRAIL_LIST_ITEM_TAG_PREFIX + "easy").assertDoesNotExist()
    }
}
