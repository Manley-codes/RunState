package com.runstate.mobile.ui

import android.content.Context
import com.runstate.mobile.RunStateApplication
import com.runstate.mobile.data.local.ReflectionBranch
import com.runstate.mobile.music.SongPassageStatus
import com.runstate.mobile.music.SpotifyLivePlayerState
import com.runstate.mobile.reflection.ReflectionSpeechGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

internal data class MusicReflectionDemoAudioState(
    val reflection: RunCompleteReflectionDisplay? = null,
    val active: Boolean = false,
    val error: String? = null
)

internal interface MusicReflectionDemoAudio {
    val player: StateFlow<SpotifyLivePlayerState>
    val state: StateFlow<MusicReflectionDemoAudioState>
    fun prepare()
    fun startMusic()
    fun toggleMusic()
    fun restartMusic()
    fun finishRun()
    fun selectEnergy(energy: ReflectionBranch)
    fun setPresentationHeld(held: Boolean)
    fun stop()
    fun retry()
    fun close()
}

/** A session-local presentation: no run repository, preferences or tracking service are available. */
internal class MusicReflectionDemoController(
    private val speaker: ReflectionSpeaker,
    private val transport: MusicReflectionDemoTransport,
    private val prepareVoice: suspend (text: String, runVoice: Boolean) -> Boolean,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) : MusicReflectionDemoAudio {
    override val player = transport.player
    private val mutableState = MutableStateFlow(MusicReflectionDemoAudioState())
    override val state = mutableState.asStateFlow()
    private var generation = 0
    private var closed = false
    private var completed = false
    private var factsDone = false
    private var playbackStopped = false
    private var presentationHeld = false
    private var selectedEnergy: ReflectionBranch? = null
    private var deliveredEnergy: ReflectionBranch? = null
    private var job: Job? = null
    private var preparation: Job? = null
    private var musicJob: Job? = null

    override fun prepare() {
        if (closed || preparation?.isActive == true) return
        preparation = scope.launch {
            // Silent prefetch only; actual playback starts through an explicit UI action.
            coroutineScope {
                val facts = async { prepareVoice(MusicReflectionDemoData.facts, true) }
                val reflection = async { prepareVoice(MusicReflectionDemoData.approvedReflection, false) }
                facts.await()
                reflection.await()
            }
        }
    }

    override fun startMusic() {
        if (closed || completed) return
        playbackStopped = false
        val token = ++generation
        val previous = musicJob
        transport.stop()
        previous?.cancel()
        musicJob = scope.launch {
            previous?.join()
            if (!owns(token)) return@launch
            mutableState.value = mutableState.value.copy(error = null)
            when (transport.playOpening()) {
                SongPassageStatus.UNAVAILABLE, SongPassageStatus.FAILED ->
                    if (owns(token)) mutableState.value = mutableState.value.copy(
                        error = "Connect Spotify in Run settings, then try again.")
                else -> Unit
            }
        }
    }

    override fun toggleMusic() {
        if (closed || completed) return
        if (musicJob?.isActive == true) {
            generation++
            transport.stop()
            musicJob?.cancel()
        } else startMusic()
    }

    override fun restartMusic() = startMusic()

    override fun finishRun() {
        if (closed || completed) return
        completed = true
        mutableState.value = mutableState.value.copy(reflection = RunCompleteReflectionDisplay.Preparing)
        playFacts()
    }

    private fun playFacts() {
        playbackStopped = false
        val token = ++generation
        job?.cancel()
        speaker.stop()
        transport.stop()
        val previousMusic = musicJob
        previousMusic?.cancel()
        mutableState.value = mutableState.value.copy(active = true, error = null)
        job = scope.launch {
            previousMusic?.join() // Release the Spotify lease before voice focus and the ending passage.
            if (!owns(token)) return@launch
            val ready = prepareVoice(MusicReflectionDemoData.facts, true)
            if (!owns(token)) return@launch
            if (!ready) {
                fail("The run voice couldn’t load. Try again.")
                return@launch
            }
            if (!owns(token)) return@launch
            speaker.speakCompletionFacts(MusicReflectionDemoData.facts) {
                if (owns(token)) {
                    factsDone = true
                    mutableState.value = mutableState.value.copy(active = false)
                    playReflectionIfReady()
                }
            }
        }
    }

    override fun selectEnergy(energy: ReflectionBranch) {
        if (closed || !completed || selectedEnergy == energy) return
        if (factsDone) {
            generation++
            job?.cancel()
            speaker.stop()
            mutableState.value = mutableState.value.copy(active = false)
        }
        selectedEnergy = energy
        // The normal Energy tile animation releases this hold after it merges into the reply.
        // Set it synchronously: the Compose effect may arrive after this click callback.
        presentationHeld = energy != ReflectionBranch.NO_SELECTION
        mutableState.value = mutableState.value.copy(
            reflection = RunCompleteReflectionDisplay.Ready(MusicReflectionDemoData.reflectionText(energy)))
        if (factsDone) playReflectionIfReady()
    }

    override fun setPresentationHeld(held: Boolean) {
        presentationHeld = held
        if (!held) playReflectionIfReady()
    }

    private fun playReflectionIfReady() {
        val energy = selectedEnergy ?: return
        if (closed || playbackStopped || !completed || !factsDone || presentationHeld || deliveredEnergy == energy ||
            mutableState.value.error != null) return
        deliveredEnergy = energy
        val token = ++generation
        job?.cancel()
        speaker.stop()
        val text = MusicReflectionDemoData.reflectionText(energy)
        mutableState.value = mutableState.value.copy(active = true, error = null)
        job = scope.launch {
            val ready = prepareVoice(text, false)
            if (!owns(token)) return@launch
            if (!ready) {
                fail("The reflection voice couldn’t load. Try again.")
                return@launch
            }
            if (!owns(token)) return@launch
            speaker.speakWithSongPassage(text, MusicReflectionDemoData.passage(),
                canPlayPassage = { owns(token) }) {
                if (owns(token)) mutableState.value = mutableState.value.copy(active = false)
            }
        }
    }

    internal fun fail(message: String) {
        if (closed) return
        stop()
        mutableState.value = mutableState.value.copy(error = message)
    }

    override fun stop() {
        playbackStopped = true
        generation++ // Invalidate callbacks before stopping a speaker that can complete synchronously.
        job?.cancel()
        preparation?.cancel()
        musicJob?.cancel()
        speaker.stop()
        transport.stop()
        mutableState.value = mutableState.value.copy(active = false)
    }

    override fun retry() {
        if (closed) return
        playbackStopped = false
        mutableState.value = mutableState.value.copy(error = null)
        when {
            !completed -> startMusic()
            !factsDone -> playFacts()
            else -> { deliveredEnergy = null; playReflectionIfReady() }
        }
    }

    override fun close() {
        if (closed) return
        stop()
        closed = true
        speaker.close()
        transport.close()
        scope.cancel()
    }

    private fun owns(token: Int) = !closed && generation == token
}

internal class AndroidMusicReflectionDemoAudio(context: Context) : MusicReflectionDemoAudio by
    createMusicReflectionDemoAudio(context.applicationContext)

private fun createMusicReflectionDemoAudio(context: Context): MusicReflectionDemoAudio {
    val app = context as RunStateApplication
    val voices = DemoVoicePreparation(app.runVoiceGateway, app.reflectionSpeechGateway)
    val base = AndroidReflectionSpeechPlatform(context)
    lateinit var controller: MusicReflectionDemoController
    val platform = object : ReflectionSpeechPlatform by base {
        override fun runVoiceGateway() = ReflectionSpeechGateway { voices.get(it, true) }
        override fun reflectionSpeechGateway() = ReflectionSpeechGateway { voices.get(it, false) }
        override fun preparedRunVoiceAudio(text: String) = voices.get(text, true)
        override fun requestAudioFocus(utteranceId: String, onFocusLost: (String) -> Unit): Boolean {
            val granted = base.requestAudioFocus(utteranceId) {
                controller.fail("Audio was interrupted. Try again."); onFocusLost(it)
            }
            if (!granted) controller.fail("Audio is busy. Try again.")
            return granted
        }
        override fun requestPausingAudioFocus(utteranceId: String, onFocusLost: (String) -> Unit): Boolean {
            val granted = base.requestPausingAudioFocus(utteranceId) {
                controller.fail("Audio was interrupted. Try again."); onFocusLost(it)
            }
            if (!granted) controller.fail("Audio is busy. Try again.")
            return granted
        }
        // This showcase promises the selected voices; never quietly replace them with device TTS.
        override fun speak(utteranceId: String, text: String, rate: Float): Boolean {
            controller.fail("The voice couldn’t play. Try again.")
            return false
        }
        override fun speakReceipt(utteranceId: String, text: String, rate: Float) =
            speak(utteranceId, text, rate)
        override fun playReflectionAudio(utteranceId: String, audio: ByteArray,
            onDone: (String) -> Unit, onUnavailable: (String) -> Unit, onError: (String) -> Unit): Boolean =
            base.playReflectionAudio(utteranceId, audio, onDone, {
                controller.fail("The reflection voice couldn’t play. Try again."); onError(it)
            }, { controller.fail("The reflection voice couldn’t play. Try again."); onError(it) })
        override fun playCompletionFactsAudio(utteranceId: String, audio: ByteArray,
            onDone: (String) -> Unit, onUnavailable: (String) -> Unit, onError: (String) -> Unit): Boolean =
            base.playCompletionFactsAudio(utteranceId, audio, onDone, {
                controller.fail("The run voice couldn’t play. Try again."); onError(it)
            }, { controller.fail("The run voice couldn’t play. Try again."); onError(it) })
        override fun playSongPassage(utteranceId: String, passage: com.runstate.mobile.music.SongPassageRequest,
            onDone: (String) -> Unit, onError: (String) -> Unit, onFocusLost: (String) -> Unit): Boolean =
            base.playSongPassage(utteranceId, passage, onDone, {
                controller.fail("The music couldn’t play. Check Spotify and try again.")
                onError(it)
            }, onFocusLost)
    }
    controller = MusicReflectionDemoController(AndroidReflectionSpeaker(context, platform),
        AndroidMusicReflectionDemoTransport(context), voices::prepare)
    return controller
}

/** Only exact showcase lines are cached in memory; credentials and recordings are never bundled. */
private class DemoVoicePreparation(
    private val runGateway: ReflectionSpeechGateway,
    private val reflectionGateway: ReflectionSpeechGateway
) {
    private val audio = java.util.concurrent.ConcurrentHashMap<Pair<String, Boolean>, ByteArray>()
    private val locks = java.util.concurrent.ConcurrentHashMap<Pair<String, Boolean>, Mutex>()
    fun get(text: String, runVoice: Boolean): ByteArray? = audio[text to runVoice]
    suspend fun prepare(text: String, runVoice: Boolean): Boolean =
        locks.getOrPut(text to runVoice) { Mutex() }.withLock {
            if (get(text, runVoice) != null) return@withLock true
            val bytes = try {
                withTimeoutOrNull(22_000) {
                    (if (runVoice) runGateway else reflectionGateway).synthesize(text)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            if (bytes == null || bytes.isEmpty() || bytes.size > 2 * 1024 * 1024) false
            else { audio[text to runVoice] = bytes; true }
        }
}
