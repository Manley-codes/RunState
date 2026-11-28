package com.runstate.mobile.trails

import kotlin.math.*

internal data class TrailCoordinate(val latitude: Double, val longitude: Double) {
    val valid: Boolean get() = latitude.isFinite() && longitude.isFinite() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0
}

internal enum class TrailDifficulty(val label: String) {
    EASY("Easy"), MODERATE("Moderate"), DIFFICULT("Difficult")
}

internal enum class TrailSurface(val label: String) {
    PAVED("Paved"), GRAVEL("Gravel"), DIRT("Dirt"), BOARDWALK("Boardwalk"), MIXED("Mixed")
}

internal data class TrailPhoto(
    val resourceName: String? = null,
    val url: String? = null,
    val credit: String = "",
    val sourceUrl: String = "",
    val license: String = "",
    val licenseUrl: String = ""
)

/** Discovery evidence is separate from a runner's recorded route and never writes run history. */
internal data class TrailPlace(
    val id: String,
    val name: String,
    val subtitle: String = "",
    val city: String = "",
    val state: String = "",
    val center: TrailCoordinate,
    val trailhead: TrailCoordinate? = null,
    val website: String = "",
    val photo: TrailPhoto? = null,
    val routeLines: List<List<TrailCoordinate>> = emptyList(),
    val lengthMeters: Double? = null,
    val elevationGainMeters: Double? = null,
    val difficulty: TrailDifficulty? = null,
    val surfaces: Set<TrailSurface> = emptySet(),
    val sourceName: String = "",
    val sourceUrl: String = "",
    val isSegment: Boolean = false
)

internal data class TrailCity(val name: String, val state: String, val coordinate: TrailCoordinate) {
    val label: String get() = "$name, $state"
}

internal data class TrailDiscoveryResult(
    val trails: List<TrailPlace>,
    val fromCache: Boolean = false,
    val networkUnavailable: Boolean = false,
    val moreAvailable: Boolean = false
)

internal fun trailDistanceMeters(a: TrailCoordinate, b: TrailCoordinate): Double {
    val radians = Math.PI / 180.0
    val lat = (b.latitude - a.latitude) * radians
    val lon = (b.longitude - a.longitude) * radians
    val h = sin(lat / 2).pow(2) + cos(a.latitude * radians) * cos(b.latitude * radians) * sin(lon / 2).pow(2)
    return 6_371_008.8 * 2 * atan2(sqrt(h.coerceIn(0.0, 1.0)), sqrt((1 - h).coerceIn(0.0, 1.0)))
}
