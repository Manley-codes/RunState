package com.runclubapp;

// Represents a date without a time, such as 2026-06-19.
import java.time.LocalDate;

// Thrown when text cannot be converted into a valid LocalDate.
import java.time.format.DateTimeParseException;
import java.util.Scanner;

// Handles console input for logging runs and selecting runner actions.
public class RunConsole {

    // The runner whose history and personal records this console manages.
    private final Runner runner;

    // Shared scanner used by every input method in this class.
    private final Scanner scanner;

    // Holds pre-run energy from the opening screen until a run is logged.
    private EnergyLevel pendingPreRunEnergy;

    // Connects the console to one runner and prepares keyboard input.
    public RunConsole(Runner runner) {
        this.runner = runner;
        this.scanner = new Scanner(System.in);
    }

    // Repeats the main menu until option 4 changes running to false.
    public void start() {
        boolean running = true;
        System.out.println("RunState");

        showOpeningPrompt();

        while (running) {
            // Prints the menu, then stores a validated choice from 1 through 4.
            displayMenu();
            int choice = readWholeNumber("Choose an option: ", 1, 4);
            System.out.println();
            switch (choice) {
                case 1:
                    // Starts the completed-run logging questions.
                    logRun();
                    break;
                case 2:
                    // Displays every run currently stored for this runner.
                    runner.displayRunHistory();
                    break;
                case 3:
                    // Displays this runner's current distance and pace records.
                    runner.displayPersonalRecords();
                    break;
                case 4:
                    // Ends the menu loop after this switch finishes.
                    running = false;
                    System.out.println("See you after your next run.");
                    break;
                default:
                    // readWholeNumber prevents unsupported choices.
                    break;
            }
        }
    }

    // Prints the four choices used by the main menu loop.
    private void displayMenu() {
        System.out.println();
        System.out.println("1. Log Run");
        System.out.println("2. View Run History");
        System.out.println("3. View Personal Records");
        System.out.println("4. Exit");
    }

    private void logRun() {
        System.out.println("Log Run");
        LocalDate date = readRunDate();
        DistanceUnit distanceUnit = readDistanceUnit();
        double distance = readPositiveNumber("Distance: ");
        double duration = readPositiveNumber("Duration in minutes: ");
        String routeName = readOptionalText("Route name (optional): ");

        EnergyLevel preRunEnergy;
        if (pendingPreRunEnergy != null) {
            // Use the opening screen answer and clear it so it doesn't carry to a second run.
            preRunEnergy = pendingPreRunEnergy;
            pendingPreRunEnergy = null;
        } else {
            // Runner skipped the opening prompt — ask for pre-run energy now.
            preRunEnergy = readEnergyLevel(true);
        }

        // Post-run energy is null here because the runner hasn't finished yet.
        // The constructor accepts null for optional fields, so this is valid.
        Run run = new Run(
                runner.getRunCount() + 1,
                runner,
                date,
                null,
                null,
                distance,
                distanceUnit,
                duration,
                routeName,
                null,
                preRunEnergy,
                null
        );

        // Adding the run now sets PR flags before we display the summary.
        runner.addRun(run);

        // The runner sees their metrics and any PR results before answering
        // how they feel.
        System.out.println();
        System.out.println(run.getRunSummary());
        System.out.println();

        // Now that the runner has seen their results, ask post-run energy.
        EnergyLevel postRunEnergy = readEnergyLevel(false);

        // Store the answer directly on the run object using the setter we just added.
        run.setPostRunEnergy(postRunEnergy);

        System.out.println();
        System.out.println(buildRunResponse(run));
    }

    // Greets the runner and captures pre-run energy before the main menu appears.
    private void showOpeningPrompt() {
        System.out.println("How are we starting today?");
        System.out.println();
        System.out.println("1. I'm Here");
        System.out.println("2. Ready-ish");
        System.out.println("3. Let's Go!");
        System.out.println("4. Not running today");
        System.out.println();
        int choice = readWholeNumber("Choose: ", 1, 4);

        switch (choice) {
            case 1: pendingPreRunEnergy = EnergyLevel.LOW;      break;
            case 2: pendingPreRunEnergy = EnergyLevel.MODERATE; break;
            case 3: pendingPreRunEnergy = EnergyLevel.HIGH;     break;
            // Choice 4 leaves pendingPreRunEnergy null — runner isn't going out today.
        }
    }


    // Builds a short personal response based on what happened in this run.
    private String buildRunResponse(Run run) {
        EnergyLevel pre = run.getPreRunEnergy();
        EnergyLevel post = run.getPostRunEnergy();
        boolean hasPR = run.isLongestDistanceRecord() || run.isFastestAveragePaceRecord();

        String mainMessage;

        if (hasPR) {
            // PR runs — tone is shaped by how the runner finished.
            if (post == EnergyLevel.LOW) {
                mainMessage = "You really pushed yourself — and it showed. "
                        + getPRLabel(run) + ". Feeling spent after that makes sense.";
            } else if (post == EnergyLevel.MODERATE) {
                mainMessage = "Strong run. " + getPRLabel(run) + " and you're still feeling good.";
            } else if (post == EnergyLevel.HIGH) {
                mainMessage = getPRLabel(run) + " and you finished strong. That's a great day.";
            } else {
                // Post-run energy was skipped.
                mainMessage = getPRLabel(run) + ". Strong effort.";
            }
        } else if (post == EnergyLevel.HIGH) {
            mainMessage = "Strong all-around run. You finished feeling great.";
        } else if (post == EnergyLevel.MODERATE) {
            mainMessage = "Solid run. Good effort today.";
        } else if (post == EnergyLevel.LOW) {
            mainMessage = "You gave everything today. Good job getting it done.";
        } else {
            // No PR, no post-run energy recorded.
            mainMessage = "Good job getting a run in today. Every run counts.";
        }

        // LOW -> HIGH energy lift always adds a second line on top of the main message.
        if (pre == EnergyLevel.LOW && post == EnergyLevel.HIGH) {
            return mainMessage + "\nSee what getting active can do. "
                    + "You started rough and finished feeling great.";
        }

        return mainMessage;
    }

    // Returns a readable description of whichever PRs this run earned.
    private String getPRLabel(Run run) {
        if (run.isLongestDistanceRecord() && run.isFastestAveragePaceRecord()) {
            return "New longest distance PR and fastest pace PR";
        } else if (run.isLongestDistanceRecord()) {
            return "New longest distance PR";
        } else {
            return "New fastest pace PR";
        }
    }

    // Asks repeatedly until it can return a valid, non-future date.
    private LocalDate readRunDate() {
        while (true) {
            System.out.print("Run date (YYYY-MM-DD, Enter for today): ");
            // Reads the answer, removes outside spaces, and stores the cleaned text.
            String input = scanner.nextLine().trim();

            // A blank answer returns today's date from the computer.
            if (input.isEmpty()) {
                return LocalDate.now();
            }

            // Attempts the date conversion because user-entered text may be invalid.
            try {
                // Converts YYYY-MM-DD text into a LocalDate object.
                LocalDate date = LocalDate.parse(input);

                // Rejects a future date because this is a completed run.
                if (date.isAfter(LocalDate.now())) {
                    System.out.println("A completed run cannot use a future date.");
                    // Restarts the while loop and asks for the date again.
                    continue;
                }

                // Returns the valid date and ends this method.
                return date;
            } catch (DateTimeParseException exception) {
                // Handles a failed date conversion instead of letting the program stop.
                System.out.println(
                        "Enter the date in YYYY-MM-DD format, such as 2026-06-19."
                );
            }
        }
    }

    // Converts a numbered unit choice into a DistanceUnit enum value.
    private DistanceUnit readDistanceUnit() {
        System.out.println("Distance unit:");
        System.out.println("1. Miles");
        System.out.println("2. Kilometers");
        // Validates a choice from 1 to 2 and stores the returned whole number.
        int choice = readWholeNumber("Choose a unit: ", 1, 2);
        // Choice 1 returns the enum value used for mile-based calculations and labels.
        if (choice == 1) {
            return DistanceUnit.MILES;
        }
        // Since only 1 or 2 is valid, reaching here means kilometers was selected.
        return DistanceUnit.KILOMETERS;
    }

    // Uses one method for either stage and returns the selected EnergyLevel or null.
    private EnergyLevel readEnergyLevel(boolean preRun) {
        // The ternary chooses the question based on the preRun boolean argument.
        String question = preRun
                ? "Pre-run energy (optional):"
                : "Post-run energy (optional):";
        System.out.println(question);
        System.out.println("0. Skip");
        // Each call returns the label matching both the level and run stage.
        System.out.println("1. " + getEnergyLabel(EnergyLevel.LOW, preRun));
        System.out.println("2. " + getEnergyLabel(EnergyLevel.MODERATE, preRun));
        System.out.println("3. " + getEnergyLabel(EnergyLevel.HIGH, preRun));
        // Validates 0 through 3 and stores the returned whole-number choice.
        int choice = readWholeNumber("Choose an energy level: ", 0, 3);
        // Translates the console number into the EnergyLevel stored by Run.
        switch (choice) {
            case 1:
                // Choice 1 maps to the shared low EnergyLevel.
                return EnergyLevel.LOW;
            case 2:
                // Choice 2 maps to the shared moderate EnergyLevel.
                return EnergyLevel.MODERATE;
            case 3:
                // Choice 3 maps to the shared high EnergyLevel.
                return EnergyLevel.HIGH;
            default:
                // Choice 0 skips this optional energy answer.
                return null;
        }
    }

    // Returns the visible label for one EnergyLevel at the requested run stage.
    private String getEnergyLabel(EnergyLevel energyLevel, boolean preRun) {
        // true retrieves labels such as "I'm Here" from the enum.
        if (preRun) {
            return energyLevel.getPreRunLabel();
        }
        // false retrieves labels such as "Spent" from the same enum value.
        return energyLevel.getPostRunLabel();
    }

    // Repeats until the entered text becomes an int inside the required range.
    private int readWholeNumber(String prompt, int minimum, int maximum) {
        while (true) {
            System.out.print(prompt);
            // Stores the runner's cleaned keyboard input before conversion.
            String input = scanner.nextLine().trim();
            try {
                // Attempts to convert the String into an int.
                int value = Integer.parseInt(input);
                // Accept the number only when it fits the current menu's range.
                if (value >= minimum && value <= maximum) {
                    return value;
                }
            } catch (NumberFormatException exception) {
                // Reaches here when input cannot be converted into a whole number.
            }
            // Runs after failed conversion or a number outside the allowed range.
            System.out.println("Enter a whole number from " + minimum + " to " + maximum + ".");
        }
    }

    // Repeats until the entered text becomes a usable double greater than zero.
    private double readPositiveNumber(String prompt) {
        while (true) {
            System.out.print(prompt);
            // Stores the cleaned keyboard input before decimal conversion.
            String input = scanner.nextLine().trim();
            try {
                // Attempts to convert the String into a double.
                double value = Double.parseDouble(input);
                // Reject zero, negative values, infinity, and NaN.
                if (Double.isFinite(value) && value > 0) {
                    return value;
                }
            } catch (NumberFormatException exception) {
                // Reaches here when input cannot be converted into a number.
            }
            // Runs after failed conversion or a number that is not usable.
            System.out.println("Enter a number greater than zero.");
        }
    }

    // Returns cleaned optional text, using null when no text was entered.
    private String readOptionalText(String prompt) {
        System.out.print(prompt);
        // trim removes extra spaces from the beginning and end of the answer.
        String input = scanner.nextLine().trim();
        // Optional blank text is represented by null in the Run object.
        if (input.isEmpty()) {
            return null;
        }
        // A nonblank route is returned to logRun and stored in routeName.
        return input;
    }
}
