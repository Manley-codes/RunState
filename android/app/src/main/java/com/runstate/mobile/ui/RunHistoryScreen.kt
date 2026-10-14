package com.runstate.mobile.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import com.runstate.mobile.R
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
    fun speak(text: String)
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
    fun initialize(onReady: (Boolean) -> Unit, onDone: (String) -> Unit, onError: (String) -> Unit)
    fun requestAudioFocus(utteranceId: String, onFocusLost: (String) -> Unit): Boolean
    fun speak(utteranceId: String, text: String, rate: Float): Boolean
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

/** Android owns the voice; the saved text remains the source for both reading and speech. */
internal class AndroidReflectionSpeaker(
    context: Context,
    private val platform: ReflectionSpeechPlatform = AndroidReflectionSpeechPlatform(context),
    private val scheduleTimeout: ((Runnable, Long) -> Unit)? = null,
    private val cancelTimeout: ((Runnable) -> Unit)? = null
) : ReflectionSpeaker {
    private enum class SpeechPhase { WAITING_FOR_ENGINE, SPEAKING, INSTRUMENTAL }

    private data class SpeechRequest(
        val id: String,
        val text: String,
        val rate: Float,
        val onFinished: (() -> Unit)?,
        val canPlayInstrumental: (() -> Boolean)?,
        var phase: SpeechPhase = SpeechPhase.WAITING_FOR_ENGINE
    )

    private val mainHandler = Handler(Looper.getMainLooper())
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
                        if (initialized) speakPending() else request?.id?.let(::finish)
                    }
                } },
                onDone = { id -> onMain { finishSpeech(id) } },
                onError = { id -> onMain {
                    if (request?.id == id && request?.phase != SpeechPhase.INSTRUMENTAL) stop()
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

    override fun speakWithInstrumental(
        text: String,
        canPlayInstrumental: () -> Boolean,
        onFinished: () -> Unit
    ) {
        submit(text, rate = 0.94f, onFinished = onFinished, canPlayInstrumental = canPlayInstrumental)
    }

    override fun speakReceipt(text: String, onFinished: () -> Unit) {
        submit(text, rate = 1.0f, onFinished = onFinished, canPlayInstrumental = null)
    }

    private fun submit(
        text: String,
        rate: Float,
        onFinished: (() -> Unit)?,
        canPlayInstrumental: (() -> Boolean)?
    ) {
        stop()
        if (closed || initializationFailed || text.isBlank() ||
            text.length > TextToSpeech.getMaxSpeechInputLength()) {
            onFinished?.invoke()
            return
        }
        val next = SpeechRequest(UUID.randomUUID().toString(), text, rate, onFinished, canPlayInstrumental)
        request = next
        // Cold engine startup has its own deadline; it must not consume audible playback time.
        if (initialized) speakPending() else armTimeout(next.id, 15_000L)
    }

    override fun stop() {
        val interrupted = request
        request = null
        clearTimeout()
        try {
            platform.stop()
        } catch (_: RuntimeException) {
            // A disconnected engine must not disturb History navigation.
        }
        stopInstrumental()
        releaseFocus()
        interrupted?.onFinished?.invoke()
    }

    override fun close() {
        if (closed) return
        closed = true
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
                    if (request?.id == id && request?.phase == SpeechPhase.SPEAKING) stop()
                } }) {
                finish(current.id)
                return
            }
            if (request?.id != current.id || closed) return
            // The bounded playback budget starts only once a ready engine can queue this text.
            val maximum = if (current.onFinished != null && current.canPlayInstrumental == null)
                120_000L else 600_000L
            armTimeout(current.id, (current.text.length * 100L + 15_000L).coerceIn(30_000L, maximum))
            if (!platform.speak(current.id, current.text, current.rate) && request?.id == current.id) stop()
        } catch (_: RuntimeException) {
            if (request?.id == current.id) stop()
            // Speech failure is independent of the saved reflection.
        }
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
        // A true TTS completion is the only path into music. Old voice callbacks cannot
        // end this phase, and a separate deadline also bounds asynchronous preparation.
        current.phase = SpeechPhase.INSTRUMENTAL
        clearTimeout()
        releaseFocus()
        armTimeout(current.id, 15_000L)
        try {
            val accepted = platform.playInstrumental(
                current.id,
                onDone = { id -> onMain {
                    if (request?.id == id && request?.phase == SpeechPhase.INSTRUMENTAL) finish(id)
                } },
                onError = { id -> onMain {
                    if (request?.id == id && request?.phase == SpeechPhase.INSTRUMENTAL) stop()
                } },
                onFocusLost = { id -> onMain {
                    if (request?.id == id && request?.phase == SpeechPhase.INSTRUMENTAL) stop()
                } }
            )
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
            override fun run() { if (speechTimeout === this && request?.id == id) stop() }
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
        stopInstrumental()
        releaseFocus()
        finished.onFinished?.invoke()
    }

    private fun stopInstrumental() {
        try { platform.stopInstrumental() }
        catch (_: RuntimeException) { /* Saved text remains available without music playback. */ }
    }

    private fun releaseFocus() {
        try { platform.releaseAudioFocus() }
        catch (_: RuntimeException) { /* Saved text stays readable if the audio service disconnects. */ }
    }
}

internal class AndroidReflectionSpeechPlatform(context: Context) : ReflectionSpeechPlatform {
    private class InstrumentalPlayback(
        val id: String,
        val player: MediaPlayer,
        var focus: AudioFocusRequest? = null,
        var released: Boolean = false
    )

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val speechAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private var engine: TextToSpeech? = null
    private var focusRequest: AudioFocusRequest? = null
    private var instrumental: InstrumentalPlayback? = null
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

    override fun speak(utteranceId: String, text: String, rate: Float): Boolean {
        val activeEngine = engine ?: return false
        return activeEngine.setSpeechRate(rate) == TextToSpeech.SUCCESS &&
            activeEngine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId) == TextToSpeech.SUCCESS
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

    override fun stop() { engine?.stop() }
    override fun releaseAudioFocus() {
        val owned = focusRequest ?: return
        focusRequest = null
        audioManager?.abandonAudioFocusRequest(owned)
    }
    override fun close() {
        closed = true
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
    val lastObservedMedia: String? = null,
    val effort: PostRunEffort? = null,
    val isGpsRun: Boolean = false,
    val energy: ReflectionBranch? = null,
    val reflectionMusicSummary: String? = null
)

internal sealed interface HistoryRouteDisplay {
    data object Loading : HistoryRouteDisplay
    data class Ready(val data: GpsRoutePreviewData) : HistoryRouteDisplay
    data object Failed : HistoryRouteDisplay
}

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
    speechLifecycle: Lifecycle = LocalLifecycleOwner.current.lifecycle
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
    var speaker by remember { mutableStateOf<ReflectionSpeaker?>(null) }
    var route by remember { mutableStateOf<Pair<String, HistoryRouteDisplay>?>(null) }
    val currentRouteLoader by rememberUpdatedState(routeLoader)
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(RUN_HISTORY_SCREEN_TAG)
            .background(Brush.verticalGradient(listOf(CompletionSurfaceTop, CompletionSurfaceBottom)))
    ) {
        CompletionAtmosphere(Modifier.fillMaxSize())
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                TextButton(
                    onClick = {
                        stopSpeaker()
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = CompletionStrongInk),
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 12.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Back to run")
                }
                Text(
                    text = "LOG HISTORY",
                    color = CompletionStrongInk,
                    fontSize = 28.sp,
                    fontFamily = CompletionNumberFont,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.1.sp,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = "Your saved runs",
                    color = CompletionMutedInk,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 24.dp, top = 10.dp, end = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().weight(1f).testTag(RUN_HISTORY_LIST_TAG)
            ) {
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
                items(items = items, key = { it.runId }) { item ->
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
                        route = route?.takeIf { it.first == item.runId }?.second
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
private fun HistoryMessage(text: String) {
    Text(
        text = text,
        color = CompletionMutedInk,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)
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
    route: HistoryRouteDisplay?
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = if (expanded) 0.88f else 0.62f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(RUN_HISTORY_ROW_TAG_PREFIX + item.runId)
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (expanded) "Close run details" else "Open run details",
                    onClick = onToggle
                )
                .padding(20.dp)
        ) {
            Text(
                text = item.dateLabel,
                color = CompletionMutedInk,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 18.sp
            )
            Text(
                text = item.metrics?.distance ?: "—",
                color = CompletionStrongInk,
                fontFamily = CompletionNumberFont,
                fontSize = 38.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 44.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = "${item.metrics?.elapsed ?: "—"} time · ${item.metrics?.averagePace ?: "—"} pace",
                color = CompletionInk,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
            Text(
                text = item.metrics?.sourceLabel ?: "Metrics unavailable",
                color = CompletionMutedInk,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (item.reflectionStatus) {
                        "READY" -> "Reflection saved"
                        "PENDING" -> "Reflection pending"
                        "FAILED" -> "Reflection unavailable"
                        else -> "Saved run"
                    },
                    color = CompletionGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f).padding(end = 10.dp)
                )
                Text(
                    text = if (expanded) "CLOSE −" else "OPEN +",
                    color = CompletionMutedInk,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.7.sp
                )
            }
        }
        if (expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 22.dp)) {
                if (item.isGpsRun) {
                    when (route) {
                        is HistoryRouteDisplay.Ready -> GpsRoutePreview(route.data,
                            Modifier.padding(top = 10.dp, bottom = 12.dp))
                        HistoryRouteDisplay.Failed -> Text(
                            "Recorded GPS path couldn't load. Close and reopen this run to retry.",
                            color = CompletionMutedInk, fontSize = 12.sp, lineHeight = 18.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                        HistoryRouteDisplay.Loading, null -> Text(
                            "Loading recorded GPS path…", color = CompletionMutedInk,
                            fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
                Text(
                    text = "Active time · ${item.metrics?.active ?: "—"}",
                    color = CompletionMutedInk,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (item.preRunEnergyLabel != null) {
                    Text(
                        text = "Before run · ${item.preRunEnergyLabel}",
                        color = CompletionStrongInk,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                if (item.reflectionStatus != null) {
                    TextButton(
                        onClick = onEditEnergy,
                        enabled = !energySaving,
                        colors = ButtonDefaults.textButtonColors(contentColor = CompletionStrongInk),
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
                        Text("Saving Energy…", color = CompletionMutedInk, fontSize = 12.sp)
                    }
                    if (energyError != null) {
                        Text(energyError, color = CompletionStrongInk, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
                TextButton(
                    onClick = onEditEffort,
                    enabled = !effortSaving,
                    colors = ButtonDefaults.textButtonColors(contentColor = CompletionMutedInk),
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
                    Text("Saving Effort…", color = CompletionMutedInk, fontSize = 12.sp)
                }
                if (effortError != null) {
                    Text(effortError, color = CompletionStrongInk, fontSize = 12.sp, lineHeight = 18.sp)
                }
                if (item.musicObservationRequested) {
                    Text(
                        text = "OBSERVED MEDIA · PARTIAL",
                        color = CompletionGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        text = if (item.musicObservationCount == 0) {
                            "No media session was observed. Music remains unknown."
                        } else {
                            "Last observed · ${item.lastObservedMedia ?: "Unknown media"}"
                        },
                        color = CompletionStrongInk,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                    if (item.musicObservationCount > 0) {
                        Text(
                            text = "${item.musicObservationCount} media-session updates saved. This may not be the full soundtrack.",
                            color = CompletionMutedInk,
                            fontSize = 11.sp,
                            lineHeight = 17.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    item.reflectionMusicSummary?.let { summary ->
                        Text(
                            text = summary,
                            color = CompletionMutedInk,
                            fontSize = 11.sp,
                            lineHeight = 17.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
                if (item.isTestData) {
                    Text(
                        text = "FAKE BACKEND · TEST DATA",
                        color = CompletionGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp,
                        modifier = Modifier.padding(top = 18.dp)
                    )
                }
                Text(
                    text = when {
                        item.reflectionStatus == "READY" && !item.reflectionText.isNullOrBlank() -> item.reflectionText
                        item.reflectionStatus == "PENDING" -> "Your run is saved. Its reflection is still being prepared."
                        item.reflectionStatus == "FAILED" -> "Your run is saved. The reflection couldn’t be prepared."
                        item.reflectionStatus == null -> "No reflection saved for this run."
                        else -> "Reflection unavailable. Your run is saved."
                    },
                    color = CompletionStrongInk,
                    fontSize = 19.sp,
                    lineHeight = 28.sp,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .testTag(RUN_HISTORY_REFLECTION_TAG_PREFIX + item.runId)
                )
                if (item.reflectionStatus == "FAILED") {
                    OutlinedButton(
                        onClick = onRetryReflection,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CompletionStrongInk),
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
