# RunState

RunState is the new working name for this project, formerly called RunNet.

RunState is a beginner-friendly Java console app for tracking completed runs and personal progress.

The project focuses on a small, polished run tracker that logs run details, calculates pace, detects personal records, and tracks how each run felt. The goal is to keep the app understandable while practicing core Java and object-oriented programming concepts.

## Current Features

- Create a runner profile.
- Log completed runs.
- Store multiple runs in a runner's run history.
- Display run history newest first.
- Track distance, duration, average pace, and route.
- Use fixed distance unit choices for miles and kilometers.
- Show pace with the correct unit, such as minutes per mile or minutes per kilometer.
- Optionally record energy before and after a run.
- Use playful labels backed by a shared low-to-high energy scale:
  - Pre-run: I'm Here, Ready-ish, Let's Go!
  - Post-run: Spent, Feeling Good, Powered Up
- Display recorded pre-run and post-run energy in each run summary.
- Display run history as compact, runner-friendly cards with clock-style pace.
- Detect personal records:
  - Longest distance
  - Fastest average pace
- Display the runner's current personal record summary.

## Project Direction

RunState is currently staying focused on run tracking, personal records, post-run feeling, simple feeling-based insights, and clean console output.

Larger ideas such as weather context, route context, shoe tracking, support messages, voice encouragement, AI coaching, GPS, and database integration are parked for later. They may become future features, but they are not the current build target.

## Next Improvements

- Prototype a transparent route-and-distance observation for the future `Your Run Style` section.
- Continue designing how energy, run metrics, and personal history should connect before adding energy-based interpretation.
- Clean up repeated summary logic where it makes sense.
- Update unit handling later so mixed miles and kilometers are handled more carefully across summaries and averages.
- Improve this README as the project becomes more polished.

## Concepts Practiced

- Classes and objects
- Constructors
- Private fields and getters
- Encapsulation
- Composition
- Enums with fields and methods
- ArrayList
- Loops
- Helper methods
- Linear search
- Counting by category
- Finding minimum and maximum values
- Calculating averages
- Avoiding division by zero
- Basic Big O thinking

## Technologies Used

- Java
- IntelliJ IDEA
- Git
- GitHub

## Project Status

RunState is in active development as a Java learning and portfolio project.

## Author

Manley Johnson
