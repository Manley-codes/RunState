package com.runstate.mobile.run

import com.runstate.mobile.data.local.RunEntity
import com.runstate.mobile.data.local.FakeRunDao
import com.runstate.mobile.data.local.PreRunEnergy
import com.runstate.mobile.data.local.StoredRunState
import com.runstate.mobile.data.local.RunTrackingMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Checks that the official-start row is built from exactly the injected sources.
 *
 * Every source is fixed, so the assertions compare whole rows rather than checking that a
 * value merely looks like a UUID or is roughly the current time.
 */
class PreparedRunFactoryTest {

    @Test
    fun `readTime and create capture each source once and freeze prepared timing through retry`() {
        var wallReads = 0
        var markReads = 0
        var now = OFFICIAL_START
        var elapsed = 10_000L
        val timingSession = "03689ffc-65d9-4907-ae77-7a17b3ad77a6"
        val factory = PreparedRunFactory(
            clock = object : Clock() {
                override fun getZone(): ZoneId = ZoneOffset.UTC
                override fun withZone(zone: ZoneId): Clock = this
                override fun instant(): Instant = Instant.ofEpochMilli(now)
                override fun millis(): Long { wallReads++; return now }
            },
            zoneIdSupplier = { ZoneId.of("America/Chicago") },
            uuidSupplier = { UUID.fromString(RUN_ID) },
            timingMarkSupplier = { markReads++; RunTimingMark(elapsed, timingSession) }
        )
        val reading = factory.readTime()
        assertEquals(RunTimeReading(now, RunTimingMark(elapsed, timingSession)), reading)
        assertEquals(1, wallReads)
        assertEquals(1, markReads)
        val prepared = factory.create(trackingMode = RunTrackingMode.GPS)
        assertEquals(2, wallReads)
        assertEquals(2, markReads)
        assertEquals(10_000L, prepared.startElapsedRealtimeMillis)
        assertEquals(timingSession, prepared.timingSessionId)

        val machine = RunSessionStateMachine().apply { beginCountdown() }
        val dao = FakeRunDao().apply { failWith = IllegalStateException("disk unavailable") }
        val starter = RunSessionStarter(machine, dao)
        assertThrows(IllegalStateException::class.java) { runBlocking { starter.start(prepared) } }
        now += 5_000L
        elapsed += 5_000L
        dao.failWith = null
        runBlocking { starter.start(prepared) }
        assertEquals(prepared, runBlocking { dao.findById(RUN_ID) })
        assertEquals(OFFICIAL_START, prepared.officialStartEpochMillis)
        assertEquals(10_000L, prepared.startElapsedRealtimeMillis)
        assertEquals(2, wallReads)
        assertEquals(2, markReads)
    }

    private companion object {
        const val OFFICIAL_START = 1_756_000_000_000L
        const val RUN_ID = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
    }

    /**
     * Proves the row is the canonical RUNNING start built from the fixed clock, zone and UUID.
     *
     * The clock deliberately carries a different zone from the supplier. Only the supplier's
     * zone may reach the row; a factory that read the clock's zone instead would fail here.
     */
    @Test
    fun `create builds the running start row from the injected sources`() {

        // Arrange: a clock in UTC, a runner in Chicago, and a fixed identity.
        val factory = PreparedRunFactory(
            clock = Clock.fixed(Instant.ofEpochMilli(OFFICIAL_START), ZoneOffset.UTC),
            zoneIdSupplier = { ZoneId.of("America/Chicago") },
            uuidSupplier = { UUID.fromString(RUN_ID) }
        )

        // Act
        val row = factory.create()

        // Assert: the whole row, including the null finish and the checkpoint at the start.
        assertEquals(
            RunEntity(
                runId = RUN_ID,
                state = StoredRunState.RUNNING,
                officialStartEpochMillis = OFFICIAL_START,
                startTimezoneId = "America/Chicago",
                lastCheckpointEpochMillis = OFFICIAL_START,
                finishEpochMillis = null,
                transitionHistoryComplete = true,
                trackingMode = RunTrackingMode.FIXTURE
            ),
            row
        )
    }

    @Test
    fun `create preserves an explicit pre run energy choice`() {
        val factory = PreparedRunFactory(
            clock = Clock.fixed(Instant.ofEpochMilli(OFFICIAL_START), ZoneOffset.UTC),
            zoneIdSupplier = { ZoneId.of("America/Chicago") },
            uuidSupplier = { UUID.fromString(RUN_ID) }
        )

        assertEquals(PreRunEnergy.HIGH, factory.create(PreRunEnergy.HIGH).preRunEnergy)
        assertEquals(null, factory.create().preRunEnergy)
    }

    @Test
    fun `create fixes the requested tracking mode with the first row`() {
        val factory = PreparedRunFactory(
            clock = Clock.fixed(Instant.ofEpochMilli(OFFICIAL_START), ZoneOffset.UTC),
            zoneIdSupplier = { ZoneId.of("America/Chicago") },
            uuidSupplier = { UUID.fromString(RUN_ID) }
        )

        assertEquals(RunTrackingMode.FIXTURE, factory.create().trackingMode)
        assertEquals(
            RunTrackingMode.GPS,
            factory.create(trackingMode = RunTrackingMode.GPS).trackingMode
        )
    }

    @Test
    fun `music observation stays off unless explicitly selected for this run`() {
        val factory = PreparedRunFactory(
            clock = Clock.fixed(Instant.ofEpochMilli(OFFICIAL_START), ZoneOffset.UTC),
            zoneIdSupplier = { ZoneId.of("America/Chicago") },
            uuidSupplier = { UUID.fromString(RUN_ID) }
        )

        assertEquals(false, factory.create().musicObservationRequested)
        assertEquals(
            true,
            factory.create(musicObservationRequested = true).musicObservationRequested
        )
    }

    @Test
    fun `music sharing requires its own consent and an observation opt in`() {
        val factory = PreparedRunFactory(
            clock = Clock.fixed(Instant.ofEpochMilli(OFFICIAL_START), ZoneOffset.UTC),
            zoneIdSupplier = { ZoneId.of("America/Chicago") },
            uuidSupplier = { UUID.fromString(RUN_ID) }
        )
        assertEquals(false, factory.create().musicReflectionSharingAllowed)
        assertEquals(false, factory.create(musicObservationRequested = true).musicReflectionSharingAllowed)
        assertEquals(true, factory.create(
            musicObservationRequested = true, musicReflectionSharingAllowed = true
        ).musicReflectionSharingAllowed)
        assertThrows(IllegalArgumentException::class.java) {
            factory.create(musicReflectionSharingAllowed = true)
        }
    }

    /**
     * Proves an uppercase-looking identity is still stored as canonical lowercase text.
     *
     * `UUID.fromString` accepts uppercase input; the row must not echo that spelling back,
     * because the primary key is compared as TEXT.
     */
    @Test
    fun `create stores the identity as canonical lowercase text`() {

        // Arrange
        val factory = PreparedRunFactory(
            clock = Clock.fixed(Instant.ofEpochMilli(OFFICIAL_START), ZoneOffset.UTC),
            zoneIdSupplier = { ZoneId.of("America/Chicago") },
            uuidSupplier = { UUID.fromString(RUN_ID.uppercase()) }
        )

        // Act and Assert
        assertEquals(RUN_ID, factory.create().runId)
    }

    /**
     * Proves each call reads the sources again rather than replaying a captured row.
     *
     * Retry identity is the coordinator's job — it keeps the row it was given. The factory
     * itself must produce a new run each time it is asked, with the zone the phone is in at
     * that moment.
     */
    @Test
    fun `each create reads the clock zone and identity at that moment`() {

        // Arrange: sources that change between calls.
        val ids = ArrayDeque(
            listOf(
                UUID.fromString(RUN_ID),
                UUID.fromString("c4e1b8a2-7d35-4f61-8b0c-2a9e6d4f13b7")
            )
        )
        var zone = ZoneId.of("America/Chicago")
        var now = OFFICIAL_START
        val factory = PreparedRunFactory(
            clock = object : Clock() {
                override fun getZone(): ZoneId = ZoneOffset.UTC
                override fun withZone(zone: ZoneId): Clock = this
                override fun instant(): Instant = Instant.ofEpochMilli(now)
            },
            zoneIdSupplier = { zone },
            uuidSupplier = { ids.removeFirst() }
        )

        // Act: one run, then the phone travels and time passes, then another.
        val first = factory.create()
        zone = ZoneId.of("America/New_York")
        now = OFFICIAL_START + 600_000L
        val second = factory.create()

        // Assert: the second row reflects the new moment, zone and identity.
        assertNotEquals(first.runId, second.runId)
        assertEquals("America/New_York", second.startTimezoneId)
        assertEquals(OFFICIAL_START + 600_000L, second.officialStartEpochMillis)
        assertEquals(second.officialStartEpochMillis, second.lastCheckpointEpochMillis)

        // Assert: the first row was not disturbed.
        assertEquals("America/Chicago", first.startTimezoneId)
        assertEquals(OFFICIAL_START, first.officialStartEpochMillis)
    }

    /**
     * Proves lifecycle time is read live from the very clock the start row uses.
     *
     * The clock moves between reads, so a factory that cached its first reading, or read a
     * different clock, would return the wrong value.
     */
    @Test
    fun `now reads the injected clock at that moment`() {

        // Arrange
        var now = OFFICIAL_START
        val factory = PreparedRunFactory(
            clock = object : Clock() {
                override fun getZone(): ZoneId = ZoneOffset.UTC
                override fun withZone(zone: ZoneId): Clock = this
                override fun instant(): Instant = Instant.ofEpochMilli(now)
            },
            zoneIdSupplier = { ZoneId.of("America/Chicago") },
            uuidSupplier = { UUID.fromString(RUN_ID) }
        )

        // Act and Assert: the start row and the first reading agree.
        assertEquals(factory.create().officialStartEpochMillis, factory.nowEpochMillis())

        // Act and Assert: a later reading follows the clock.
        now = OFFICIAL_START + 60_000L
        assertEquals(OFFICIAL_START + 60_000L, factory.nowEpochMillis())
    }
}
