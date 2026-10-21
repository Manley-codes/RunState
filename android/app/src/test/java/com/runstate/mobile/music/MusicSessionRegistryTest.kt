package com.runstate.mobile.music

import com.runstate.mobile.data.local.MusicObservationKind
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicSessionRegistryTest {
    private class Controller(private val key: String, private val ownApp: Boolean = false) : MusicSessionHandle {
        var identityFailure: RuntimeException? = null
        var registrationFailure: RuntimeException? = null
        var removalFailure: RuntimeException? = null
        var snapshotFailure: RuntimeException? = null
        var registrations = 0
        var removals = 0
        val callbacks = mutableListOf<(MusicObservationKind) -> Unit>()
        val endedCallbacks = mutableListOf<() -> Unit>()

        override val token: Any get() {
            identityFailure?.let { throw it }
            return key
        }
        override val isOwnApp: Boolean get() = ownApp

        override fun registerCallback(onEvent: (MusicObservationKind) -> Unit, onSessionEnded: () -> Unit) {
            registrations++
            callbacks += onEvent
            endedCallbacks += onSessionEnded
            // Model registration that installs a callback and then reports failure.
            registrationFailure?.let { throw it }
        }

        override fun unregisterCallback() {
            removals++
            removalFailure?.let { throw it }
        }

        fun emit(kind: MusicObservationKind = MusicObservationKind.METADATA_CHANGED) {
            callbacks.last()(kind)
        }
    }

    private data class Observation(val runId: String, val controller: Controller, val kind: MusicObservationKind)

    private class Fixture {
        val observations = mutableListOf<Observation>()
        var controllerFailures = 0
        var accessLosses = 0
        var continuityLosses = 0
        var afterObservation: ((Observation) -> Unit)? = null
        val registry = MusicSessionRegistry<Controller>(
            onObservation = { runId, controller, kind ->
                controller.snapshotFailure?.let { throw it }
                val observation = Observation(runId, controller, kind)
                observations += observation
                afterObservation?.invoke(observation)
            },
            onControllerFailure = { controllerFailures++ },
            onAccessLost = { accessLosses++ },
            onContinuityLost = { continuityLosses++ }
        )
    }

    @Test fun noOwnerOrOwnAppNeverStartsObservation() {
        val fixture = Fixture()
        val other = Controller("other")
        fixture.registry.reconcile(listOf(other))
        assertEquals(0, other.registrations)

        val own = Controller("runstate", ownApp = true)
        fixture.registry.start("run-a")
        fixture.registry.reconcile(listOf(own, other))
        assertEquals(0, own.registrations)
        assertEquals(listOf(Observation("run-a", other, MusicObservationKind.SESSION_SNAPSHOT)), fixture.observations)
    }

    @Test fun unreadableIdentityAndFailedRegistrationLeaveHealthyPlayerObserved() {
        val fixture = Fixture()
        val unreadable = Controller("unreadable").apply { identityFailure = IllegalStateException("dead") }
        val failed = Controller("failed").apply { registrationFailure = IllegalStateException("dead") }
        val healthy = Controller("spotify")
        fixture.registry.start("run-a")
        fixture.registry.reconcile(listOf(unreadable, failed, healthy))
        failed.emit()
        healthy.emit(MusicObservationKind.PLAYBACK_CHANGED)

        assertEquals(2, fixture.controllerFailures)
        assertEquals(0, fixture.accessLosses)
        assertEquals(1, failed.removals)
        assertEquals(listOf(
            Observation("run-a", healthy, MusicObservationKind.SESSION_SNAPSHOT),
            Observation("run-a", healthy, MusicObservationKind.PLAYBACK_CHANGED)
        ), fixture.observations)
    }

    @Test fun failedRegistrationCanRecoverOnALaterSessionListWithoutDuplicatingHealthyWatch() {
        val fixture = Fixture()
        val recovering = Controller("recovering").apply { registrationFailure = IllegalStateException("dead") }
        val healthy = Controller("spotify")
        fixture.registry.start("run-a")
        fixture.registry.reconcile(listOf(recovering, healthy))
        val stale = recovering.callbacks.single()
        recovering.registrationFailure = null
        fixture.registry.reconcile(listOf(recovering, healthy))
        stale(MusicObservationKind.METADATA_CHANGED)
        recovering.emit()
        healthy.emit()

        assertEquals(1, healthy.registrations)
        assertEquals(2, recovering.registrations)
        assertEquals(listOf(healthy, recovering, recovering, healthy), fixture.observations.map { it.controller })
    }

    @Test fun unreadableSnapshotLeavesOtherPlayersAndFutureReadableCallbacksAvailable() {
        val fixture = Fixture()
        val recovering = Controller("recovering").apply { snapshotFailure = IllegalStateException("unreadable") }
        val healthy = Controller("spotify")
        fixture.registry.start("run-a")
        fixture.registry.reconcile(listOf(recovering, healthy))
        recovering.emit()
        healthy.emit()
        recovering.snapshotFailure = null
        recovering.emit()

        assertEquals(2, fixture.controllerFailures)
        assertEquals(0, recovering.removals)
        assertEquals(listOf(healthy, healthy, recovering), fixture.observations.map { it.controller })
    }

    @Test fun removalFailureCannotKeepOldCallbacksAliveOrStopTheRemainingPlayer() {
        val fixture = Fixture()
        val removed = Controller("removed").apply { removalFailure = IllegalStateException("dead") }
        val healthy = Controller("spotify")
        fixture.registry.start("run-a")
        fixture.registry.reconcile(listOf(removed, healthy))
        fixture.observations.clear()
        fixture.registry.reconcile(listOf(healthy))
        removed.emit()
        healthy.emit()

        assertEquals(1, fixture.controllerFailures)
        assertEquals(listOf(Observation("run-a", healthy, MusicObservationKind.METADATA_CHANGED)), fixture.observations)
    }

    @Test fun removedAndReaddedSameTokenDoesNotAcceptOldControllerCallbacks() {
        val fixture = Fixture()
        val old = Controller("spotify")
        val replacement = Controller("spotify")
        fixture.registry.start("run-a")
        fixture.registry.reconcile(listOf(old))
        fixture.registry.reconcile(emptyList())
        fixture.registry.reconcile(listOf(replacement))
        fixture.observations.clear()
        old.emit()
        replacement.emit()

        assertEquals(listOf(Observation("run-a", replacement, MusicObservationKind.METADATA_CHANGED)), fixture.observations)
    }

    @Test fun stoppedPausedOrReconnectedCaptureRejectsOldCallbacksEvenForTheSameRunId() {
        val fixture = Fixture()
        val player = Controller("spotify")
        fixture.registry.start("run-a")
        fixture.registry.reconcile(listOf(player))
        val beforePause = player.callbacks.single()
        fixture.registry.stop()
        fixture.observations.clear()
        beforePause(MusicObservationKind.PLAYBACK_CHANGED)
        fixture.registry.reconcile(listOf(player))
        assertTrue(fixture.observations.isEmpty())
        assertEquals(1, player.registrations)

        fixture.registry.start("run-a")
        fixture.registry.reconcile(listOf(player))
        fixture.observations.clear()
        beforePause(MusicObservationKind.METADATA_CHANGED)
        player.emit()
        assertEquals(listOf(Observation("run-a", player, MusicObservationKind.METADATA_CHANGED)), fixture.observations)
    }

    @Test fun startingAnotherRunInvalidatesEveryPreviousRegistrationBeforeCleanup() {
        val fixture = Fixture()
        val player = Controller("spotify").apply { removalFailure = IllegalStateException("dead") }
        fixture.registry.start("run-a")
        fixture.registry.reconcile(listOf(player))
        val previousRun = player.callbacks.single()
        fixture.registry.start("run-b")
        fixture.registry.reconcile(listOf(player))
        fixture.observations.clear()
        previousRun(MusicObservationKind.PLAYBACK_CHANGED)
        player.emit()
        assertEquals(listOf(Observation("run-b", player, MusicObservationKind.METADATA_CHANGED)), fixture.observations)
    }

    @Test fun permissionLossAtDiscoveryRegistrationSnapshotOrRemovalStopsEveryWatch() {
        listOf("identity", "registration", "snapshot", "removal").forEach { failurePoint ->
            val fixture = Fixture()
            val healthy = Controller("spotify")
            val revoked = Controller("revoked")
            fixture.registry.start("run-a")
            fixture.registry.reconcile(listOf(healthy, revoked))
            val denied = SecurityException("revoked")
            when (failurePoint) {
                "identity" -> revoked.identityFailure = denied
                "registration" -> {
                    fixture.registry.reconcile(listOf(healthy))
                    revoked.registrationFailure = denied
                }
                "snapshot" -> revoked.snapshotFailure = denied
                "removal" -> revoked.removalFailure = denied
            }
            fixture.observations.clear()
            when (failurePoint) {
                "snapshot" -> revoked.emit()
                "removal" -> fixture.registry.reconcile(listOf(healthy))
                else -> fixture.registry.reconcile(listOf(healthy, revoked))
            }
            healthy.emit()
            revoked.emit()
            fixture.registry.reconcile(listOf(healthy))
            assertEquals(failurePoint, 1, fixture.accessLosses)
            assertEquals(failurePoint, 0, fixture.controllerFailures)
            assertTrue(failurePoint, fixture.observations.isEmpty())
        }
    }

    @Test fun cancellationIsNeverReportedAsAnOrdinaryControllerFailure() {
        val fixture = Fixture()
        val cancelled = CancellationException("cancelled")
        val player = Controller("spotify").apply { identityFailure = cancelled }
        fixture.registry.start("run-a")
        assertSame(cancelled, assertThrows(CancellationException::class.java) {
            fixture.registry.reconcile(listOf(player))
        })
        assertEquals(0, fixture.controllerFailures)
        assertEquals(0, fixture.accessLosses)
    }

    @Test fun repeatedSamplesObserveOnlyTheRequestedRegisteredPlayerWithoutReplacingCallbacks() {
        val fixture = Fixture()
        val spotify = Controller("spotify")
        val other = Controller("other")
        fixture.registry.start("run-a")
        fixture.registry.reconcile(listOf(spotify, other))
        fixture.observations.clear()

        repeat(3) { fixture.registry.sample { it === spotify } }
        other.emit()

        assertEquals(List(3) { Observation("run-a", spotify, MusicObservationKind.PLAYBACK_SAMPLE) } +
            Observation("run-a", other, MusicObservationKind.METADATA_CHANGED), fixture.observations)
        assertEquals(1, spotify.registrations)
        assertEquals(1, other.registrations)
        fixture.registry.stop()
        fixture.observations.clear()
        fixture.registry.sample()
        assertTrue(fixture.observations.isEmpty())
    }

    @Test fun samplingCannotCrossAStoppedAndReplacedCaptureOwner() {
        val fixture = Fixture()
        val first = Controller("first")
        val second = Controller("second")
        val replacement = Controller("replacement")
        fixture.registry.start("run-a")
        fixture.registry.reconcile(listOf(first, second))
        fixture.observations.clear()
        fixture.afterObservation = { observation ->
            if (observation.kind == MusicObservationKind.PLAYBACK_SAMPLE && observation.controller === first) {
                fixture.registry.start("run-b")
                fixture.registry.reconcile(listOf(replacement))
            }
        }

        fixture.registry.sample()
        assertEquals(listOf(
            Observation("run-a", first, MusicObservationKind.PLAYBACK_SAMPLE),
            Observation("run-b", replacement, MusicObservationKind.SESSION_SNAPSHOT)
        ), fixture.observations)
        fixture.observations.clear()
        first.emit()
        second.emit()
        fixture.registry.sample()
        assertEquals(listOf(Observation("run-b", replacement, MusicObservationKind.PLAYBACK_SAMPLE)),
            fixture.observations)
    }

    @Test fun destroyedSessionCannotSupplyCachedSamplesOrRetireItsReplacement() {
        val fixture = Fixture()
        val original = Controller("spotify")
        val replacement = Controller("spotify")
        fixture.registry.start("run-a")
        fixture.registry.reconcile(listOf(original))
        val oldEnd = original.endedCallbacks.single()
        oldEnd()
        fixture.observations.clear()
        original.emit()
        fixture.registry.sample()
        assertTrue(fixture.observations.isEmpty())
        assertEquals(1, fixture.continuityLosses)
        assertEquals(1, original.removals)

        fixture.registry.reconcile(listOf(replacement))
        fixture.observations.clear()
        oldEnd()
        fixture.registry.sample()
        assertEquals(1, fixture.continuityLosses)
        assertEquals(listOf(Observation("run-a", replacement, MusicObservationKind.PLAYBACK_SAMPLE)),
            fixture.observations)
    }

    @Test fun unreadableSampleLeavesAnHonestGapAndAccessLossStopsFurtherSampling() {
        val fixture = Fixture()
        val recovering = Controller("recovering")
        val healthy = Controller("spotify")
        fixture.registry.start("run-a")
        fixture.registry.reconcile(listOf(recovering, healthy))
        fixture.observations.clear()
        recovering.snapshotFailure = IllegalStateException("unreadable")
        fixture.registry.sample()
        assertEquals(1, fixture.controllerFailures)
        assertEquals(listOf(Observation("run-a", healthy, MusicObservationKind.PLAYBACK_SAMPLE)),
            fixture.observations)

        fixture.observations.clear()
        recovering.snapshotFailure = SecurityException("revoked")
        fixture.registry.sample()
        healthy.emit()
        fixture.registry.sample()
        assertEquals(1, fixture.accessLosses)
        assertTrue(fixture.observations.isEmpty())
    }
}
