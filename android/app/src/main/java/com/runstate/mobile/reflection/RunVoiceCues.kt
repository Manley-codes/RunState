package com.runstate.mobile.reflection

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The neutral question after the saved facts. One exact string, so its prepared audio matches. */
internal const val ENERGY_QUESTION_TEXT = "How’s your energy now?"

internal const val COUNTDOWN_INTRO_TEXT = "Starting in ten."
internal const val COUNTDOWN_FINISH_TEXT = "Let’s go, runner."

/** Ten is part of the joined intro; the final encouragement follows one and the real start. */
internal fun countdownCueText(step: Int): String = when (step) {
    10 -> "Ten."
    3 -> "Three."
    2 -> "Two."
    1 -> "One."
    else -> "$step."
}

internal val COUNTDOWN_VOICE_LINES: List<String> =
    listOf(COUNTDOWN_INTRO_TEXT) + listOf(3, 2, 1).map(::countdownCueText) + COUNTDOWN_FINISH_TEXT

/** Fixed run-voice lines prepared ahead of time: the countdown first, then the energy question. */
internal val RUN_VOICE_PREPARED_LINES: List<String> = COUNTDOWN_VOICE_LINES + ENERGY_QUESTION_TEXT

/**
 * Keeps the fixed run-voice lines in memory for this app session, so a timed cue never waits on
 * the network. Lines are fetched one at a time. A failed line ends that attempt, and a later
 * [prefetch] fetches only what is still missing. Nothing is written to storage.
 */
internal class RunVoiceCueCache(
    private val scope: CoroutineScope,
    private val gateway: ReflectionSpeechGateway,
    private val lines: List<String> = RUN_VOICE_PREPARED_LINES
) {
    private val audio = ConcurrentHashMap<String, ByteArray>()
    @Volatile private var attempt: Job? = null

    /** Safe to call repeatedly: one attempt at a time, and finished lines are never fetched again. */
    fun prefetch() {
        if (attempt?.isActive == true || hasAll(lines)) return
        attempt = scope.launch {
            for (line in lines) {
                if (audio.containsKey(line)) continue
                val bytes = try { gateway.synthesize(line) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
                if (bytes == null || bytes.isEmpty() || bytes.size > MAX_REFLECTION_AUDIO_BYTES) return@launch
                audio[line] = bytes
            }
        }
    }

    fun audioFor(text: String): ByteArray? = audio[text]

    fun hasAll(texts: List<String>): Boolean = texts.all { audio.containsKey(it) }
}

/** A voice that can load itself silently before its first line, and be released if never used. */
internal interface RehearsableVoice {
    /** Loads everything needed to speak [text] without making a sound. */
    fun warmUp(text: String)
    fun close()
}

/**
 * Keeps one countdown voice awake while Start is visible. On the Moto, RunState's own work after
 * the Start tap took about 0.1 seconds, but the sleeping phone speech engine needed about 3.2 more
 * to wake and load its voice before "Starting in ten" (October 24 log). Preparing the voice early,
 * with one silent rehearsal of that line, moves the wait off the tap. Main thread only.
 * [take] hands the voice to the countdown, which then owns and closes it; [release] closes a voice
 * nobody took.
 */
internal class PreparedCountdownVoice<V : RehearsableVoice>(private val create: () -> V) {
    private var voice: V? = null

    /** Safe to call repeatedly: at most one voice waits, and it rehearses the opening once. */
    fun prepare() {
        if (voice != null) return
        val created = try { create() } catch (_: RuntimeException) { return }
        voice = created
        try { created.warmUp(COUNTDOWN_INTRO_TEXT) } catch (_: RuntimeException) { }
    }

    /** The caller owns the returned voice. The next [prepare] creates a fresh one. */
    fun take(): V? = voice.also { voice = null }

    fun release() {
        val unused = voice ?: return
        voice = null
        try { unused.close() } catch (_: RuntimeException) { }
    }
}
