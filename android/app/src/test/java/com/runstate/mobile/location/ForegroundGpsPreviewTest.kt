package com.runstate.mobile.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundGpsPreviewTest {
    @Test
    fun `quiet start label distinguishes live fix cache and signal trouble`() {
        val evidence = ForegroundGpsPreviewEvidence()
        assertEquals("GPS connecting…", evidence.state(1_000).startStatusText)
        evidence.observe(sample(1_000), 1_000, isLive = false)
        assertEquals("GPS connecting…", evidence.state(1_000).startStatusText)
        evidence.observe(sample(2_000), 2_000)
        assertEquals("GPS ready", evidence.state(2_000).startStatusText)
        assertEquals("GPS connecting…", evidence.state(7_001).startStatusText)
        assertEquals("GPS signal lost", evidence.state(17_001).startStatusText)
        evidence.observe(sample(18_000).copy(horizontalAccuracyMeters = 90.0), 18_000)
        assertFalse(evidence.state(18_000).hasFreshLiveFix)
        assertEquals("Location off", ForegroundGpsPreviewState(
            ForegroundGpsPreviewStatus.LOCATION_OFF).startStatusText)
        assertEquals("Allow precise location", ForegroundGpsPreviewState(
            ForegroundGpsPreviewStatus.PERMISSION_NEEDED).startStatusText)
    }

    @Test
    fun `fresh cached fix is current only through its original fifteen second lifetime`() {
        val evidence = ForegroundGpsPreviewEvidence()
        evidence.observe(sample(1_000), 10_000, isLive = false)
        assertFalse(evidence.state(10_000).hasFreshLiveFix)
        assertEquals(ForegroundGpsPreviewStatus.CURRENT, evidence.state(16_000).status)
        assertEquals(ForegroundGpsPreviewStatus.STALE, evidence.state(16_001).status)
        assertEquals(GpsMapMarkerState.LAST_KNOWN, evidence.state(16_001).marker?.state)
    }

    @Test
    fun `a current cached map fix cannot claim fresh live signal`() {
        val harness = Harness()
        harness.source.cached = sample(1_000)
        harness.session.start()
        assertEquals(ForegroundGpsPreviewStatus.CURRENT, harness.latest.status)
        assertFalse(harness.latest.hasFreshLiveFix)
        harness.source.callbacks.single().sample(sample(2_000))
        assertTrue(harness.latest.hasFreshLiveFix)
        assertTrue(harness.latest.toMapEvidence().sections.isEmpty())
    }

    @Test
    fun `readiness expires after five source seconds before the map becomes stale`() {
        val evidence = ForegroundGpsPreviewEvidence()
        evidence.observe(sample(1_000), 1_000)
        assertTrue(evidence.state(6_000).hasFreshLiveFix)
        assertFalse(evidence.state(6_001).hasFreshLiveFix)
        assertEquals(ForegroundGpsPreviewStatus.CURRENT, evidence.state(6_001).status)
        assertEquals(ForegroundGpsPreviewStatus.STALE, evidence.state(16_001).status)
        evidence.observe(sample(6_001), 6_001)
        assertTrue(evidence.state(6_001).hasFreshLiveFix)
        evidence.clear()
        assertFalse(evidence.state(6_001).hasFreshLiveFix)
    }

    @Test
    fun `duplicate older and delayed callbacks cannot refresh readiness`() {
        val evidence = ForegroundGpsPreviewEvidence()
        evidence.observe(sample(1_000), 1_000)
        evidence.observe(sample(1_000), 6_001)
        evidence.observe(sample(999), 6_001)
        assertFalse(evidence.state(6_001).hasFreshLiveFix)
        evidence.observe(sample(1_001), 6_002)
        assertFalse(evidence.state(6_002).hasFreshLiveFix)
        evidence.observe(sample(6_003), 6_003)
        assertTrue(evidence.state(6_003).hasFreshLiveFix)
    }

    @Test
    fun `mock cached and live callbacks cannot prepare a start or poison a later real fix`() {
        val harness = Harness()
        harness.source.cached = sample(1_000)
        harness.source.cachedIsMock = true
        harness.session.start()
        assertFalse(harness.latest.hasFreshLiveFix)
        assertNull(harness.latest.marker)
        harness.source.callbacks.single().mockSample(sample(2_000))
        assertFalse(harness.latest.hasFreshLiveFix)
        assertNull(harness.latest.marker)
        harness.source.callbacks.single().sample(sample(2_000))
        assertTrue(harness.latest.hasFreshLiveFix)
        harness.now = 3_000
        harness.source.callbacks.single().mockSample(sample(3_000))
        assertFalse(harness.latest.hasFreshLiveFix)
        assertEquals(ForegroundGpsPreviewStatus.WEAK, harness.latest.status)
        assertEquals(GpsMapMarkerState.LAST_KNOWN, harness.latest.marker?.state)
    }

    @Test
    fun `an older mock cache cannot replace a newer synchronous real live fix`() {
        val harness = Harness()
        harness.source.synchronousSample = sample(2_000)
        harness.source.cached = sample(1_000)
        harness.source.cachedIsMock = true
        harness.session.start()
        assertTrue(harness.latest.hasFreshLiveFix)
        assertEquals(ForegroundGpsPreviewStatus.CURRENT, harness.latest.status)
    }

    @Test
    fun `signal status rechecks source freshness and current permission or provider`() {
        for (unavailable in listOf(GpsPreviewAvailability.PERMISSION_NEEDED, GpsPreviewAvailability.LOCATION_OFF)) {
            var now = 2_000L
            val source = FakeSource()
            val controller = ForegroundGpsPreviewController()
            controller.startSession(source) { now }!!
            source.callbacks.single().sample(sample(2_000))
            assertTrue(controller.hasFreshLiveFixNow())
            now = 7_001L
            assertFalse(controller.hasFreshLiveFixNow())
            source.callbacks.single().sample(sample(7_001))
            assertTrue(controller.hasFreshLiveFixNow())
            source.available = unavailable
            assertFalse(controller.hasFreshLiveFixNow())
            assertEquals(0, source.activeListeners)
            assertNull(controller.value.marker)
            assertEquals(1, source.registrations)
            source.callbacks.single().sample(sample(7_002))
            assertFalse(controller.value.hasFreshLiveFix)
        }
    }

    @Test
    fun `background restart requires a new live callback and handoff erases readiness`() {
        val source = FakeSource()
        val controller = ForegroundGpsPreviewController()
        val first = controller.startSession(source) { 2_000L }!!
        val old = source.callbacks.single()
        old.sample(sample(2_000))
        assertTrue(controller.hasFreshLiveFixNow())
        controller.stopSession(first)
        assertFalse(controller.hasFreshLiveFixNow())
        source.cached = sample(2_000)
        controller.startSession(source) { 3_000L }!!
        old.sample(sample(3_000))
        assertFalse(controller.hasFreshLiveFixNow())
        source.callbacks.last().sample(sample(3_000))
        assertTrue(controller.hasFreshLiveFixNow())
        controller.stopBeforeRun()
        assertFalse(controller.hasFreshLiveFixNow())
        source.callbacks.last().sample(sample(3_001))
        assertFalse(controller.value.hasFreshLiveFix)
        assertEquals(0, source.activeListeners)
    }

    @Test
    fun `a delayed live callback cannot initialize a current or invented position`() {
        val evidence = ForegroundGpsPreviewEvidence()
        evidence.observe(sample(1_000), 16_001)
        assertEquals(ForegroundGpsPreviewStatus.STALE, evidence.state(16_001).status)
        assertNull(evidence.state(16_001).marker)
    }

    @Test
    fun `accurate last known cache can anchor the map for thirty minutes but is never current`() {
        val evidence = ForegroundGpsPreviewEvidence()
        evidence.observe(sample(1_000), 16_001, allowStaleLastKnown = true)
        assertEquals(ForegroundGpsPreviewStatus.STALE, evidence.state(16_001).status)
        assertEquals(GpsMapMarkerState.LAST_KNOWN, evidence.state(16_001).marker?.state)
        val finalAllowed = 1_000 + ForegroundGpsPreviewEvidence.MAX_LAST_KNOWN_AGE_MILLIS
        assertEquals(GpsMapMarkerState.LAST_KNOWN, evidence.state(finalAllowed).marker?.state)
        assertNull(evidence.state(finalAllowed + 1).marker)
    }

    @Test
    fun `old future inaccurate and malformed caches cannot anchor the preview`() {
        val cases = listOf(
            sample(1_000) to (1_001 + ForegroundGpsPreviewEvidence.MAX_LAST_KNOWN_AGE_MILLIS),
            sample(2_001) to 2_000L,
            sample(1_000).copy(horizontalAccuracyMeters = 50.0) to 20_000L,
            sample(1_000).copy(latitudeDegrees = 100.0) to 20_000L
        )
        cases.forEach { (sample, now) ->
            val evidence = ForegroundGpsPreviewEvidence()
            evidence.observe(sample, now, allowStaleLastKnown = true)
            assertNull(evidence.state(now).marker)
            assertTrue(evidence.state(now).status != ForegroundGpsPreviewStatus.CURRENT)
        }
    }

    @Test
    fun `absent fix starts acquiring without coordinates or a route`() {
        val state = ForegroundGpsPreviewEvidence().state(1_000)
        assertEquals(ForegroundGpsPreviewStatus.ACQUIRING, state.status)
        assertNull(state.marker)
        assertTrue(state.toMapEvidence().sections.isEmpty())
    }

    @Test
    fun `repeated and older delivery cannot refresh a fix or move the marker`() {
        val evidence = ForegroundGpsPreviewEvidence()
        evidence.observe(sample(2_000), 2_000)
        evidence.observe(sample(2_000).copy(latitudeDegrees = 60.0), 10_000)
        evidence.observe(sample(1_000).copy(latitudeDegrees = 70.0), 12_000)
        assertEquals(sample(2_000).latitudeDegrees, evidence.state(12_000).marker!!.coordinate.latitude, 0.0)
        assertEquals(ForegroundGpsPreviewStatus.STALE, evidence.state(17_001).status)
    }

    @Test
    fun `bad accuracy coordinates and missing wall time never become current`() {
        val cases = listOf(
            sample(1_000).copy(horizontalAccuracyMeters = null),
            sample(1_000).copy(horizontalAccuracyMeters = 0.0),
            sample(1_000).copy(horizontalAccuracyMeters = 25.01),
            sample(1_000).copy(horizontalAccuracyMeters = Double.NaN),
            sample(1_000).copy(latitudeDegrees = 90.1),
            sample(1_000).copy(longitudeDegrees = Double.POSITIVE_INFINITY),
            sample(1_000).copy(observedEpochMillis = 0)
        )
        cases.forEach { candidate ->
            val evidence = ForegroundGpsPreviewEvidence()
            evidence.observe(candidate, 2_000)
            assertEquals(ForegroundGpsPreviewStatus.WEAK, evidence.state(2_000).status)
            assertNull(evidence.state(2_000).marker)
            assertFalse(evidence.state(2_000).hasFreshLiveFix)
        }
    }

    @Test
    fun `future and negative source clocks do not poison a later valid sample`() {
        for (offset in listOf(-1L, 50_000L)) {
            val evidence = ForegroundGpsPreviewEvidence()
            evidence.observe(sample(offset), 2_000)
            assertEquals(ForegroundGpsPreviewStatus.WEAK, evidence.state(2_000).status)
            assertFalse(evidence.state(2_000).hasFreshLiveFix)
            evidence.observe(sample(3_000), 3_000)
            assertEquals(ForegroundGpsPreviewStatus.CURRENT, evidence.state(3_000).status)
            assertTrue(evidence.state(3_000).hasFreshLiveFix)
        }
    }

    @Test
    fun `inaccurate latest sample keeps an earlier position explicitly last known`() {
        val evidence = ForegroundGpsPreviewEvidence()
        evidence.observe(sample(1_000), 1_000)
        evidence.observe(sample(2_000).copy(latitudeDegrees = 60.0, horizontalAccuracyMeters = 100.0), 2_000)
        val state = evidence.state(3_000)
        assertEquals(ForegroundGpsPreviewStatus.WEAK, state.status)
        assertEquals(GpsMapMarkerState.LAST_KNOWN, state.marker?.state)
        assertEquals(sample(1_000).latitudeDegrees, state.marker!!.coordinate.latitude, 0.0)
        evidence.observe(sample(4_000), 4_000)
        assertEquals(ForegroundGpsPreviewStatus.CURRENT, evidence.state(4_000).status)
    }

    @Test
    fun `accurate stationary updates need no movement or measured segment`() {
        val evidence = ForegroundGpsPreviewEvidence()
        evidence.observe(sample(1_000), 1_000)
        evidence.observe(sample(15_000), 15_000)
        val state = evidence.state(20_000)
        assertEquals(ForegroundGpsPreviewStatus.CURRENT, state.status)
        assertTrue(state.hasFreshLiveFix)
        assertTrue(state.toMapEvidence().sections.isEmpty())
        assertFalse(state.explanation.contains("recorded", ignoreCase = true))
        assertTrue(state.explanation.contains("not started"))
    }

    @Test
    fun `clock reset and explicit clear cannot retain a current claim`() {
        val evidence = ForegroundGpsPreviewEvidence()
        evidence.observe(sample(5_000), 5_000)
        assertNull(evidence.state(1_000).marker)
        evidence.clear()
        assertNull(evidence.state(5_000).marker)
        assertEquals(ForegroundGpsPreviewStatus.ACQUIRING, evidence.state(5_000).status)
    }

    @Test
    fun `start tick and duplicate start own exactly one listener until stop`() {
        val harness = Harness()
        harness.session.tick()
        assertEquals(0, harness.source.registrations)
        harness.session.start()
        harness.session.tick()
        harness.session.start()
        assertEquals(1, harness.source.registrations)
        assertEquals(1, harness.source.activeListeners)
        harness.session.stop()
        harness.session.stop()
        harness.session.tick()
        assertEquals(1, harness.source.removals)
        assertEquals(0, harness.source.activeListeners)
        assertEquals(ForegroundGpsPreviewStatus.DISABLED, harness.latest.status)
    }

    @Test
    fun `permission denial or provider off never registers or reads cached locations`() {
        for (unavailable in listOf(GpsPreviewAvailability.PERMISSION_NEEDED, GpsPreviewAvailability.LOCATION_OFF)) {
            val harness = Harness()
            harness.source.available = unavailable
            harness.session.start()
            assertEquals(0, harness.source.registrations)
            assertEquals(0, harness.source.cacheReads)
            assertNull(harness.latest.marker)
            assertEquals(if (unavailable == GpsPreviewAvailability.PERMISSION_NEEDED) {
                ForegroundGpsPreviewStatus.PERMISSION_NEEDED
            } else ForegroundGpsPreviewStatus.LOCATION_OFF, harness.latest.status)
        }
    }

    @Test
    fun `permission grant while foreground starts without a new run or component restart`() {
        val harness = Harness()
        harness.source.available = GpsPreviewAvailability.PERMISSION_NEEDED
        harness.session.start()
        harness.source.available = GpsPreviewAvailability.READY
        harness.source.cached = sample(1_000)
        harness.session.tick()
        assertEquals(1, harness.source.registrations)
        assertEquals(ForegroundGpsPreviewStatus.CURRENT, harness.latest.status)
    }

    @Test
    fun `session uses bounded older cache as last known while waiting for current gps`() {
        val harness = Harness()
        harness.now = 20_000
        harness.source.cached = sample(1_000)
        harness.session.start()
        assertEquals(ForegroundGpsPreviewStatus.STALE, harness.latest.status)
        assertEquals(GpsMapMarkerState.LAST_KNOWN, harness.latest.marker?.state)
        harness.source.callbacks.last().sample(sample(20_000))
        assertEquals(ForegroundGpsPreviewStatus.CURRENT, harness.latest.status)
    }

    @Test
    fun `revocation clears in memory position and late callbacks cannot restore it`() {
        val harness = Harness()
        harness.session.start()
        val old = harness.source.callbacks.single()
        old.sample(sample(1_000))
        assertEquals(ForegroundGpsPreviewStatus.CURRENT, harness.latest.status)
        harness.source.available = GpsPreviewAvailability.PERMISSION_NEEDED
        harness.session.tick()
        assertEquals(1, harness.source.removals)
        assertNull(harness.latest.marker)
        old.sample(sample(2_000))
        assertEquals(ForegroundGpsPreviewStatus.PERMISSION_NEEDED, harness.latest.status)
        assertNull(harness.latest.marker)
    }

    @Test
    fun `provider disable removes listener and enable reacquires without using old callbacks`() {
        val harness = Harness()
        harness.session.start()
        val old = harness.source.callbacks.single()
        old.disabled()
        assertEquals(1, harness.source.removals)
        assertEquals(ForegroundGpsPreviewStatus.LOCATION_OFF, harness.latest.status)
        harness.session.tick()
        assertEquals(2, harness.source.registrations)
        old.sample(sample(1_000))
        assertNull(harness.latest.marker)
        harness.source.callbacks.last().sample(sample(1_000))
        assertEquals(ForegroundGpsPreviewStatus.CURRENT, harness.latest.status)
    }

    @Test
    fun `background stop and return use a new lease with no preview history`() {
        val harness = Harness()
        harness.session.start()
        val old = harness.source.callbacks.single()
        old.sample(sample(1_000))
        harness.session.stop()
        assertNull(harness.latest.marker)
        harness.session.start()
        old.sample(sample(1_000))
        assertEquals(ForegroundGpsPreviewStatus.ACQUIRING, harness.latest.status)
        assertEquals(2, harness.source.registrations)
        assertEquals(1, harness.source.activeListeners)
    }

    @Test
    fun `cached source cannot replace newer synchronous registration callback`() {
        val harness = Harness()
        harness.now = 3_000
        harness.source.synchronousSample = sample(3_000).copy(latitudeDegrees = 40.0)
        harness.source.cached = sample(1_000)
        harness.session.start()
        assertEquals(40.0, harness.latest.marker!!.coordinate.latitude, 0.0)
    }

    @Test
    fun `synchronous provider disable closes listener returned after the callback`() {
        val harness = Harness()
        harness.source.disableDuringRegistration = true
        harness.session.start()
        assertEquals(1, harness.source.removals)
        assertEquals(0, harness.source.activeListeners)
        assertEquals(ForegroundGpsPreviewStatus.LOCATION_OFF, harness.latest.status)
    }

    @Test
    fun `cache permission race removes registration and never publishes its location`() {
        val harness = Harness()
        harness.source.cacheFailure = SecurityException("Test access revoked")
        harness.session.start()
        assertEquals(1, harness.source.removals)
        assertEquals(0, harness.source.activeListeners)
        assertEquals(ForegroundGpsPreviewStatus.PERMISSION_NEEDED, harness.latest.status)
        assertNull(harness.latest.marker)
    }

    @Test
    fun `ordinary platform failures stay local and expired current data still clears`() {
        val harness = Harness()
        harness.source.cacheFailure = IllegalStateException("Test provider failure")
        harness.session.start()
        assertEquals(1, harness.source.removals)
        assertEquals(ForegroundGpsPreviewStatus.UNAVAILABLE, harness.latest.status)
        assertTrue(harness.latest.toMapEvidence().evidenceUnavailable)
        harness.source.cacheFailure = null
        harness.session.tick()
        harness.source.callbacks.last().sample(sample(1_000))
        harness.now = 16_001
        harness.session.tick()
        assertEquals(ForegroundGpsPreviewStatus.STALE, harness.latest.status)
    }

    @Test
    fun `service handoff synchronously removes preview and prevents further publication`() {
        val source = FakeSource().apply { cached = sample(1_000) }
        val controller = ForegroundGpsPreviewController()
        val session = controller.startSession(source) { 2_000 }!!
        val callback = source.callbacks.single()
        assertEquals(ForegroundGpsPreviewStatus.CURRENT, controller.value.status)

        controller.stopBeforeRun()
        assertEquals(0, source.activeListeners)
        assertEquals(1, source.removals)
        assertEquals(ForegroundGpsPreviewStatus.DISABLED, controller.value.status)
        assertNull(controller.value.marker)

        callback.sample(sample(2_000))
        callback.disabled()
        session.tick()
        // A lifecycle return during a slow service start cannot reopen the temporary listener.
        assertNull(controller.startSession(source) { 2_000 })
        assertEquals(ForegroundGpsPreviewStatus.DISABLED, controller.value.status)
        assertNull(controller.value.marker)
        assertEquals(1, source.registrations)
    }

    @Test
    fun `older lifecycle cleanup cannot clear a new preview after cancelled start`() {
        val source = FakeSource().apply { cached = sample(1_000) }
        val controller = ForegroundGpsPreviewController()
        val oldSession = controller.startSession(source) { 2_000 }!!
        controller.stopBeforeRun()

        source.cached = sample(3_000).copy(latitudeDegrees = 40.0)
        controller.beginPreparation() // A new preparation state/effect, not a lifecycle return.
        val newSession = controller.startSession(source) { 4_000 }!!
        controller.stopSession(oldSession)
        source.callbacks.first().sample(sample(4_000).copy(latitudeDegrees = 60.0))
        assertEquals(ForegroundGpsPreviewStatus.CURRENT, controller.value.status)
        assertEquals(40.0, controller.value.marker!!.coordinate.latitude, 0.0)
        assertEquals(1, source.activeListeners)
        assertEquals(1, source.removals)

        controller.stopSession(newSession)
        assertEquals(0, source.activeListeners)
        assertEquals(2, source.removals)
        assertNull(controller.value.marker)
    }

    private class Harness {
        var now = 2_000L
        val source = FakeSource()
        var latest = ForegroundGpsPreviewState(ForegroundGpsPreviewStatus.DISABLED)
        val session = ForegroundGpsPreviewSession(source, { now }) { latest = it }
    }

    private class FakeSource : ForegroundGpsPreviewSource {
        data class Callbacks(
            val sample: (GpsSample) -> Unit,
            val disabled: () -> Unit,
            val mockSample: (GpsSample) -> Unit
        )
        var available = GpsPreviewAvailability.READY
        var cached: GpsSample? = null
        var cachedIsMock = false
        var synchronousSample: GpsSample? = null
        var disableDuringRegistration = false
        var cacheFailure: RuntimeException? = null
        var registrations = 0
        var removals = 0
        var cacheReads = 0
        var activeListeners = 0
        val callbacks = mutableListOf<Callbacks>()

        override fun availability() = available
        override fun lastKnownSample(): ForegroundGpsPreviewFix? {
            cacheReads++
            cacheFailure?.let { throw it }
            return cached?.let { ForegroundGpsPreviewFix(it, cachedIsMock) }
        }
        override fun listen(onSample: (ForegroundGpsPreviewFix) -> Unit, onProviderDisabled: () -> Unit): () -> Unit {
            registrations++
            activeListeners++
            callbacks.add(Callbacks({ onSample(ForegroundGpsPreviewFix(it)) }, onProviderDisabled,
                { onSample(ForegroundGpsPreviewFix(it, isMock = true)) }))
            synchronousSample?.let { onSample(ForegroundGpsPreviewFix(it)) }
            if (disableDuringRegistration) onProviderDisabled()
            var closed = false
            return {
                check(!closed) { "Listener removed twice." }
                closed = true
                removals++
                activeListeners--
            }
        }
    }

    companion object {
        private fun sample(elapsedMillis: Long) = GpsSample(
            latitudeDegrees = 29.76, longitudeDegrees = -95.36, horizontalAccuracyMeters = 5.0,
            observedEpochMillis = 1_800_000_000_000L + elapsedMillis,
            elapsedRealtimeMillis = elapsedMillis)
    }
}
