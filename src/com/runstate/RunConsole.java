package com.runstate;

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
                    // Starts the completed-run logging questions. logRun returns false only
                    // when the save failed — end the menu loop after its recovery receipt.
                    if (!logRun()) {
                        running = false;
                    }
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

    // Returns true when the run was saved and the full success flow ran (menu continues),
    // or false when the save failed (start() ends the session after the recovery receipt).
    private boolean logRun() {
        System.out.println("Log Run");
        LocalDate date = readRunDate();
        DistanceUnit distanceUnit = readDistanceUnit();
        double distance = readPositiveNumber("Distance: ");
        double duration = readPositiveNumber("Duration in minutes: ");
        String routeName = readOptionalText("Route name (optional): ");

        // Optional pre-run context section (surface, shoes, company, sound). Returns an
        // all-null RunContext.EMPTY if the runner skips the whole section.
        RunContext context = readRunContext();

        EnergyLevel preRunEnergy;
        if (pendingPreRunEnergy != null) {
            // Use the opening screen answer and clear it so it doesn't carry to a second run.
            preRunEnergy = pendingPreRunEnergy;
            pendingPreRunEnergy = null;
        } else {
            // Runner skipped the opening prompt — ask for pre-run energy now.
            preRunEnergy = readEnergyLevel(true);
        }

        // Fetch weather for this run's date and the runner's location BEFORE the
        // Run is created and saved. THIS is the persistence fix: weather becomes
        // part of the run from birth, instead of being bolted on after saveRun.
        // WeatherService returns an all-null WeatherData on any failure, so a slow
        // or down API never blocks logging a run. No prompt — this is silent.
        WeatherData weather = WeatherService.fetch(runner.getCity(), runner.getState(), date);

        // Post-run energy and effort are null here because the runner hasn't finished yet.
        // The constructor accepts null for optional fields; both are set just below via setters.
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
                null,
                context,
                weather,
                null
        );

        // Step 2 — show the metrics BEFORE reflection. addRun has not run yet, so the
        // summary is pure metrics (plus pre-run energy) with no PR line. PRs are announced
        // later, only after the run is durably saved (step 7). This preserves
        // summary-before-reflection while moving PR confirmation behind the save.
        System.out.println();
        System.out.println(run.getRunSummary());
        System.out.println();

        // Step 3 — now that the runner has seen their results, ask post-run energy.
        EnergyLevel postRunEnergy = readEnergyLevel(false);

        // Store the answer directly on the run object using the setter we just added.
        run.setPostRunEnergy(postRunEnergy);

        // Step 4 — immediately after energy, ask what the run COST — the effort axis. Same
        // construct-early reason as post-run energy, so it's attached via a setter too.
        EffortLevel effortLevel = readEffortLevel();
        run.setEffortLevel(effortLevel);

        // Step 5 — save the complete run FIRST. A run only counts once it is durably
        // stored, so nothing below (history, PRs, AI, RunStyle) runs until this succeeds.
        try {
            RunStorage.saveRun(run);
        } catch (RunStorageException e) {
            // The run is still in hand but not saved. Print the recovery receipt and
            // report failure so start() ends the session — without adding to history,
            // announcing a PR, or generating AI/RunStyle for a run that was never saved.
            printSaveFailureReceipt(run, e);
            return false;
        }

        // Steps 6 & 7 — add the saved run to in-memory history; addRun both flips the PR
        // flags and announces any new PR. This now happens only after a durable save,
        // which is exactly what kills the phantom-PR bug.
        runner.addRun(run);

        // Step 8 — the AI response (PR flags are set now, so it can reflect them).
        System.out.println();
        System.out.println(RunAgent.buildRunResponse(run));

        // Step 9 — any RunStyle announcement (history already includes this run).
        String runStyleAlert = runner.detectRunStyle(run);
        if (runStyleAlert != null) {
            System.out.println();
            System.out.println(runStyleAlert);
        }

        // Full success flow ran — tell start() to keep the menu open.
        return true;
    }

    // Prints the save-failure recovery receipt (spec wording). The temporary run is still
    // in hand, so its summary — date, route, distance, pace, duration, energy, effort —
    // becomes the recovery record. "Check Run History" is required because MySQL may have
    // inserted the row before the connection dropped; re-entering blindly could duplicate it.
    private void printSaveFailureReceipt(Run run, RunStorageException e) {
        System.out.println();
        System.out.println("RunState could not confirm that this run was saved.");
        System.out.println();
        System.out.println("Recovery summary:");
        System.out.println(run.getRunSummary());
        System.out.println();
        System.out.println("Restart RunState and check Run History.");
        System.out.println("Re-enter this run only if it is missing.");
        System.out.println();
        System.out.println("Details: " + e.getCause().getMessage());
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

    // Asks how the run landed (the effort axis) and returns the EffortLevel, or null if skipped.
    private EffortLevel readEffortLevel() {
        System.out.println("How did that run land?");
        System.out.println("0. Skip");
        // Labels live on the enum (Smooth/Working/Heavy/Empty tank) — single source of truth.
        System.out.println("1. " + EffortLevel.LOW_COST.getLabel());
        System.out.println("2. " + EffortLevel.MODERATE_COST.getLabel());
        System.out.println("3. " + EffortLevel.HIGH_COST.getLabel());
        System.out.println("4. " + EffortLevel.MAX_COST.getLabel());
        // Validates 0 through 4 and stores the returned whole-number choice.
        int choice = readWholeNumber("Choose how it landed: ", 0, 4);
        switch (choice) {
            case 1: return EffortLevel.LOW_COST;
            case 2: return EffortLevel.MODERATE_COST;
            case 3: return EffortLevel.HIGH_COST;
            case 4: return EffortLevel.MAX_COST;
            default: return null;  // 0 = Skip
        }
    }

    // Collects the optional pre-run context section. One "skip all" gate opens it; once
    // open, every individual answer is still skippable. Returns RunContext.EMPTY when the
    // runner declines the whole section, so a run with no context is still valid.
    private RunContext readRunContext() {
        System.out.println();
        System.out.print("Add run context — surface, shoes, company, sound? (y/N): ");
        String answer = scanner.nextLine().trim().toLowerCase();
        // Anything other than an explicit yes skips the entire section (the single skip-all).
        if (!answer.equals("y") && !answer.equals("yes")) {
            return RunContext.EMPTY;
        }

        // Each reader below returns null when its own item is skipped.
        SurfaceType surface = readSurface();
        String shoeLabel = readOptionalText("Shoes (e.g. Pegasus 41) (optional): ");
        RunCompany company = readCompany();
        MusicMode musicMode = readMusicMode();

        // The free-text music note is only worth asking when the runner actually had music.
        String musicNote = null;
        if (musicMode == MusicMode.MUSIC) {
            musicNote = readOptionalText("What were you listening to? (optional): ");
        }

        return new RunContext(surface, company, shoeLabel, musicMode, musicNote);
    }

    // Asks what surface the run was on, or returns null if skipped. Menu labels come
    // straight from the enum so the wording lives in exactly one place.
    private SurfaceType readSurface() {
        System.out.println("Surface (optional):");
        System.out.println("0. Skip");
        System.out.println("1. " + SurfaceType.ROAD.getLabel());
        System.out.println("2. " + SurfaceType.TRAIL.getLabel());
        System.out.println("3. " + SurfaceType.TRACK.getLabel());
        System.out.println("4. " + SurfaceType.TREADMILL.getLabel());
        System.out.println("5. " + SurfaceType.MIXED.getLabel());
        int choice = readWholeNumber("Choose a surface: ", 0, 5);
        switch (choice) {
            case 1: return SurfaceType.ROAD;
            case 2: return SurfaceType.TRAIL;
            case 3: return SurfaceType.TRACK;
            case 4: return SurfaceType.TREADMILL;
            case 5: return SurfaceType.MIXED;
            default: return null;  // 0 = Skip
        }
    }

    // Asks whether the runner was solo or with others, or returns null if skipped.
    private RunCompany readCompany() {
        System.out.println("Company (optional):");
        System.out.println("0. Skip");
        System.out.println("1. " + RunCompany.SOLO.getLabel());
        System.out.println("2. " + RunCompany.WITH_OTHERS.getLabel());
        int choice = readWholeNumber("Choose company: ", 0, 2);
        switch (choice) {
            case 1: return RunCompany.SOLO;
            case 2: return RunCompany.WITH_OTHERS;
            default: return null;  // 0 = Skip
        }
    }

    // Asks whether the runner had music at all (the "Sound" question), or returns null
    // if skipped. NO_MUSIC is a real recorded answer, distinct from a skip (null).
    private MusicMode readMusicMode() {
        System.out.println("Sound (optional):");
        System.out.println("0. Skip");
        System.out.println("1. " + MusicMode.MUSIC.getLabel());
        System.out.println("2. " + MusicMode.NO_MUSIC.getLabel());
        int choice = readWholeNumber("Did you run with sound? ", 0, 2);
        switch (choice) {
            case 1: return MusicMode.MUSIC;
            case 2: return MusicMode.NO_MUSIC;
            default: return null;  // 0 = Skip
        }
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
