package com.runstate.mobile.reflection

import org.junit.Assert.assertFalse
import org.junit.Test

class ReflectionBridgeModeTest {
    @Test fun releaseBuildDisablesTheEmulatorBridge() {
        assertFalse(reflectionBridgeEnabledForBuild())
    }
}
