package com.runstate.mobile.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.VolumeShaper
import android.media.MediaDataSource
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.runstate.mobile.data.local.PostRunEffort
import com.runstate.mobile.data.local.TelemetryCoverage
import com.runstate.mobile.data.local.PreRunEnergy
import com.runstate.mobile.data.local.MetricSource
import com.runstate.mobile.data.local.RunSplitStatus
import com.runstate.mobile.R
import com.runstate.mobile.RunStateApplication
import com.runstate.mobile.reflection.ReflectionSpeechGateway
import com.runstate.mobile.reflection.MAX_REFLECTION_AUDIO_BYTES
import com.runstate.mobile.music.AndroidSpotifyPassageSession
import com.runstate.mobile.music.SongPassageRequest
import com.runstate.mobile.music.SongPassageStatus
import com.runstate.mobile.music.SongPassageDiagnostic
import com.runstate.mobile.music.SpotifySongPassageController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import com.runstate.mobile.data.local.ReflectionBranch

internal const val RUN_HISTORY_SCREEN_TAG = "runHistoryScreen"
internal const val RUN_HISTORY_LIST_TAG = "runHistoryList"
internal const val RUN_HISTORY_ROW_TAG_PREFIX = "runHistoryRow:"
internal const val RUN_HISTORY_REFLECTION_TAG_PREFIX = "runHistoryReflection:"
internal const val RUN_HISTORY_RETRY_TAG_PREFIX = "runHistoryRetry:"
internal const val RUN_HISTORY_EFFORT_TAG_PREFIX = "runHistoryEffort:"
internal const val RUN_HISTORY_ENERGY_TAG_PREFIX = "runHistoryEnergy:"
internal const val RUN_HISTORY_ENERGY_SHEET_TAG = "runHistoryEnergySheet"
internal const val RUN_HISTORY_ENERGY_CHOICE_TAG_PREFIX = "runHistoryEnergyChoice:"

/** Kept injectable so screen behavior can be checked without a device voice engine. */
internal interface ReflectionSpeaker {
    /** Prepare one exact reflection silently; custom speakers can retain their existing behavior. */
    fun prepare(text: String) = Unit
    fun cancelPreparation() = Unit
    fun speak(text: String)
    fun speakWithSongPassage(
        text: String,
        passage: SongPassageRequest,
        canPlayPassage: () -> Boolean = { true },
        onFinished: () -> Unit
    ) {
        try { speak(text) } finally { onFinished() }
    }
    fun speakWithInstrumental(
        text: String,
        canPlayInstrumental: () -> Boolean = { true },
        onFinished: () -> Unit
    ) {
        // Test/custom speakers without a player retain their plain-speech behavior.
        try { speak(text) } finally { onFinished() }
    }
    fun speakReceipt(text: String, onFinished: () -> Unit) {
        speak(text)
        onFinished()
    }
    fun stop()
    fun close()
}

/** The Android boundary is separate so request ownership can be verified without playing audio. */
internal interface ReflectionSpeechPlatform {
    fun reflectionSpeechGateway(): ReflectionSpeechGateway? = null
    fun initialize(onReady: (Boolean) -> Unit, onDone: (String) -> Unit, onError: (String) -> Unit)
    fun requestAudioFocus(utteranceId: String, onFocusLost: (String) -> Unit): Boolean
    fun speak(utteranceId: String, text: String, rate: Float): Boolean
    /** The saved facts have their own delivery; reflection/fallback speech keeps its normal level. */
    fun speakReceipt(utteranceId: String, text: String, rate: Float): Boolean =
        speak(utteranceId, text, rate)
    /** Unavailable is allowed only before playback starts; an audible failure must never repeat text. */
    fun playReflectionAudio(
        utteranceId: String, audio: ByteArray,
        onDone: (String) -> Unit, onUnavailable: (String) -> Unit, onError: (String) -> Unit
    ): Boolean = false
    fun stopReflectionAudio() = Unit
    fun playSongPassage(
        utteranceId: String,
        passage: SongPassageRequest,
        onDone: (String) -> Unit,
        onError: (String) -> Unit,
        onFocusLost: (String) -> Unit
    ): Boolean = false
    fun stopSongPassage() = Unit
    fun playInstrumental(
        utteranceId: String,
        onDone: (String) -> Unit,
        onError: (String) -> Unit,
        onFocusLost: (String) -> Unit
    ): Boolean = false
    fun stopInstrumental() = Unit
    fun stop()
    fun releaseAudioFocus()
    fun close()
}

/** The selected saved text owns one voice attempt and, only on success, its optional music. */
internal class AndroidReflectionSpeaker(
    context: Context,
    private val platform: ReflectionSpeechPlatform = AndroidReflectionSpeechPlatform(context),
    private val scheduleTimeout: ((Runnable, Long) -> Unit)? = null,
    private val cancelTimeout: ((Runnable) -> Unit)? = null
) : ReflectionSpeaker {
    private enum class SpeechPhase { PREPARING_CLOUD, WAITING_FOR_ENGINE, SPEAKING, WAITING_FOR_MUSIC, INSTRUMENTAL }
    private enum class SpeechPurpose { RECEIPT, REFLECTION }

    private data class SpeechRequest(
        val id: String,
        val text: String,
        val rate: Float,
        val purpose: SpeechPurpose,
        val onFinished: (() -> Unit)?,
        val canPlayInstrumental: (() -> Boolean)?,
        val songPassage: SongPassageRequest? = null,
        var phase: SpeechPhase = SpeechPhase.WAITING_FOR_ENGINE,
        var cloudAudio: Boolean = false
    )

    private data class PreparedReflection(val text: String, val audio: Deferred<ByteArray?>)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val voiceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var preparation: Job? = null
    private var preparedReflection: PreparedReflection? = null
    private var adoptedReflection: PreparedReflection? = null
    private var initialized = false
    private var initializationFailed = false
    private var request: SpeechRequest? = null
    private var speechTimeout: Runnable? = null
    private var closed = false

    init {
        try {
            platform.initialize(
                onReady = { ready -> onMain {
                    if (!closed) {
                        initialized = ready
                        initializationFailed = !initialized
                        if (request?.phase == SpeechPhase.WAITING_FOR_ENGINE) {
                            if (initialized) speakPending() else request?.id?.let(::finish)
                        }
                    }
                } },
                onDone = { id -> onMain { if (request?.cloudAudio != true) finishSpeech(id) } },
                onError = { id -> onMain {
                    if (request?.id == id && !requireNotNull(request).cloudAudio &&
                        request?.phase == SpeechPhase.SPEAKING) stop()
                } }
            )
        } catch (_: RuntimeException) {
            initializationFailed = true
            // The exact saved text remains on screen if no speech engine is available.
        }
    }

    override fun speak(text: String) {
        submit(text, rate = 0.94f, onFinished = null, canPlayInstrumental = null)
    }

    override fun prepare(text: String) {
        if (closed || !validSpeechText(text)) {
            cancelPreparation()
            return
        }
        if (preparedReflection?.text == text) return
        cancelPreparation()
        val gateway = try { platform.reflectionSpeechGateway() } catch (_: RuntimeException) { null } ?: return
        preparedReflection = PreparedReflection(text, voiceScope.async {
            withTimeoutOrNull(20_000L) { synthesizeAudio(gateway, text) }
        })
    }

    override fun cancelPreparation() {
        val abandoned = preparedReflection
        preparedReflection = null
        abandoned?.audio?.cancel()
    }

    private fun cancelAdoptedPreparation() {
        val abandoned = adoptedReflection
        adoptedReflection = null
        abandoned?.audio?.cancel()
    }

    private fun validSpeechText(text: String) = text.isNotBlank() &&
        text.length <= TextToSpeech.getMaxSpeechInputLength()

    private suspend fun synthesizeAudio(gateway: ReflectionSpeechGateway, text: String): ByteArray? = try {
        gateway.synthesize(text)?.takeIf { it.isNotEmpty() && it.size <= MAX_REFLECTION_AUDIO_BYTES }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { null }

    override fun speakWithSongPassage(
        text: String,
        passage: SongPassageRequest,
        canPlayPassage: () -> Boolean,
        onFinished: () -> Unit
    ) {
        submit(text, 0.94f, onFinished, canPlayPassage, passage)
    }

    override fun speakWithInstrumental(
        text: String,
        canPlayInstrumental: () -> Boolean,
        onFinished: () -> Unit
    ) {
        submit(text, rate = 0.94f, onFinished = onFinished, canPlayInstrumental = canPlayInstrumental)
    }

    override fun speakReceipt(text: String, onFinished: () -> Unit) {
        submit(text, rate = 0.96f, onFinished = onFinished, canPlayInstrumental = null,
            purpose = SpeechPurpose.RECEIPT)
    }

    private fun submit(
        text: String,
        rate: Float,
        onFinished: (() -> Unit)?,
        canPlayInstrumental: (() -> Boolean)?,
        songPassage: SongPassageRequest? = null,
        purpose: SpeechPurpose = SpeechPurpose.REFLECTION
    ) {
        // A matching silent request becomes this speech attempt's sole preparation. Detach it
        // before stop() clears the old owner, so an in-flight request is awaited, not repeated.
        val prepared = preparedReflection?.takeIf {
            purpose == SpeechPurpose.REFLECTION && !closed && validSpeechText(text) && it.text == text
        }
        if (prepared != null) preparedReflection = null
        stop()
        if (closed || !validSpeechText(text)) {
            onFinished?.invoke()
            return
        }
        val next = SpeechRequest(UUID.randomUUID().toString(), text, rate, purpose, onFinished,
            canPlayInstrumental, songPassage)
        request = next
        val gateway = if (purpose == SpeechPurpose.REFLECTION) platform.reflectionSpeechGateway() else null
        if (prepared == null && gateway == null) useLocalVoice(next) else {
            adoptedReflection = prepared
            next.phase = SpeechPhase.PREPARING_CLOUD
            armTimeout(next.id, 25_000L)
            preparation = voiceScope.launch {
                val audio = if (prepared != null) {
                    try { prepared.audio.await() }
                    catch (cancelled: CancellationException) {
                        // Cancelling the speech owner still exits immediately. A separately
                        // cancelled prepared worker instead falls back once without another call.
                        coroutineContext.ensureActive()
                        null
                    }
                } else synthesizeAudio(requireNotNull(gateway), next.text)
                if (request !== next || closed || next.phase != SpeechPhase.PREPARING_CLOUD) return@launch
                if (adoptedReflection === prepared) adoptedReflection = null
                preparation = null
                if (audio == null) useLocalVoice(next) else playCloudVoice(next, audio)
            }
        }
    }

    private fun useLocalVoice(current: SpeechRequest) {
        if (request !== current || closed) return
        preparation?.cancel()
        preparation = null
        cancelAdoptedPreparation()
        current.cloudAudio = false
        current.phase = SpeechPhase.WAITING_FOR_ENGINE
        clearTimeout()
        try { platform.stopReflectionAudio() } catch (_: RuntimeException) { }
        releaseFocus()
        if (initializationFailed) finish(current.id)
        else if (initialized) speakPending() else armTimeout(current.id, 15_000L)
    }

    private fun playCloudVoice(current: SpeechRequest, audio: ByteArray) {
        if (request !== current || closed) return
        current.cloudAudio = true
        current.phase = SpeechPhase.SPEAKING
        try {
            if (!platform.requestAudioFocus(current.id) { id -> onMain {
                    if (request?.id == id && request?.phase in
                        setOf(SpeechPhase.SPEAKING, SpeechPhase.WAITING_FOR_MUSIC)) stop()
                } }) {
                finish(current.id)
                return
            }
            if (request !== current || closed) return
            armSpeechTimeout(current)
            fun ownsCloud(id: String) = request === current && current.id == id &&
                current.cloudAudio && current.phase == SpeechPhase.SPEAKING && !closed
            val accepted = platform.playReflectionAudio(current.id, audio,
                { id -> onMain { if (ownsCloud(id)) finishSpeech(id) } },
                { id -> onMain { if (ownsCloud(id)) useLocalVoice(current) } },
                { id -> onMain { if (ownsCloud(id)) stop() } })
            if (!accepted && ownsCloud(current.id)) useLocalVoice(current)
        } catch (_: RuntimeException) {
            // The platform reports pre-playback failures explicitly. Unknown failures cannot repeat speech.
            if (request === current) stop()
        }
    }

    override fun stop() {
        val interrupted = request
        request = null
        clearTimeout()
        preparation?.cancel()
        preparation = null
        cancelPreparation()
        cancelAdoptedPreparation()
        try {
            platform.stop()
        } catch (_: RuntimeException) {
            // A disconnected engine must not disturb History navigation.
        }
        stopInstrumental()
        try { platform.stopReflectionAudio() } catch (_: RuntimeException) { }
        releaseFocus()
        interrupted?.onFinished?.invoke()
    }

    override fun close() {
        if (closed) return
        closed = true
        voiceScope.cancel()
        try {
            stop()
        } finally {
            try { platform.close() } catch (_: RuntimeException) {
                // The screen is already leaving; the visible run is still intact.
            }
        }
    }

    private fun speakPending() {
        val current = request ?: return
        if (current.phase != SpeechPhase.WAITING_FOR_ENGINE) return
        current.phase = SpeechPhase.SPEAKING
        try {
            if (!platform.requestAudioFocus(current.id) { id -> onMain {
                    if (request?.id == id && request?.phase in
                        setOf(SpeechPhase.SPEAKING, SpeechPhase.WAITING_FOR_MUSIC)) stop()
                } }) {
                finish(current.id)
                return
            }
            if (request?.id != current.id || closed) return
            // The bounded playback budget starts only once a ready engine can queue this text.
            armSpeechTimeout(current)
            val accepted = if (current.purpose == SpeechPurpose.RECEIPT) {
                platform.speakReceipt(current.id, current.text, current.rate)
            } else platform.speak(current.id, current.text, current.rate)
            if (!accepted && request?.id == current.id) stop()
        } catch (_: RuntimeException) {
            if (request?.id == current.id) stop()
            // Speech failure is independent of the saved reflection.
        }
    }

    private fun armSpeechTimeout(current: SpeechRequest) {
        val maximum = if (current.purpose == SpeechPurpose.RECEIPT) 120_000L else 600_000L
        armTimeout(current.id, (current.text.length * 100L + 15_000L).coerceIn(30_000L, maximum))
    }

    private fun finishSpeech(utteranceId: String) {
        val current = request?.takeIf {
            it.id == utteranceId && it.phase == SpeechPhase.SPEAKING
        } ?: return
        val allowed = try { current.canPlayInstrumental?.invoke() == true }
        catch (_: RuntimeException) { false }
        if (!allowed || closed || request !== current) {
            finish(utteranceId)
            return
        }
        // Only successful voice completion can schedule music. Keep focus during the brief
        // pause so a background player cannot resume between the voice and selected passage.
        current.phase = SpeechPhase.WAITING_FOR_MUSIC
        clearTimeout()
        if (current.songPassage == null) {
            startMusic(current)
            return
        }
        val handoff = object : Runnable {
            override fun run() {
                if (speechTimeout !== this || request !== current || closed ||
                    current.phase != SpeechPhase.WAITING_FOR_MUSIC) return
                speechTimeout = null
                startMusic(current)
            }
        }
        speechTimeout = handoff
        try {
            if (scheduleTimeout != null) scheduleTimeout.invoke(handoff, 650L)
            else mainHandler.postDelayed(handoff, 650L)
        } catch (_: RuntimeException) {
            if (request === current) stop()
        }
    }

    private fun startMusic(current: SpeechRequest) {
        if (request !== current || closed || current.phase != SpeechPhase.WAITING_FOR_MUSIC) return
        val allowed = try { current.canPlayInstrumental?.invoke() == true }
        catch (_: RuntimeException) { false }
        if (!allowed || request !== current || closed) {
            if (request === current) finish(current.id)
            return
        }
        current.phase = SpeechPhase.INSTRUMENTAL
        releaseFocus()
        if (request !== current || closed) return
        armTimeout(current.id, if (current.songPassage != null) 30_000L else 15_000L)
        try {
            val done: (String) -> Unit = { id -> onMain {
                    if (request?.id == id && request?.phase == SpeechPhase.INSTRUMENTAL) finish(id)
                } }
            val error: (String) -> Unit = { id -> onMain {
                    if (request?.id == id && request?.phase == SpeechPhase.INSTRUMENTAL) stop()
                } }
            val accepted = if (current.songPassage != null) {
                platform.playSongPassage(current.id, current.songPassage, done, error, error)
            } else platform.playInstrumental(current.id, done, error, error)
            if (!accepted && request === current) finish(current.id)
        } catch (_: RuntimeException) {
            if (request === current) stop()
        }
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == mainHandler.looper) action() else mainHandler.post { action() }
    }

    private fun armTimeout(id: String, delayMillis: Long) {
        clearTimeout()
        val timeout = object : Runnable {
            override fun run() {
                if (speechTimeout === this && request?.id == id) {
                    val current = requireNotNull(request)
                    if (current.phase == SpeechPhase.PREPARING_CLOUD) useLocalVoice(current) else stop()
                }
            }
        }
        speechTimeout = timeout
        if (scheduleTimeout != null) scheduleTimeout.invoke(timeout, delayMillis)
        else mainHandler.postDelayed(timeout, delayMillis)
    }

    private fun clearTimeout() {
        speechTimeout?.let { timeout ->
            if (cancelTimeout != null) cancelTimeout.invoke(timeout) else mainHandler.removeCallbacks(timeout)
        }
        speechTimeout = null
    }

    private fun finish(utteranceId: String) {
        val finished = request?.takeIf { it.id == utteranceId } ?: return
        request = null
        clearTimeout()
        preparation?.cancel()
        preparation = null
        cancelAdoptedPreparation()
        try { platform.stopReflectionAudio() } catch (_: RuntimeException) { }
        stopInstrumental()
        releaseFocus()
        finished.onFinished?.invoke()
    }

    private fun stopInstrumental() {
        try { platform.stopSongPassage() } catch (_: RuntimeException) { }
        try { platform.stopInstrumental() }
        catch (_: RuntimeException) { /* Saved text remains available without music playback. */ }
    }

    private fun releaseFocus() {
        try { platform.releaseAudioFocus() }
        catch (_: RuntimeException) { /* Saved text stays readable if the audio service disconnects. */ }
    }
}

internal class AndroidReflectionSpeechPlatform(
    context: Context,
    passageDiagnosticObserver: (SongPassageDiagnostic) -> Unit = {}
) : ReflectionSpeechPlatform {
    private class MemoryAudioSource(private var audio: ByteArray?) : MediaDataSource() {
        @Synchronized override fun getSize(): Long = audio?.size?.toLong() ?: 0L
        @Synchronized override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            val bytes = audio ?: return -1
            if (position < 0L) return -1
            if (size == 0) return 0
            if (position >= bytes.size) return -1
            val count = minOf(size, bytes.size - position.toInt())
            bytes.copyInto(buffer, offset, position.toInt(), position.toInt() + count)
            return count
        }
        @Synchronized override fun close() { audio = null }
    }
    private class ReflectionAudio(
        val id: String, val player: MediaPlayer, val source: MemoryAudioSource,
        var started: Boolean = false, var released: Boolean = false,
        var entryShaper: VolumeShaper? = null
    )
    private class InstrumentalPlayback(
        val id: String,
        val player: MediaPlayer,
        var focus: AudioFocusRequest? = null,
        var released: Boolean = false
    )

    private val appContext = context.applicationContext
    override fun reflectionSpeechGateway(): ReflectionSpeechGateway? =
        (appContext as? RunStateApplication)?.reflectionSpeechGateway
    private val passageScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val passageController by lazy {
        SpotifySongPassageController(AndroidSpotifyPassageSession(appContext),
            diagnosticObserver = passageDiagnosticObserver)
    }
    private var passageJob: Job? = null
    private var passageId: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val speechAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private var engine: TextToSpeech? = null
    private var focusRequest: AudioFocusRequest? = null
    private var instrumental: InstrumentalPlayback? = null
    private var reflectionAudio: ReflectionAudio? = null
    private var closed = false

    override fun initialize(onReady: (Boolean) -> Unit, onDone: (String) -> Unit, onError: (String) -> Unit) {
        engine = TextToSpeech(appContext) { result ->
            // Posting also lets the constructor assign engine before a very fast init callback.
            mainHandler.post {
                if (!closed) {
                    try {
                        val activeEngine = engine
                        val listener = object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) = Unit
                            override fun onDone(utteranceId: String?) { utteranceId?.let(onDone) }
                            @Deprecated("Required by the Android speech listener")
                            override fun onError(utteranceId: String?) { utteranceId?.let(onError) }
                            override fun onError(utteranceId: String?, errorCode: Int) { utteranceId?.let(onError) }
                            override fun onStop(utteranceId: String?, interrupted: Boolean) { utteranceId?.let(onError) }
                        }
                        onReady(result == TextToSpeech.SUCCESS && activeEngine != null &&
                            activeEngine.setAudioAttributes(speechAttributes) == TextToSpeech.SUCCESS &&
                            activeEngine.setOnUtteranceProgressListener(listener) == TextToSpeech.SUCCESS)
                    } catch (_: RuntimeException) { onReady(false) }
                }
            }
        }
    }

    override fun requestAudioFocus(utteranceId: String, onFocusLost: (String) -> Unit): Boolean {
        // Every focus listener belongs to one utterance; a queued old LOSS cannot stop a new one.
        val next = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(speechAttributes)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({ change -> if (change < 0) onFocusLost(utteranceId) }, mainHandler)
            .build()
        focusRequest = next
        return audioManager?.requestAudioFocus(next) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    override fun speak(utteranceId: String, text: String, rate: Float): Boolean =
        speakLocally(utteranceId, text, rate, volume = 1.0f)

    override fun speakReceipt(utteranceId: String, text: String, rate: Float): Boolean =
        speakLocally(utteranceId, text, rate, volume = 0.8f)

    private fun speakLocally(utteranceId: String, text: String, rate: Float, volume: Float): Boolean {
        val activeEngine = engine ?: return false
        // This scales only this utterance; it never changes the phone's media-volume setting.
        val parameters = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume) }
        return activeEngine.setSpeechRate(rate) == TextToSpeech.SUCCESS &&
            activeEngine.speak(text, TextToSpeech.QUEUE_FLUSH, parameters, utteranceId) == TextToSpeech.SUCCESS
    }

    override fun playReflectionAudio(
        utteranceId: String, audio: ByteArray,
        onDone: (String) -> Unit, onUnavailable: (String) -> Unit, onError: (String) -> Unit
    ): Boolean {
        stopReflectionAudio()
        if (closed || audio.isEmpty()) return false
        val playback = ReflectionAudio(utteranceId, MediaPlayer(), MemoryAudioSource(audio))
        reflectionAudio = playback
        fun owns() = reflectionAudio === playback && !playback.released && !closed
        fun failed() {
            if (!owns()) return
            val hadStarted = playback.started
            releaseReflectionAudio(playback)
            if (hadStarted) onError(playback.id) else onUnavailable(playback.id)
        }
        return try {
            playback.player.setAudioAttributes(speechAttributes)
            playback.player.isLooping = false
            playback.player.setOnPreparedListener { player ->
                if (owns()) try {
                    // Ease only this reflection's initial attack; keep the first word audible.
                    // This changes neither the designed voice nor the phone/Spotify volume.
                    playback.entryShaper = try {
                        player.createVolumeShaper(VolumeShaper.Configuration.Builder()
                            .setDuration(320L)
                            .setCurve(floatArrayOf(0f, 0.55f, 1f), floatArrayOf(0.68f, 0.9f, 1f))
                            .setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_CUBIC_MONOTONIC)
                            .build())
                    } catch (_: RuntimeException) { null }
                    player.start()
                    playback.started = true
                    try { playback.entryShaper?.apply(VolumeShaper.Operation.PLAY) }
                    catch (_: RuntimeException) {
                        // Optional polish must not leave speech stuck at its initial lower gain.
                        try { playback.entryShaper?.close() } catch (_: RuntimeException) { }
                        playback.entryShaper = null
                    }
                } catch (_: RuntimeException) { failed() }
            }
            playback.player.setOnCompletionListener {
                if (owns()) {
                    releaseReflectionAudio(playback)
                    onDone(playback.id)
                }
            }
            playback.player.setOnErrorListener { _, _, _ -> failed(); true }
            playback.player.setDataSource(playback.source)
            playback.player.prepareAsync()
            true
        } catch (_: Exception) {
            releaseReflectionAudio(playback)
            false
        }
    }

    override fun stopReflectionAudio() {
        reflectionAudio?.let(::releaseReflectionAudio)
    }

    private fun releaseReflectionAudio(playback: ReflectionAudio) {
        if (playback.released) return
        playback.released = true
        if (reflectionAudio === playback) reflectionAudio = null
        try { playback.player.release() } catch (_: RuntimeException) { }
        // Release the player before closing its envelope, avoiding a volume jump during Stop.
        try { playback.entryShaper?.close() } catch (_: RuntimeException) { }
        playback.entryShaper = null
        playback.source.close()
    }

    override fun playInstrumental(
        utteranceId: String,
        onDone: (String) -> Unit,
        onError: (String) -> Unit,
        onFocusLost: (String) -> Unit
    ): Boolean {
        stopInstrumental()
        if (closed) return false
        val playback = InstrumentalPlayback(utteranceId, MediaPlayer())
        instrumental = playback
        fun failed() {
            if (instrumental !== playback || playback.released) return
            releaseInstrumental(playback)
            onError(playback.id)
        }
        try {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            playback.player.setAudioAttributes(attributes)
            playback.player.isLooping = false
            playback.player.setVolume(0.22f, 0.22f)
            playback.player.setOnPreparedListener { player ->
                if (instrumental === playback && !playback.released && !closed) {
                    try { player.start() } catch (_: RuntimeException) { failed() }
                }
            }
            playback.player.setOnCompletionListener {
                if (instrumental === playback && !playback.released) {
                    releaseInstrumental(playback)
                    onDone(playback.id)
                }
            }
            playback.player.setOnErrorListener { _, _, _ -> failed(); true }
            val nextFocus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener({ change ->
                    if (change < 0 && instrumental === playback && !playback.released) {
                        // Never resume a cue after a call, another player, or lost focus.
                        releaseInstrumental(playback)
                        onFocusLost(playback.id)
                    }
                }, mainHandler)
                .build()
            playback.focus = nextFocus
            val granted = audioManager?.requestAudioFocus(nextFocus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            if (!granted || instrumental !== playback || playback.released || closed) {
                releaseInstrumental(playback)
                return false
            }
            appContext.resources.openRawResourceFd(R.raw.runstate_reflection_instrumental).use { source ->
                playback.player.setDataSource(source.fileDescriptor, source.startOffset, source.length)
            }
            playback.player.prepareAsync()
            return true
        } catch (_: Exception) {
            releaseInstrumental(playback)
            return false
        }
    }

    override fun playSongPassage(
        utteranceId: String,
        passage: SongPassageRequest,
        onDone: (String) -> Unit,
        onError: (String) -> Unit,
        onFocusLost: (String) -> Unit
    ): Boolean {
        stopSongPassage()
        if (closed) return false
        passageId = utteranceId
        passageJob = passageScope.launch {
            try {
                val result = passageController.play(passage)
                if (passageId == utteranceId && !closed) {
                    passageId = null
                    passageJob = null
                    if (result.status == SongPassageStatus.FINISHED) onDone(utteranceId)
                    else onError(utteranceId)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RuntimeException) {
                if (passageId == utteranceId && !closed) {
                    passageId = null
                    passageJob = null
                    onError(utteranceId)
                }
            }
        }
        return true
    }

    override fun stopSongPassage() {
        passageId = null
        passageController.stop()
        passageJob?.cancel()
        passageJob = null
    }

    override fun stopInstrumental() {
        instrumental?.let(::releaseInstrumental)
    }

    private fun releaseInstrumental(playback: InstrumentalPlayback) {
        if (playback.released) return
        // Invalidate identity before release/abandon can deliver queued callbacks.
        playback.released = true
        if (instrumental === playback) instrumental = null
        try { playback.player.release() } catch (_: RuntimeException) { }
        val focus = playback.focus
        playback.focus = null
        if (focus != null) {
            try { audioManager?.abandonAudioFocusRequest(focus) } catch (_: RuntimeException) { }
        }
    }

    override fun stop() {
        stopReflectionAudio()
        engine?.stop()
    }
    override fun releaseAudioFocus() {
        val owned = focusRequest ?: return
        focusRequest = null
        audioManager?.abandonAudioFocusRequest(owned)
    }
    override fun close() {
        closed = true
        stopReflectionAudio()
        stopSongPassage()
        passageScope.cancel()
        stopInstrumental()
        try { releaseAudioFocus() } catch (_: RuntimeException) { }
        val owned = engine
        engine = null
        owned?.shutdown()
    }
}

/** Display facts read from local storage; opening a row never starts generation. */
internal data class RunHistoryItem(
    val runId: String,
    val dateLabel: String,
    val metrics: RunMetricsDisplay?,
    val reflectionStatus: String?,
    val reflectionText: String?,
    val energyLabel: String?,
    val isTestData: Boolean,
    val preRunEnergyLabel: String? = null,
    val musicObservationRequested: Boolean = false,
    val musicObservationCount: Int = 0,
    val musicObservationsUnavailable: Boolean = false,
    val lastObservedMedia: String? = null,
    val effort: PostRunEffort? = null,
    val isGpsRun: Boolean = false,
    val energy: ReflectionBranch? = null,
    val reflectionMusicSummary: String? = null,
    val weatherSummary: String? = null,
    /** Frozen at completion; path geometry never infers complete recording by itself. */
    val telemetryCoverage: TelemetryCoverage? = null,
    val preRunEnergy: PreRunEnergy? = null,
    val metricSource: MetricSource? = null,
    val insightEligible: Boolean = true,
    val splitStatus: RunSplitStatus? = null,
    val postRunNote: String? = null
)

internal sealed interface HistoryRouteDisplay {
    data object Loading : HistoryRouteDisplay
    data class Ready(val data: GpsRoutePreviewData) : HistoryRouteDisplay
    data object Failed : HistoryRouteDisplay
}

private enum class HistoryFilter(val label: String) { ALL("All"), GPS("GPS"), SAMPLES("Samples") }

private val HistoryDark = Color(0xFF263029)
private val HistoryLightInk = Color(0xFFF4F5ED)
private val HistoryLightMuted = Color(0xFFCAD0C5)
private val HistoryGold = Color(0xFFD6BC72)

/** A single expanded saved record keeps exact reflection text readable on a phone. */
@Composable
internal fun RunHistoryScreen(
    items: List<RunHistoryItem>,
    onBack: () -> Unit,
    onRetryReflection: (String) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    error: String? = null,
    speakerFactory: (Context) -> ReflectionSpeaker = { AndroidReflectionSpeaker(it) },
    onSelectEffort: (String, PostRunEffort?) -> Unit = { _, _ -> },
    effortSavingRunId: String? = null,
    effortError: Pair<String, String>? = null,
    onSelectEnergy: (String, ReflectionBranch) -> Unit = { _, _ -> },
    energySavingRunId: String? = null,
    energyError: Pair<String, String>? = null,
    routeLoader: (suspend (String) -> GpsRoutePreviewData)? = null,
    speechLifecycle: Lifecycle = LocalLifecycleOwner.current.lifecycle,
    splitLoader: (suspend (String) -> HistorySplitDisplay)? = null,
    onRetrySplits: suspend (String) -> Unit = {}
) {
    val listState = rememberLazyListState()
    // A newly saved run is inserted above the old first row. LazyColumn's stable row keys
    // otherwise preserve that old row as the scroll anchor and hide the new run above it.
    // The saved identity guard leaves initial loading, rotation, and same-run feedback alone.
    val newestRunId = items.firstOrNull()?.runId
    var previousNewestRunId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(newestRunId) {
        if (newestRunId != null) {
            if (previousNewestRunId != null && previousNewestRunId != newestRunId) {
                listState.scrollToItem(0)
            }
            previousNewestRunId = newestRunId
        }
    }
    var expandedRunId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingEffortRunId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingEnergyRunId by rememberSaveable { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }
    var speaker by remember { mutableStateOf<ReflectionSpeaker?>(null) }
    var route by remember { mutableStateOf<Pair<String, HistoryRouteDisplay>?>(null) }
    val currentRouteLoader by rememberUpdatedState(routeLoader)
    var splitDetails by remember { mutableStateOf<Pair<String, HistorySplitDisplay>?>(null) }
    var splitLoadAttempt by remember { mutableStateOf(0) }
    var retrySplitsForRun by remember { mutableStateOf<String?>(null) }
    val currentSplitLoader by rememberUpdatedState(splitLoader)
    val currentSplitRetry by rememberUpdatedState(onRetrySplits)
    val context = LocalContext.current

    fun closeSpeaker() {
        val owned = speaker
        speaker = null
        try { owned?.close() } catch (_: RuntimeException) { /* Saved details remain readable. */ }
    }
    fun stopSpeaker() {
        try { speaker?.stop() } catch (_: RuntimeException) { closeSpeaker() }
    }

    val expandedGpsRun = items.firstOrNull { it.runId == expandedRunId }?.isGpsRun == true
    val expandedSplitStatus = items.firstOrNull { it.runId == expandedRunId }?.splitStatus
    LaunchedEffect(expandedRunId, expandedGpsRun, expandedSplitStatus, splitLoadAttempt) {
        splitDetails = null
        val id = expandedRunId ?: return@LaunchedEffect
        if (!expandedGpsRun) return@LaunchedEffect
        splitDetails = id to HistorySplitDisplay.Loading
        val retryRequested = retrySplitsForRun == id
        retrySplitsForRun = null
        try {
            if (retryRequested) currentSplitRetry(id)
            val display = currentSplitLoader?.invoke(id) ?: if (expandedSplitStatus == null) {
                HistorySplitDisplay.NotRecorded
            } else HistorySplitDisplay.Failed
            coroutineContext.ensureActive()
            splitDetails = id to display
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { splitDetails = id to HistorySplitDisplay.Failed }
    }
    LaunchedEffect(expandedRunId, expandedGpsRun) {
        route = null
        val id = expandedRunId ?: return@LaunchedEffect
        if (!expandedGpsRun) return@LaunchedEffect
        val loader = currentRouteLoader
        if (loader == null) {
            route = id to HistoryRouteDisplay.Failed
            return@LaunchedEffect
        }
        route = id to HistoryRouteDisplay.Loading
        try {
            val data = loader(id)
            coroutineContext.ensureActive()
            route = id to HistoryRouteDisplay.Ready(data)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { route = id to HistoryRouteDisplay.Failed }
    }

    DisposableEffect(speechLifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) stopSpeaker()
        }
        speechLifecycle.addObserver(observer)
        // Read the current speaker on exit, including one created by the last tap before recomposition.
        onDispose {
            speechLifecycle.removeObserver(observer)
            closeSpeaker()
        }
    }
    LaunchedEffect(items, expandedRunId) {
        val openItem = items.firstOrNull { it.runId == expandedRunId }
        if (expandedRunId != null &&
            (openItem?.reflectionStatus != "READY" || openItem?.reflectionText.isNullOrBlank())
        ) {
            stopSpeaker()
        }
    }
    LaunchedEffect(items, editingEffortRunId) {
        if (editingEffortRunId != null && items.none { it.runId == editingEffortRunId }) {
            editingEffortRunId = null
        }
    }
    LaunchedEffect(items, editingEnergyRunId) {
        if (editingEnergyRunId != null &&
            items.firstOrNull { it.runId == editingEnergyRunId }?.reflectionStatus == null
        ) {
            editingEnergyRunId = null
        }
    }

    val visibleItems = when (filter) {
        HistoryFilter.ALL -> items
        HistoryFilter.GPS -> items.filter { it.isGpsRun }
        // Unknown legacy provenance must not be relabelled as a sample run.
        HistoryFilter.SAMPLES -> items.filter { it.metrics?.sourceLabel == "Fixture metrics" }
    }
    val summaryItem = visibleItems.firstOrNull { it.runId == expandedRunId }
        ?: visibleItems.firstOrNull()
    val stateInsight = remember(items, isLoading, error) {
        if (isLoading || error != null) null else runnerStateInsightFor(items)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag(RUN_HISTORY_SCREEN_TAG)
            .background(CompletionSurfaceBottom)
    ) {
        val compactSummary = maxHeight < 680.dp || LocalDensity.current.fontScale > 1.25f
        Image(
            painter = painterResource(R.drawable.history_running_trail),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to HistoryDark.copy(alpha = 0.50f),
            0.34f to Color(0xFFF2F4EB).copy(alpha = 0.14f),
            0.72f to Color(0xFFF4F6EF).copy(alpha = 0.64f),
            1f to Color(0xFFFAFBF6).copy(alpha = 0.96f)
        )))
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = {
                        stopSpeaker()
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 12.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Back to run", fontSize = 12.sp)
                }
                Text(
                    text = "LOG HISTORY",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.8.sp,
                    modifier = Modifier.semantics { heading() }
                )
            }

            if (summaryItem != null) {
                HistoryMetricsPanel(summaryItem,
                    Modifier.padding(horizontal = 20.dp, vertical = 6.dp), compact = compactSummary)
            }
            if (items.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.58f), RoundedCornerShape(24.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    HistoryFilter.entries.forEach { choice ->
                        Box(
                            modifier = Modifier.weight(1f).heightIn(min = 42.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(if (filter == choice) Color.White.copy(alpha = 0.94f) else Color.Transparent)
                                .selectable(
                                    selected = filter == choice,
                                    role = Role.Tab,
                                    onClick = {
                                        if (filter != choice) {
                                            stopSpeaker()
                                            expandedRunId = null
                                            filter = choice
                                        }
                                    }
                                )
                                .testTag("runHistoryFilter:" + choice.name),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(choice.label, fontSize = 12.sp,
                                fontWeight = if (filter == choice) FontWeight.Bold else FontWeight.Medium,
                                color = CompletionStrongInk)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 25.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("YOUR SAVED RUNS", color = HistoryDark, fontSize = 9.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text("${visibleItems.size} ${if (visibleItems.size == 1) "activity" else "activities"}",
                        color = HistoryDark, fontSize = 11.sp)
                }
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().weight(1f).testTag(RUN_HISTORY_LIST_TAG)
            ) {
                if (filter != HistoryFilter.SAMPLES && stateInsight != null) {
                    item(key = "runner-state-insight") { RunnerStateInsightPanel(stateInsight) }
                }
                if (isLoading) {
                    item(key = "loading") {
                        HistoryMessage("Loading your saved runs…")
                    }
                }
                if (error != null) {
                    item(key = "error") {
                        HistoryMessage(error)
                    }
                }
                if (!isLoading && error == null && items.isEmpty()) {
                    item(key = "empty") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.65f), RoundedCornerShape(22.dp))
                                .padding(22.dp)
                        ) {
                            Text(
                                text = "Your first run starts the story.",
                                color = CompletionStrongInk,
                                fontSize = 23.sp,
                                lineHeight = 30.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Complete a run and it will appear here with its saved details and reflection.",
                                color = CompletionMutedInk,
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }
                    }
                }
                if (!isLoading && error == null && items.isNotEmpty() && visibleItems.isEmpty()) {
                    item(key = "empty-filter") {
                        HistoryMessage("No ${if (filter == HistoryFilter.GPS) "GPS" else "sample"} runs saved yet.")
                    }
                }
                items(items = visibleItems, key = { it.runId }) { item ->
                    HistoryRow(
                        item = item,
                        expanded = expandedRunId == item.runId,
                        onToggle = {
                            stopSpeaker()
                            if (expandedRunId == item.runId) {
                                expandedRunId = null
                            } else {
                                expandedRunId = item.runId
                                val exactText = item.reflectionText
                                if (item.reflectionStatus == "READY" && !exactText.isNullOrBlank() &&
                                    speechLifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                    try {
                                        val activeSpeaker = speaker ?: speakerFactory(context).also { speaker = it }
                                        activeSpeaker.speak(exactText)
                                    } catch (_: RuntimeException) {
                                        closeSpeaker()
                                    }
                                }
                            }
                        },
                        onRetryReflection = { onRetryReflection(item.runId) },
                        onEditEffort = { editingEffortRunId = item.runId },
                        effortSaving = effortSavingRunId == item.runId,
                        effortError = effortError?.takeIf { it.first == item.runId }?.second,
                        onEditEnergy = { editingEnergyRunId = item.runId },
                        energySaving = energySavingRunId == item.runId,
                        energyError = energyError?.takeIf { it.first == item.runId }?.second,
                        route = route?.takeIf { it.first == item.runId }?.second,
                        splits = splitDetails?.takeIf { it.first == item.runId }?.second,
                        onRetrySplits = {
                            retrySplitsForRun = item.runId
                            splitLoadAttempt += 1
                        }
                    )
                }
            }
        }
        val editingItem = items.firstOrNull { it.runId == editingEffortRunId }
        if (editingItem != null) {
            EffortChoiceSheet(
                selectedEffort = editingItem.effort,
                enabled = effortSavingRunId != editingItem.runId,
                onDismiss = { editingEffortRunId = null },
                onSelect = { effort ->
                    editingEffortRunId = null
                    onSelectEffort(editingItem.runId, effort)
                }
            )
        }
        val editingEnergyItem = items.firstOrNull { it.runId == editingEnergyRunId }
        if (editingEnergyItem?.reflectionStatus != null) {
            HistoryEnergyChoiceSheet(
                selectedEnergy = editingEnergyItem.energy,
                enabled = energySavingRunId != editingEnergyItem.runId,
                onDismiss = { editingEnergyRunId = null },
                onSelect = { energy ->
                    editingEnergyRunId = null
                    onSelectEnergy(editingEnergyItem.runId, energy)
                }
            )
        }
    }
}

@Composable
private fun HistoryMetricsPanel(
    item: RunHistoryItem,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Column(
        modifier = modifier.fillMaxWidth()
            .background(Color(0xFFF7F8F1).copy(alpha = 0.82f), RoundedCornerShape(18.dp))
            .border(1.dp, Color.White.copy(alpha = 0.58f), RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.metrics?.distance ?: "—",
                    color = HistoryDark,
                    fontFamily = CompletionNumberFont,
                    fontSize = if (compact) 38.sp else 49.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = if (compact) 42.sp else 52.sp,
                    letterSpacing = (-1.5).sp
                )
                Text("DISTANCE", color = CompletionMutedInk, fontSize = 8.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            }
            Column(Modifier.weight(0.85f), horizontalAlignment = Alignment.End) {
                Text(
                    text = item.energyLabel?.let { "ENERGY · ${it.uppercase()}" } ?: "ENERGY NOT RECORDED",
                    color = CompletionStrongInk,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 14.sp,
                    modifier = Modifier.background(Color.White.copy(alpha = 0.40f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                )
                Text(
                    text = item.metrics?.sourceLabel ?: "Metrics unavailable",
                    color = CompletionMutedInk,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }
        }
        Text(item.dateLabel, color = CompletionStrongInk, fontSize = 10.sp,
            fontWeight = FontWeight.Medium, lineHeight = 16.sp,
            modifier = Modifier.padding(top = 4.dp))
        if (!compact) {
            Spacer(Modifier.fillMaxWidth().padding(top = 10.dp).height(1.dp)
                .background(CompletionStrongInk.copy(alpha = 0.13f)))
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HistorySummaryMetric("PACE", item.metrics?.averagePace ?: "—", Modifier.weight(1f))
                HistorySummaryMetric("TIME", item.metrics?.active ?: "—", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HistorySummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, color = HistoryDark, fontFamily = CompletionNumberFont,
            fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.Medium)
        Text(label, color = CompletionMutedInk, fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = 1.3.sp)
    }
}

@Composable
private fun HistoryMessage(text: String) {
    Text(
        text = text,
        color = CompletionStrongInk,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        modifier = Modifier.fillMaxWidth()
            .background(Color.White.copy(alpha = 0.82f), RoundedCornerShape(14.dp))
            .padding(18.dp)
    )
}

@Composable
private fun HistoryRow(
    item: RunHistoryItem,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRetryReflection: () -> Unit,
    onEditEffort: () -> Unit,
    effortSaving: Boolean,
    effortError: String?,
    onEditEnergy: () -> Unit,
    energySaving: Boolean,
    energyError: String?,
    route: HistoryRouteDisplay?,
    splits: HistorySplitDisplay?,
    onRetrySplits: () -> Unit
) {
    val shape = RoundedCornerShape(13.dp)
    val rowInk = if (expanded) HistoryLightInk else CompletionStrongInk
    val rowMuted = if (expanded) HistoryLightMuted else CompletionMutedInk
    val rowAccent = if (expanded) HistoryGold else CompletionGreen
    val reflectionState = when (item.reflectionStatus) {
        "READY" -> "Reflection saved"
        "PENDING" -> "Reflection pending"
        "FAILED" -> "Reflection unavailable"
        else -> "Saved run"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(tween(250))
            .clip(shape)
            .background(if (expanded) HistoryDark.copy(alpha = 0.96f) else Color(0xFFF5F7EE).copy(alpha = 0.87f))
            .border(1.dp, if (expanded) HistoryGold.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.68f), shape)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(RUN_HISTORY_ROW_TAG_PREFIX + item.runId)
                .semantics {
                    stateDescription = "${if (expanded) "Expanded" else "Collapsed"} · $reflectionState"
                }
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (expanded) "Close run details" else "Open run details",
                    onClick = onToggle
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.width(3.dp).height(31.dp)
                    .background(if (item.isGpsRun) rowAccent else rowMuted.copy(alpha = 0.50f), RoundedCornerShape(2.dp)))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.metrics?.distance ?: "—",
                        color = rowInk,
                        fontFamily = CompletionNumberFont,
                        fontSize = if (expanded) 22.sp else 25.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = if (expanded) 26.sp else 29.sp,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "${item.metrics?.active ?: "—"} time · ${item.metrics?.averagePace ?: "—"} pace",
                        color = rowMuted,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
                Column(Modifier.weight(0.80f), horizontalAlignment = Alignment.End) {
                    Text(item.dateLabel, color = rowInk, fontSize = 10.sp,
                        lineHeight = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (expanded) "CLOSE −" else "OPEN +",
                        color = rowMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.7.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 15.dp, top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = (item.metrics?.sourceLabel ?: "Metrics unavailable") +
                        if (!expanded && item.reflectionStatus in listOf("PENDING", "FAILED"))
                            " · $reflectionState" else "",
                    color = rowMuted,
                    fontSize = 9.sp,
                    lineHeight = 13.sp,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                if (expanded) {
                    Text(
                        text = reflectionState,
                        color = rowAccent,
                        fontSize = 9.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(0.85f)
                    )
                }
            }
        }
        if (expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 22.dp)) {
                Text(
                    text = "RUNSTATE",
                    color = rowAccent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.8.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = when {
                        item.reflectionStatus == "READY" && !item.reflectionText.isNullOrBlank() -> item.reflectionText
                        item.reflectionStatus == "PENDING" -> "Your run is saved. Its reflection is still being prepared."
                        item.reflectionStatus == "FAILED" -> "Your run is saved. The reflection couldn’t be prepared."
                        item.reflectionStatus == null -> "No reflection saved for this run."
                        else -> "Reflection unavailable. Your run is saved."
                    },
                    color = rowInk,
                    fontSize = 17.sp,
                    lineHeight = 25.sp,
                    modifier = Modifier
                        .padding(top = 9.dp, bottom = 10.dp)
                        .testTag(RUN_HISTORY_REFLECTION_TAG_PREFIX + item.runId)
                )
                item.weatherSummary?.let { summary ->
                    Text(text = summary, color = rowInk.copy(alpha = 0.8f),
                        fontSize = 12.sp, lineHeight = 19.sp,
                        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
                            .testTag("historyWeather:" + item.runId))
                    RunWeatherCredit(color = rowInk.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 10.dp))
                }
                if (item.musicObservationRequested) {
                    HistoryObservedMusic(item)
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "Music wasn’t recorded for this run.",
                            color = rowMuted,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                        Text(
                            text = "Enable Observe music before a future run to save available song details.",
                            color = rowMuted,
                            fontSize = 11.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
                if (item.isGpsRun) {
                    when (route) {
                        is HistoryRouteDisplay.Ready -> GpsRoutePreview(route.data,
                            Modifier.padding(top = 10.dp, bottom = 12.dp)
                                .background(CompletionSurfaceTop, RoundedCornerShape(12.dp)).padding(12.dp),
                            coverage = item.telemetryCoverage)
                        HistoryRouteDisplay.Failed -> Text(
                            "Recorded GPS path couldn't load. Close and reopen this run to retry.",
                            color = rowMuted, fontSize = 12.sp, lineHeight = 18.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                        HistoryRouteDisplay.Loading, null -> Text(
                            "Loading recorded GPS path…", color = rowMuted,
                            fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
                Text(
                    text = "Active time · ${item.metrics?.active ?: "—"}",
                    color = rowMuted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (item.isGpsRun) HistorySplitsPanel(splits, onRetrySplits)
                if (item.preRunEnergyLabel != null) {
                    Text(
                        text = "Before run · ${item.preRunEnergyLabel}",
                        color = rowInk,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                item.postRunNote?.takeIf { it.isNotBlank() }?.let { note ->
                    Text("YOUR NOTE", color = rowMuted, fontSize = 10.sp,
                        letterSpacing = 1.sp, modifier = Modifier.padding(top = 14.dp))
                    Text(note, color = rowInk, fontSize = 13.sp, lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 4.dp))
                }
                if (item.reflectionStatus != null) {
                    TextButton(
                        onClick = onEditEnergy,
                        enabled = !energySaving,
                        colors = ButtonDefaults.textButtonColors(contentColor = rowInk),
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 10.dp),
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .heightIn(min = 48.dp)
                            .testTag(RUN_HISTORY_ENERGY_TAG_PREFIX + item.runId)
                    ) {
                        Text(
                            text = item.energyLabel?.let { "After run · $it" } ?: "After run +",
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                    }
                    if (energySaving) {
                        Text("Saving Energy…", color = rowMuted, fontSize = 12.sp)
                    }
                    if (energyError != null) {
                        Text(energyError, color = rowInk, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
                TextButton(
                    onClick = onEditEffort,
                    enabled = !effortSaving,
                    colors = ButtonDefaults.textButtonColors(contentColor = rowMuted),
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 10.dp),
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .heightIn(min = 48.dp)
                        .testTag(RUN_HISTORY_EFFORT_TAG_PREFIX + item.runId)
                ) {
                    Text(
                        text = item.effort?.let { "EFFORT · ${it.displayLabel().uppercase()}" } ?: "EFFORT +",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.1.sp
                    )
                }
                if (effortSaving) {
                    Text("Saving Effort…", color = rowMuted, fontSize = 12.sp)
                }
                if (effortError != null) {
                    Text(effortError, color = rowInk, fontSize = 12.sp, lineHeight = 18.sp)
                }
                if (item.isTestData) {
                    Text(
                        text = "FAKE BACKEND · TEST DATA",
                        color = rowAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp,
                        modifier = Modifier.padding(top = 18.dp)
                    )
                }
                if (item.reflectionStatus == "FAILED") {
                    OutlinedButton(
                        onClick = onRetryReflection,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = rowInk),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .heightIn(min = 48.dp)
                            .testTag(RUN_HISTORY_RETRY_TAG_PREFIX + item.runId)
                    ) {
                        Text("Retry reflection")
                    }
                }
            }
        }
    }
}

/** Saved device evidence belongs beside the reply, without suggesting live playback. */
@Composable
private fun HistoryObservedMusic(item: RunHistoryItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 9.dp, bottom = 15.dp)
    ) {
        Spacer(
            Modifier.fillMaxWidth().height(1.dp)
                .background(HistoryGold.copy(alpha = 0.25f))
        )
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // A static mark keeps a saved observation distinct from a player/equalizer.
            Canvas(Modifier.size(16.dp)) {
                val center = size.height / 2f
                val heights = floatArrayOf(0.34f, 0.70f, 1f, 0.55f)
                heights.forEachIndexed { index, fraction ->
                    val x = size.width * (index + 0.5f) / heights.size
                    val halfHeight = size.height * fraction * 0.43f
                    drawLine(
                        color = HistoryGold,
                        start = Offset(x, center - halfHeight),
                        end = Offset(x, center + halfHeight),
                        strokeWidth = 1.6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
            Text(
                text = if (item.musicObservationsUnavailable) "OBSERVED MEDIA · UNAVAILABLE"
                    else "OBSERVED MEDIA · PARTIAL",
                color = HistoryGold,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                lineHeight = 14.sp,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            text = if (item.musicObservationsUnavailable) {
                "Song details couldn't load. Reopen History to try again. Your run is saved."
            } else if (item.musicObservationCount == 0) {
                "No media session was observed. Music remains unknown."
            } else {
                "Last observed · ${item.lastObservedMedia ?: "Unknown media"}"
            },
            color = HistoryLightInk,
            fontSize = 13.sp,
            fontWeight = if (item.musicObservationCount > 0) FontWeight.Medium else FontWeight.Normal,
            lineHeight = 20.sp,
            modifier = Modifier.padding(top = 7.dp)
        )
        if (item.musicObservationCount > 0) {
            Text(
                text = "${item.musicObservationCount} listening updates saved. This may not be the full soundtrack.",
                color = HistoryLightMuted,
                fontSize = 11.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        item.reflectionMusicSummary?.let { summary ->
            Text(
                text = summary,
                color = HistoryLightMuted,
                fontSize = 11.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

/** Updating historical Energy reports feedback only; opening or choosing never reads the reply. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryEnergyChoiceSheet(
    selectedEnergy: ReflectionBranch?,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onSelect: (ReflectionBranch) -> Unit
) {
    val choices = listOf(
        ReflectionBranch.SPENT to "Spent",
        ReflectionBranch.FEELING_GOOD to "Feeling Good",
        ReflectionBranch.POWERED_UP to "Powered Up"
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CompletionSurfaceTop,
        modifier = Modifier.testTag(RUN_HISTORY_ENERGY_SHEET_TAG)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "How did you feel after this run?",
                color = CompletionStrongInk,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            choices.forEach { (branch, label) ->
                OutlinedButton(
                    onClick = { onSelect(branch) },
                    enabled = enabled,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CompletionStrongInk),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(RUN_HISTORY_ENERGY_CHOICE_TAG_PREFIX + branch.name)
                        .semantics { selected = branch == selectedEnergy }
                ) {
                    Text(
                        text = label,
                        fontWeight = if (branch == selectedEnergy) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
