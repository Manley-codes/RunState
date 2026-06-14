package com.runclubapp;

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

        Run run1 = new Run(
                1,
                runner1,
                "06/05/2026",
                "6:00 PM",
                "6:50 PM",
                5.0,
                DistanceUnit.MILES,
                50,
                "Memorial Park Loop",
                "Houston, TX",
                RunFeeling.NORMAL,
                true,
                "Houston Run Club"
        );

       // Create a second completed run with different details.
        Run run2 = new Run(
                2,
                runner1,
                "06/03/2026",
                "7:00 AM",
                "7:36 AM",
                3.0,
                DistanceUnit.MILES,
                36,
                "Buffalo Bayou Trail",
                "Houston, TX",
                RunFeeling.DIFFICULT,
                false,
                "None"
        );

        // Create a third completed run with different details.
        Run run3 = new Run(
                3,
                runner1,
                "06/06/2026",
                "7:00 AM",
                "7:36 AM",
                6.0,
                DistanceUnit.MILES,
                36,
                "Buffalo Bayou Trail",
                "Houston, TX",
                RunFeeling.GREAT,
                false,
                "None"
        );


        // Store this completed run in runner1's run history.
        runner1.addRun(run1);
        runner1.addRun(run2);
        runner1.addRun(run3);

        // Display all runs stored for this runner.
        runner1.displayRunHistory();

        System.out.println();

        // Display the runner's current personal records.
        runner1.displayPersonalRecords();

        System.out.println();

       // Display a summary of how the runner felt across all runs.
        runner1.displayRunFeelingSummary();

        System.out.println();

        // Display the average pace for each run feeling.
        runner1.displayAveragePaceByFeeling();


    }
}
