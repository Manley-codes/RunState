package com.runstate.mobile.reflection

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

/** Timed playback reads memory immediately. Only the six fixed phrases may also use private disk cache. */
internal class RunVoiceCueCache(private val files: FixedRunVoiceFiles?) {
    private class Voice(val key: String, val gateway: ReflectionSpeechGateway) {
        val audio = ConcurrentHashMap<String, ByteArray>()
    }

    @Volatile private var voice: Voice? = null
    private val preparing = Mutex()

    /** Called by the serialized connection owner. An old response cannot populate a new voice. */
    suspend fun select(key: String?, gateway: ReflectionSpeechGateway?, clearPrevious: Boolean) {
        voice = null
        if (clearPrevious) files?.clear()
        if (key != null && gateway != null) voice = Voice(key, gateway)
    }

    /** Owned by the visible Start/countdown. Leaving it cancels further requests. */
    suspend fun prefetch(requested: List<String> = RUN_VOICE_PREPARED_LINES, fetchMissing: Boolean = true) = preparing.withLock {
        val selected = voice ?: return@withLock
        val lines = RUN_VOICE_PREPARED_LINES.filter { it in requested }
        for (line in lines) {
            if (voice !== selected) return@withLock
            if (selected.audio.containsKey(line)) continue
            files?.read(selected.key, line)?.let { selected.audio[line] = it }
        }
        if (!fetchMissing) return@withLock
        for (line in lines) {
            currentCoroutineContext().ensureActive()
            if (voice !== selected) return@withLock
            if (selected.audio.containsKey(line)) continue
            val bytes = try { selected.gateway.synthesize(line) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            currentCoroutineContext().ensureActive()
            if (voice !== selected || bytes == null || bytes.isEmpty() || bytes.size > MAX_REFLECTION_AUDIO_BYTES) {
                return@withLock
            }
            selected.audio[line] = bytes
            files?.write(selected.key, line, bytes) { voice === selected }
        }
    }

    fun audioFor(text: String): ByteArray? = voice?.audio?.get(text)

    fun hasAll(texts: List<String>): Boolean {
        val selected = voice ?: return false
        return texts.all { selected.audio.containsKey(it) }
    }
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
