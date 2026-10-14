package com.runstate.mobile.reflection

import com.runstate.mobile.BuildConfig

/** Local HTTP/USB routing is development-only; hosted access requires explicit saved settings. */
internal fun reflectionBridgeEnabledForBuild(): Boolean = BuildConfig.DEBUG
