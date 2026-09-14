package com.runstate.mobile.run

import com.runstate.mobile.data.local.RunEntity
import com.runstate.mobile.data.local.StoredRunState
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Checks that the official-start row is built from exactly the injected sources.
 *
 * Every source is fixed, so the assertions compare whole rows rather than checking that a
 * value merely looks like a UUID or is roughly the current time.
 */
class PreparedRunFactoryTest {

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
                finishEpochMillis = null
            ),
            row
        )
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
}
