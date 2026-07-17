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
        List<Run> savedRuns;
        try {
            savedRuns = RunStorage.loadRuns(runner1);
        } catch (RunStorageException e) {
            // History could not be loaded — either the database was unreachable or a
            // stored row is malformed. Per the plan we do NOT continue with an empty or
            // partial history — that would silently look like "no runs yet" and corrupt
            // PRs and RunStyle. Report the problem and exit before the opening prompt or
            // menu ever appears. No existing history is modified.
            System.out.println("RunState could not load your saved run history.");
            System.out.println("Check the database connection or stored run data, then restart RunState.");
            System.out.println();
            System.out.println("Details: " + e.getCause().getMessage());
            return;
        }

        for (Run run : savedRuns) {
            runner1.loadRun(run);
        }

        // Gives runner1 to RunConsole so its menu can manage this runner's data.
        RunConsole runConsole = new RunConsole(runner1);
        // Starts the menu and keeps main active until the runner selects Exit.
        runConsole.start();

    }
}
