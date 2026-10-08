package com.runstate.mobile.reflection

import com.runstate.mobile.BuildConfig

/** Keeps the emulator-only bridge and its UI out of release behavior. */
internal fun reflectionBridgeEnabledForBuild(): Boolean = BuildConfig.DEBUG
