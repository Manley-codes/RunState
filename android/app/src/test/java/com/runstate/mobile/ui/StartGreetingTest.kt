package com.runstate.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StartGreetingTest {
    @Test fun greetingChangesAtLocalMorningNoonAndEveningBoundaries() {
        listOf(0, 4, 17, 23).forEach {
            assertEquals("Good evening, Alex", startGreeting(it, "Alex", false))
        }
        listOf(5, 11).forEach {
            assertEquals("Good morning, Alex", startGreeting(it, "Alex", false))
        }
        listOf(12, 16).forEach {
            assertEquals("Good afternoon, Alex", startGreeting(it, "Alex", false))
        }
    }

    @Test fun publicUsersGetTheirOwnNameOrAGenericGreeting() {
        assertEquals("Good morning, María O’Neill", startGreeting(8, "  María\t O’Neill ", false))
        assertEquals("Good morning", startGreeting(8, null, false))
        assertEquals("Good morning", startGreeting(8, "", false))
        assertEquals("Good morning", startGreeting(8, "  ", false))
    }

    @Test fun savedNameAndExplicitClearingOverrideThePrivatePreviewName() {
        assertEquals("Good afternoon, Manley", startGreeting(12, null, true))
        assertEquals("Good afternoon, Morgan", startGreeting(12, "Morgan", true))
        assertEquals("Good afternoon", startGreeting(12, "", true))
    }
}
