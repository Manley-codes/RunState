package com.runclubapp;

import java.time.LocalDate;

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
                LocalDate.of(2026, 6, 5),
                "6:00 PM",
                "6:50 PM",
                5.0,
                DistanceUnit.MILES,
                50,
                "Memorial Park Loop",
                "Houston, TX",
                EnergyLevel.LOW,
                EnergyLevel.LOW
        );

       // Create a second completed run with different details.
        Run run2 = new Run(
                2,
                runner1,
                LocalDate.of(2026, 6, 3),
                "7:00 AM",
                "7:36 AM",
                3.0,
                DistanceUnit.MILES,
                36,
                "Buffalo Bayou Trail",
                "Houston, TX",
                EnergyLevel.MODERATE,
                null
        );

        // Create a third completed run with different details.
        Run run3 = new Run(
                3,
                runner1,
                LocalDate.of(2026, 6, 6),
                "7:00 AM",
                "7:36 AM",
                6.0,
                DistanceUnit.MILES,
                36,
                "Buffalo Bayou Trail",
                "Houston, TX",
                EnergyLevel.HIGH,
                EnergyLevel.HIGH
        );

        // Create a fourth run with only a post-run energy answer.
        Run run4 = new Run(
                4,
                runner1,
                LocalDate.of(2026, 6, 8),
                "6:30 AM",
                "7:10 AM",
                4.0,
                DistanceUnit.MILES,
                40,
                "White Oak Bayou Trail",
                "Houston, TX",
                null,
                EnergyLevel.MODERATE
        );

        // Create a fifth run with both optional energy answers skipped.
        Run run5 = new Run(
                5,
                runner1,
                LocalDate.of(2026, 6, 10),
                "6:15 AM",
                "6:45 AM",
                2.5,
                DistanceUnit.MILES,
                30,
                "Neighborhood Route",
                "Houston, TX",
                null,
                null
        );

        // Store this completed run in runner1's run history.
        runner1.addRun(run1);
        runner1.addRun(run2);
        runner1.addRun(run3);
        runner1.addRun(run4);
        runner1.addRun(run5);

        // Display all runs stored for this runner.
        runner1.displayRunHistory();

        System.out.println();

        // Display the runner's current personal records.
        runner1.displayPersonalRecords();

    }
}
