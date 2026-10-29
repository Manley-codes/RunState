package com.runstate.mobile.ui

import com.runstate.mobile.data.local.PreRunEnergy
import com.runstate.mobile.data.local.ReflectionBranch
import com.runstate.mobile.music.SongPassageRequest

/** Presentation values only; never admitted to the run coordinator or written to Room. */
internal object MusicReflectionDemoData {
    val preRunEnergy = PreRunEnergy.HIGH
    val metrics = RunMetricsDisplay(
        elapsed = "00:30:00", active = "00:28:00", distance = "2.75 mi",
        averagePace = "10:11 /mi", sourceLabel = "", gpsDistanceAvailable = true,
        currentMilePace = "10:04 /mi"
    )
    const val trackUri = "spotify:track:6KgBpzTuTRPebChN0VTyzV"
    const val facts = "Run complete. 2.75 miles. Active time, 28 minutes. Average pace, 10 minutes, 11 seconds per mile."
    const val approvedReflection = "You started high and finished powered up, with twenty-eight active minutes between those two answers. Go ahead, hair toss. You don't need me to name this feeling. Your hype friend has it covered."

    // The accepted take stays verbatim for its matching Energy pair. Other interactive
    // choices must not silently tell a visitor that they chose Powered Up.
    fun reflectionText(energy: ReflectionBranch): String = when (energy) {
        ReflectionBranch.POWERED_UP -> approvedReflection
        ReflectionBranch.FEELING_GOOD -> approvedReflection.replace("finished powered up", "finished feeling good")
        ReflectionBranch.SPENT -> approvedReflection.replace("finished powered up", "finished gassed out")
        ReflectionBranch.NO_SELECTION -> "You started high and put in twenty-eight active minutes. " +
            "Go ahead, hair toss. You don't need me to name this feeling. Your hype friend has it covered."
    }

    fun passage(durationMillis: Long = 159_735L) = SongPassageRequest(
        trackTitle = "Good as Hell", artistName = "Lizzo", startPositionMillis = 0,
        durationMillis = 10_000, spotifyMediaId = trackUri,
        allowTrackSwitch = true, observedDurationMillis = durationMillis
    )
}
