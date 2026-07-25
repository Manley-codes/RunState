package com.runstate;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RunAgentTest {

    @Test
    void formatPaceCarriesRoundedSixtySecondsIntoNextMinute() {
        // A pace of 7 + 59.6/60 minutes would previously round the seconds to 60,
        // producing the invalid string "7:60". The fix converts total seconds first.
        double pace = 7 + 59.6 / 60.0;
        assertEquals("8:00", RunAgent.formatPace(pace));
    }

    @Test
    void formatPaceKeepsOrdinarySecondsFormatting() {
        // 8.4 minutes per mile = 8 minutes + 0.4 * 60 = 24 seconds → "8:24"
        assertEquals("8:24", RunAgent.formatPace(8.4));
    }
}
