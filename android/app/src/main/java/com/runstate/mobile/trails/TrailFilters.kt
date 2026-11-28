package com.runstate.mobile.trails

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

internal enum class TrailElevation(val label: String) {
    ANY("Any elevation"), LOW("Under 300 ft"), MEDIUM("300–1,000 ft"), HIGH("Over 1,000 ft")
}

internal data class TrailFilters(
    val difficulty: TrailDifficulty? = null,
    val surface: TrailSurface? = null,
    val elevation: TrailElevation = TrailElevation.ANY
) {
    val count: Int get() = (if (difficulty != null) 1 else 0) +
        (if (surface != null) 1 else 0) + (if (elevation != TrailElevation.ANY) 1 else 0)

    fun matches(trail: TrailPlace): Boolean {
        if (difficulty != null && difficulty != trail.difficulty) return false
        if (surface != null && surface !in trail.surfaces) return false
        if (elevation == TrailElevation.ANY) return true
        val feet = trail.elevationGainMeters?.takeIf { it.isFinite() && it >= 0 }?.div(0.3048)
            ?: return false
        return when (elevation) {
            TrailElevation.ANY -> true
            TrailElevation.LOW -> feet < 300
            TrailElevation.MEDIUM -> feet in 300.0..1000.0
            TrailElevation.HIGH -> feet > 1000
        }
    }
}

internal fun searchTrails(trails: List<TrailPlace>, query: String, filters: TrailFilters): List<TrailPlace> {
    val words = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotBlank() }
    return trails.filter { trail ->
        filters.matches(trail) && words.all { word ->
            listOf(trail.name, trail.subtitle, trail.city, trail.state).any { it.lowercase(Locale.ROOT).contains(word) }
        }
    }.distinctBy { it.id }
}

/** A supplied entrance may be used directly; a segment midpoint is never a driving destination. */
internal fun trailNavigationUri(trail: TrailPlace): String {
    val entrance = trail.trailhead?.takeIf { it.valid }
    val query = if (entrance != null) "${entrance.latitude},${entrance.longitude} (${trail.name})"
        else listOf(trail.name, trail.city, trail.state, "USA").filter { it.isNotBlank() }.joinToString(", ")
    // A source segment's center is only the search-area bias, never the requested destination.
    val searchArea = if (entrance == null && trail.center.valid) "${trail.center.latitude},${trail.center.longitude}" else "0,0"
    return "geo:$searchArea?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8.name())
}
