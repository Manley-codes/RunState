# RunNet Ideas

## Run Tracking

- Users can log and track their runs.
- Runs can include distance, time, pace, route, location, and whether it was a solo or club run.

## User Experience Ideas

- The app should avoid clutter and make important features easy to find.
- Common runner actions should be visible without digging through too many menus.
- After completing a run, the app should show useful next actions such as:
  - Add photo
  - Add notes
  - Share with club
  - View run details
- Users may eventually be able to pin their most-used features to the main screen.
- The app should use clear defaults first, then add customization later.
- Customization should start simple, such as pinned shortcuts, instead of a fully customizable layout.

## Context-Aware Suggestions

- The app should suggest useful actions based on what the runner is doing.
- Instead of making users search through menus, the app should bring important next steps forward at the right moment.

Examples:

- After completing a run, suggest adding a photo, adding notes, sharing with a club, or viewing run details.
- After setting a new personal record, suggest saving or sharing the achievement.
- Before a scheduled club run, suggest viewing the route, checking weather, or messaging the club.
- If weather conditions are poor, suggest safer alternatives or warnings.
- After a hard effort, suggest recovery actions or reflection notes.

Example post-run prompt:

```text
Great job, Jay.

You completed 5.0 miles in 50 minutes.
Average pace: 10:00 per mile.

Would you like to add a photo to this run?
[Add Photo] [Skip]
```


## Run Club Communication

- Combine run tracking with group communication, similar to how apps like GroupMe support group chat.
- Clubs can use the app to communicate before and after runs.

## Support Message Feature

- When a runner completes a run or race, friends or club members can send supportive messages.
- Future versions could support voice messages or text messages converted into voice encouragement.

## Auto-Stop Runs

- Users can set a goal before starting a run, such as a target distance or target time.
- The app can automatically stop the run when the goal is reached.
- Possible extra feature: if the runner continues past the goal, the app can remind them again after a short extra distance or time.

## AI-Assisted Workout Creation

- Users can describe the workout they want in normal language.
- Example: "Create a beginner 30-minute interval run."
- The app turns that request into a structured workout plan.

## Event Announcements

- Run clubs can post announcements for upcoming group runs, races, meetups, or special events.

## Shared Run History

- Club members can view shared run history, such as group runs, completed club events, or personal runs shared with the club.

## Run Club Platform

- Users can create and manage run clubs.
- The app should work for both organized run clubs and individual runners who are not part of a club.

## Trail And Route Information

- Users can browse trails or routes with images and descriptions.
- Route details could include:
    - Surface type
    - Elevation
    - Difficulty
    - Shade
    - Restrooms
    - Water fountains
    - Parking
    - Safety notes

## Route Intelligence

- Future feature that gives runners useful route updates.
- Could include:
    - Route crowding
    - Nearby events
    - Construction
    - Local activity
    - Other conditions that may affect the run

## Weather Conditions

- Show weather information that may affect a run.
- Could include:
    - Temperature
    - Rain
    - Wind
    - Humidity
    - Heat warnings
    - Unsafe running conditions

## Personal New Records

- The app should recognize when a runner sets a new personal record.
- Possible personal records include:
  - Longest distance completed
  - Fastest average pace overall
  - Fastest average pace for a specific distance
  - Fastest time for a specific distance

Pace note:

- A lower pace number is better.
- Example: 8.0 minutes per mile is faster than 10.0 minutes per mile.

## Future Effort Analysis

- The app could eventually analyze how hard a runner pushed during a run.
- This would require more detailed run data, such as mile or kilometer splits.
- Possible effort indicators include:
  - Fastest split
  - Strong finish
  - Negative split
  - Pace surge
  - Inconsistent pacing
  - Estimated max effort

## Post-Run Feeling And Performance Patterns

- After a run, the app can ask how the runner felt using a simple 3-level choice.
- The first version can use:
  - Hard
  - Normal
  - Great

The goal is to collect simple feedback without making the app feel cluttered or overwhelming.

Over time, the app could compare how the runner felt with other run details, such as:

- Weather conditions
- Trail or route location
- Whether the runner ran alone or with others
- Recent running consistency
- Distance
- Average pace
- Time of day

Future goal:

- Help runners understand when and why they perform their best.
- Suggest useful patterns, such as whether the runner performs better on certain routes, in certain weather, or after consistent training.
- Later, the app could use voice prompts or voice announcements after a run.

Example post-run prompt:

```text
How did this run feel?

[Difficult] [Normal] [Great]
```