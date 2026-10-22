package com.runstate.mobile.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Standalone screen checks: no RunState Application, database, run, provider or audio. */
@RunWith(AndroidJUnit4::class)
class OpeningScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private class ScreenLifecycle : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun visibleTimePausesInBackgroundAndFinishesOnlyOnce() {
        compose.mainClock.autoAdvance = false
        val owner = ScreenLifecycle()
        var finished = 0
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.setContent {
            OpeningScreen({ finished++ }, owner.lifecycle, motionEnabledOverride = true)
        }
        waitForBundledPhotos()
        compose.mainClock.advanceTimeBy(4_000)
        compose.runOnUiThread { assertEquals(0, finished) }

        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.mainClock.advanceTimeBy(5_000)
        compose.runOnUiThread { assertEquals(0, finished) }

        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.mainClock.advanceTimeBy(3_000)
        compose.runOnUiThread { assertEquals(0, finished) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnUiThread { assertEquals(1, finished) }
        compose.mainClock.advanceTimeBy(5_000)
        compose.runOnUiThread { assertEquals(1, finished) }
    }

    @Test
    fun tapSkipsImmediatelyAndCannotRaceTheAutomaticFinish() {
        compose.mainClock.autoAdvance = false
        val owner = ScreenLifecycle()
        var finished = 0
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.setContent {
            OpeningScreen({ finished++ }, owner.lifecycle, motionEnabledOverride = true)
        }
        compose.onNodeWithTag(OPENING_SCREEN_TAG).assertIsDisplayed().performClick()
        compose.runOnUiThread { assertEquals(1, finished) }
        compose.mainClock.advanceTimeBy(10_000)
        compose.onNodeWithTag(OPENING_SCREEN_TAG).performClick()
        compose.runOnUiThread { assertEquals(1, finished) }
    }

    @Test
    fun backSkipsWithoutLeavingTheActivity() {
        compose.mainClock.autoAdvance = false
        val owner = ScreenLifecycle()
        var finished = 0
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.setContent { OpeningScreen({ finished++ }, owner.lifecycle) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnUiThread {
            assertEquals(1, finished)
            assertEquals(false, compose.activity.isFinishing)
        }
    }

    @Test
    fun reducedMotionKeepsPhotographsAndWordmarkStillButStillExits() {
        compose.mainClock.autoAdvance = false
        val owner = ScreenLifecycle()
        var finished = 0
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.setContent {
            OpeningScreen({ finished++ }, owner.lifecycle, motionEnabledOverride = false)
        }
        waitForBundledPhotos()
        val before = screenFingerprint()
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.mainClock.advanceTimeBy(1_500)
        assertEquals(before, screenFingerprint())
        compose.mainClock.advanceTimeBy(3_500)
        compose.runOnUiThread { assertEquals(1, finished) }
    }

    @Test
    fun enabledMotionMovesThePhotoWallAndStopsWhileNotResumed() {
        compose.mainClock.autoAdvance = false
        val owner = ScreenLifecycle()
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.setContent {
            OpeningScreen({}, owner.lifecycle, motionEnabledOverride = true)
        }
        waitForBundledPhotos()
        val before = screenFingerprint()
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.mainClock.advanceTimeBy(1_600)
        val moving = screenFingerprint()
        assertNotEquals(before, moving)
        saveScreen("opening-native.png")
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.mainClock.advanceTimeByFrame()
        val paused = screenFingerprint()
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(paused, screenFingerprint())
    }

    @Test
    fun compactLandscapeWithLargeTextStillOffersFullScreenSkip() {
        compose.mainClock.autoAdvance = false
        val owner = ScreenLifecycle()
        var finished = 0
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                Box(Modifier.size(320.dp, 180.dp)) {
                    OpeningScreen({ finished++ }, owner.lifecycle, motionEnabledOverride = false)
                }
            }
        }
        waitForBundledPhotos()
        compose.onNodeWithTag(OPENING_SCREEN_TAG).assertIsDisplayed()
        saveScreen("opening-native-compact.png")
        compose.onNodeWithTag(OPENING_SCREEN_TAG).performClick()
        compose.runOnUiThread { assertEquals(1, finished) }
    }

    private fun waitForBundledPhotos() {
        compose.mainClock.advanceTimeByFrame()
        // The ready tag requires all 33 assets to decode; a timeout/partial or empty
        // fallback must not pass the visual and motion checks below.
        compose.waitUntil(10_000) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithTag("openingPhotosReady", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, compose.onAllNodesWithTag("openingPhotosUnavailable", useUnmergedTree = true)
            .fetchSemanticsNodes().size)
    }

    /** Sample actual rendered pixels, so a stationary wall cannot pass a motion check. */
    private fun screenFingerprint(): Long {
        val pixels = compose.onNodeWithTag(OPENING_SCREEN_TAG).captureToImage().toPixelMap()
        var hash = 1L
        for (y in 0 until pixels.height step 11) {
            for (x in 0 until pixels.width step 11) hash = 31 * hash + pixels[x, y].value.toLong()
        }
        return hash
    }

    private fun saveScreen(name: String) {
        val bitmap = compose.onNodeWithTag(OPENING_SCREEN_TAG).captureToImage().asAndroidBitmap()
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        File(cache, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
