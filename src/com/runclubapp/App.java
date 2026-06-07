package com.runclubapp;

public class App {

    public static void main(String[] args) {

        Runner runner1 = new Runner(
                100,
                "runnerJay",
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
                "miles",
                50,
                "Memorial Park Loop",
                "Houston, TX",
                true,
                "Houston Run Club"
        );

        run1.displayRunInfo();
    }
}
