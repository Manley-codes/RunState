package com.runstate.mobile.ui

import com.runstate.mobile.settings.normalizeRunnerName

/** Uses the phone's local hour and a local display name; no account or network lookup. */
internal fun startGreeting(hour: Int, runnerName: String?, ownerToolsEnabled: Boolean): String {
    require(hour in 0..23)
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    // The private preview can retain Manley only until a name is saved. Public builds never use it.
    val name = runnerName?.let(::normalizeRunnerName) ?: if (ownerToolsEnabled) "Manley" else ""
    return if (name.isEmpty()) greeting else "$greeting, $name"
}
