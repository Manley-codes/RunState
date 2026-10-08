package com.runstate.mobile.reflection

import org.junit.Assert.assertTrue
import org.junit.Test

class ReflectionBridgeModeTest {
    @Test fun debugBuildEnablesTheEmulatorBridge() {
        assertTrue(reflectionBridgeEnabledForBuild())
    }
}
