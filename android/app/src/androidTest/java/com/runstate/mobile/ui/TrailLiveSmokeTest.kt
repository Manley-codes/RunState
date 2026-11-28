package com.runstate.mobile.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.runstate.mobile.data.local.DistanceUnit
import com.runstate.mobile.trails.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Explicit opt-in: public data/maps only. Normal CI stays offline and deterministic. */
class TrailLiveSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun realNearbyPathsAndBundledPhotoRenderTogether() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runstate.trailLiveSmoke") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = TrailRepository(context)
        val catalog = repository.bundled()
        assertTrue(catalog.size >= 60)
        val result = runBlocking { repository.nearby(TrailCoordinate(29.76, -95.37)) }
        assertFalse("USGS must be reached or have a valid recent cached response", result.networkUnavailable)
        val route = result.trails.first { it.routeLines.any { line -> line.size > 2 } }
        assertTrue(route.isSegment)
        val memorial = catalog.first { it.id == "memorial" }
        var page by androidx.compose.runtime.mutableStateOf(memorial)
        compose.setContent {
            MaterialTheme { key(page.id) {
                TrailBrowserSheet(page.id, null, null, true, DistanceUnit.MILES, {}, catalogOverride = listOf(page))
            } }
        }
        compose.waitUntil(30_000) { compose.onAllNodesWithContentDescription("Map of ${memorial.name}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("${memorial.name} photograph").assertIsDisplayed()
        saveScreenshot("memorial-detail.png")
        compose.runOnIdle { page = route }
        compose.waitUntil(30_000) { compose.onAllNodesWithContentDescription("Map of ${route.name}").fetchSemanticsNodes().isNotEmpty() }
        saveScreenshot("usgs-route.png")
        val remote = catalog.first { it.id == "bear_creek" || it.name == "Bear Creek Pioneers Park" }
        compose.runOnIdle { page = remote }
        compose.waitUntil(30_000) { compose.onAllNodesWithContentDescription("${remote.name} photograph").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("trailMapThumbnail:${remote.id}").assertIsDisplayed()
        saveScreenshot("remote-photo.png")
    }

    private fun saveScreenshot(name: String) {
        // Let the native window draw the already-observed Compose image state before capture.
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(400)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "trail-review").apply { mkdirs() }
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(directory, name).outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }
}
