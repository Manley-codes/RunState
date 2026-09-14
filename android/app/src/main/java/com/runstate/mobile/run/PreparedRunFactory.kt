package com.runstate.mobile.run

import com.runstate.mobile.data.local.RunEntity
import com.runstate.mobile.data.local.StoredRunState
import java.time.Clock
import java.time.ZoneId
import java.util.UUID

/**
 * Builds the one row that represents a run becoming official.
 *
 * Everything that makes a run *this* run — its permanent UUID, the instant it began and the
 * zone it began in — is decided here, once, and nowhere else. [RunSessionCoordinator]
 * receives a factory rather than reaching for `UUID.randomUUID()` or the system clock
 * itself, for two reasons:
 *
 * - **Retry identity.** The coordinator asks for a prepared row once and keeps it. A failed
 *   insert is retried with that exact row, so a retry re-attempts the same run instead of
 *   quietly minting a second UUID and a later start time for it.
 * - **Testability.** All three sources are injected, so a JVM test can fix the clock, the
 *   zone and the identity and assert the row exactly, rather than asserting that a value
 *   "looks like" a UUID or "is roughly now".
 *
 * The row contains only what is genuinely known at the official start. No distance, no
 * pause history and no finish — nothing has happened yet, and inventing a value here would
 * be a claim storage would then preserve forever.
 *
 * @property clock the source of the official start instant. Only its instant is read; its
 *   zone is ignored, because the run's zone is a separate fact supplied below.
 * @property zoneIdSupplier the zone the phone is in at the moment of creation. A supplier
 *   rather than a captured value, so a phone that changed zone since the app launched
 *   records where the run actually began.
 * @property uuidSupplier the source of the run's permanent identity.
 */
internal class PreparedRunFactory(
    private val clock: Clock,
    private val zoneIdSupplier: () -> ZoneId,
    private val uuidSupplier: () -> UUID
) {

    /**
     * Creates the version-2 RUNNING row for a run starting now.
     *
     * The clock is read once and that single value is used for both the official start and
     * the last checkpoint. Reading it twice could produce two different milliseconds, and
     * [RunSessionStarter] refuses any initial row whose checkpoint differs from its start.
     */
    fun create(): RunEntity {
        val officialStart = clock.millis()

        return RunEntity(

            // `UUID.toString()` is always canonical lowercase text, which is exactly the
            // spelling RunEntity requires for its primary key.
            runId = uuidSupplier().toString(),
            state = StoredRunState.RUNNING,
            officialStartEpochMillis = officialStart,
            startTimezoneId = zoneIdSupplier().id,

            // Nothing past the start has been confirmed durable yet.
            lastCheckpointEpochMillis = officialStart,
            finishEpochMillis = null
        )
    }
}
