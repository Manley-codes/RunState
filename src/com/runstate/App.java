package com.runstate;

import java.util.List;

public class App {

    public static void main(String[] args) {

        Runner runner1 = new Runner(
                100,
                "runnerMan",
                "Jay",
                "Smith",
                "Houston",
                "TX",
                "jay@example.com"
        );

        // Load all previously saved runs from the database.
        // loadRun() re-evaluates PR flags without replaying announcements.
        List<Run> savedRuns = RunStorage.loadRuns(runner1);
        for (Run run : savedRuns) {
            runner1.loadRun(run);
        }

        // Gives runner1 to RunConsole so its menu can manage this runner's data.
        RunConsole runConsole = new RunConsole(runner1);
        // Starts the menu and keeps main active until the runner selects Exit.
        runConsole.start();

    }
}
