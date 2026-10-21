package com.runstate.mobile.music

import com.runstate.mobile.data.local.MusicObservationKind
import java.util.concurrent.CancellationException

/** Only media-session observation is exposed; this boundary has no playback controls. */
internal interface MusicSessionHandle {
    val token: Any
    val isOwnApp: Boolean
    fun registerCallback(onEvent: (MusicObservationKind) -> Unit, onSessionEnded: () -> Unit)
    fun unregisterCallback()
}

/**
 * Confines a broken controller to its own observation gap. All calls and callbacks belong
 * on the listener's main thread. Each start and registration has its own identity, so a
 * queued callback cannot acquire a later run, resumed interval, or replacement controller.
 */
internal class MusicSessionRegistry<C : MusicSessionHandle>(
    private val onObservation: (String, C, MusicObservationKind) -> Unit,
    private val onControllerFailure: () -> Unit,
    private val onAccessLost: () -> Unit,
    private val onContinuityLost: () -> Unit = {}
) {
    private class Owner(val runId: String)
    private class Registration<C>(val owner: Owner, val token: Any, val controller: C)

    private var owner: Owner? = null
    private val registrations = linkedMapOf<Any, Registration<C>>()

    fun start(runId: String) {
        stop()
        owner = Owner(runId)
    }

    fun stop() {
        owner = null
        val removed = registrations.values.toList()
        registrations.clear()
        removed.forEach { registration ->
            try { registration.controller.unregisterCallback() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: RuntimeException) {
                // Ownership is already gone even if Android cannot unregister a dead session.
            }
        }
    }

    fun reconcile(active: List<C>) {
        val current = owner ?: return
        val byToken = linkedMapOf<Any, C>()
        active.forEach { controller ->
            guardController {
                if (!controller.isOwnApp) byToken[controller.token] = controller
            }
            if (owner !== current) return
        }
        registrations.keys.filter { it !in byToken }.forEach { token ->
            val removed = registrations.remove(token) ?: return@forEach
            onContinuityLost()
            guardController { removed.controller.unregisterCallback() }
            if (owner !== current) return
        }
        byToken.forEach { (token, controller) ->
            if (token !in registrations) {
                val registration = Registration(current, token, controller)
                registrations[token] = registration
                val registered = guardController {
                    controller.registerCallback(
                        onEvent = { kind -> observe(registration, kind) },
                        onSessionEnded = { retire(registration) }
                    )
                }
                if (owner !== current) return
                if (registered) {
                    observe(registration, MusicObservationKind.SESSION_SNAPSHOT)
                } else {
                    // Registration can fail after installing a callback; invalidate first.
                    registrations.remove(token)
                    guardController { controller.unregisterCallback() }
                }
                if (owner !== current) return
            }
        }
    }

    /** A caller-owned timer samples only the requested players; this never sends playback commands. */
    fun sample(shouldSample: (C) -> Boolean = { true }) {
        val current = owner ?: return
        registrations.values.toList().forEach { registration ->
            if (owner !== current) return
            if (registrations[registration.token] !== registration) return@forEach
            guardController {
                if (shouldSample(registration.controller)) {
                    observe(registration, MusicObservationKind.PLAYBACK_SAMPLE)
                }
            }
        }
    }

    private fun retire(registration: Registration<C>) {
        if (owner !== registration.owner || registrations[registration.token] !== registration) return
        registrations.remove(registration.token)
        onContinuityLost()
        guardController { registration.controller.unregisterCallback() }
    }

    private fun observe(registration: Registration<C>, kind: MusicObservationKind) {
        if (owner !== registration.owner || registrations[registration.token] !== registration) return
        guardController { onObservation(registration.owner.runId, registration.controller, kind) }
    }

    private fun guardController(action: () -> Unit): Boolean {
        return try {
            action()
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            // Permission loss is global, unlike an individual app's unreadable controller.
            stop()
            onAccessLost()
            false
        } catch (_: RuntimeException) {
            onControllerFailure()
            false
        }
    }
}
