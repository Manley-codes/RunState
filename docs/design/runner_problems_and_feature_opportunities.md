# Runner problems and feature opportunities

**Recorded October 15, 2026.** This is a product opportunity record, not a new build queue or a
promise to deliver every feature before AfroTech. The accepted order remains **dependable GPS and
a real map → useful runner-state learning → bounded music integration**, with early provider
feasibility checks and time protected for demonstration reliability.

Two opportunities deserve stronger emphasis: **starting without fumbling with a phone** and
**useful announcements that preserve the musical experience**. Manley's experience and recorded
run-club observations identify plausible problems; outside-runner usefulness remains to be tested.

## Automatic start detection

**The problem:** a running group takes off while someone is still handling Start, music, gloves or
an armband. The runner either falls behind or misses the recorded beginning, a specific situation
in the project's run-club notes.

**The opportunity:** prepare once, put the phone away, and trust RunState to recognize the real
takeoff. The existing **Prepare to Detect** direction makes this a leading feature hypothesis:

1. The runner deliberately arms a temporary preparation window. Monitoring has a visible status,
   an expiry and a simple cancel action.
2. Possible running enters a quiet provisional state. A short rolling buffer retains the evidence
   needed to recover the beginning; a candidate does not create an official run.
3. Sustained plausible movement confirms takeoff. The app starts the durable session and restores
   only a beginning supported by trustworthy buffered observations.
4. A brief audio or haptic confirmation lets the runner know it worked. Manual Start and an easy
   correction or Undo path remain available; false detection must not silently become history.

Warm-up drills, walking, short jogs, crowded starts, early traffic lights, loops and weak GPS are
the difficult cases. A single speed threshold cannot distinguish them. Location and permitted
motion evidence should be evaluated together; missing evidence must not become invented route or
time. The first experiment should observe candidate starts without controlling a real session.

The existing tracking, lifecycle and recovery work provides groundwork, but automatic detection
is **not implemented**. Success means fewer missed beginnings and less phone handling, with
acceptable false starts, detection delay and battery use.
Automatic pause during a run is a separate experiment. Voice-triggered Start, preparation of music
and an optional forgotten-headphones reminder remain related ideas, not prerequisites.

Detailed behavior and edge cases: [Run initiation register](run_initiation_register.md).

## Announcements that respect music and concentration

**The problem:** a useful pace or distance update can still feel like an abrupt interruption. An
earlier runner complaint in the project notes supports investigating this; the solution's benefit
has not yet been demonstrated with other runners.

**The opportunity:** make information available in a delivery the runner welcomes. The existing
ideas and Manley's October 15 additions fit together as follows:

**Current emphasis:** fit the announcement's voice, tone and delivery to the playing music so it
feels less intrusive. This has more value to Manley than processing the music into an echo; that
effect is parked. Matching uses only evidence available through a permitted route. Music access or
notification access does not itself authorize audio capture, automatic music analysis or supplying
provider content to AI. Runner-chosen delivery profiles can be explored without those assumptions.

| Dimension | Proposed behavior |
| --- | --- |
| Voice and delivery | Offer a restrained tone, cadence, intensity and length suited to the runner's preference. Music-aware delivery may be explored using permitted evidence; matching delivery never changes factual numbers or the exact saved reflection. |
| Timing and control | Let runners choose silence, requested updates or scheduled announcements. Distance/time intervals and selected milestones are options to evaluate, not locked settings. |
| Restraint | Avoid repeated commentary and stale queued updates. Silence is a valid result; the app never congratulates itself for staying quiet. |
| Protect momentum | Do not automatically skip or replace a playing song. Song-boundary timing can be considered where reliable and permitted; beat-perfect timing is not promised. |
| Brief support | Preserve the runner-invoked **I Need a Push** idea and runner-authored anchor phrases. A pace drop alone never authorizes motivational speech. |
| Different moments | Keep factual live updates distinct from the more personal post-run reflection. A characterful Completion voice should not imply theatrical announcements throughout the run. |

The current facts → reflection → instrumental sequence and one Moto/Spotify return check provide
groundwork. Adaptive live announcements, adjustable schedules and music-matched speech remain
unbuilt. Calls, lost focus and headphone changes also need dependable behavior.

**Provider preference:** Spotify remains the first choice. Investigate its actual constraints
before changing direction; consider a less restrictive provider only if multiple substantial
barriers prevent the intended useful experience. An optional echo effect alone is not a reason to
switch. This preference does not change the accepted build order.

Related decisions: [Music register](music_feature_register.md),
[music system](design_music_selection_system.md) and
[post-run voice contrast](design_run_response_system.md#post-run-voice-contrast--direction-not-yet-prototyped-added-august-31-2026).

### Parked idea for music surrounding the announcement

**New idea from Manley:** when speech enters, the music feels more spacious or echo-like around it,
supporting the voice instead of simply becoming quieter. Manley's later clarification parks this
effect in favor of voice delivery that fits the music; it is not required for that feature.

Three different mechanisms matter:

- **Volume ducking** lowers music while speech plays. Android audio focus coordinates that behavior,
  including whether another player ducks or pauses; it does not supply that player's audio for
  editing. [Android audio focus](https://developer.android.com/media/optimize/audio-focus)
- **Audio processing** changes the sound itself. For audio RunState controls and has appropriate
  rights to modify, a player/effects path can support a subtle reverberant treatment. Android's
  reverb API requires the relevant player to be attached to the effect; device behavior still needs
  testing. [Android reverb](https://developer.android.com/reference/android/media/audiofx/EnvironmentalReverb)
- **A supportive impression** is the listening goal. It does not require obvious repeated echoes
  or theatrical spatial effects.

**If revisited:** audition with owned or appropriately licensed audio. Start with a
clear, mostly unprocessed voice and restrained musical ambience. Strong repeated echoes could
compete with words and rhythm, defeating the purpose. Compare it with ordinary ducking for speech
understanding, perceived interruption and preference, including headphones and mono playback.

Do not promise this processing for Spotify playing in another app. Spotify's developer policy
prohibits mixing, remixing or overlapping Spotify content with other audio in developer apps.
The exact integration needs a permitted route; existing Android coexistence is not proof of
permission to remix. [Spotify policy](https://developer.spotify.com/policy)

Screen/audio capture or a system-wide effect is not a workaround to pursue. Android capture needs
user approval and respects the source app's capture policy; capture permission does not grant
content rights. [Android capture rules](https://developer.android.com/media/platform/av-capture)

## Other opportunities to retain

| Runner problem | Feature and evidence needed |
| --- | --- |
| Uncertain recording | Trustworthy GPS, honest gaps, pace, pause/resume and recovery. Prove them on measured outdoor routes. |
| Similar numbers, different experience | Pre/post Energy and separate Effort, followed by grounded patterns across genuine answered runs. Test whether an insight adds something useful. |
| Progress beyond speed is overlooked | **Quiet Gains:** comparable running that felt less demanding. Report perceived effort, not a diagnosis of fitness improvement. |
| Repeated setup distracts from running | Remembered preferences and quick Start. Already partly implemented; check real setup burden. |
| Songs and running memories get disconnected | Trustworthy music History and separate Taste/Run fit feedback. Playback alone does not prove enjoyment or benefit. |
| Shoe use is hard to remember | Automatic per-pair mileage from completed runs. Useful bookkeeping, without injury-prevention promises. |

Visualizers, transitions, snippets and share cards support identity and enjoyment; their practical
benefit still needs evidence.

## How to decide what earns further work

Manley welcomes real-run checks and can recruit other willing runners. Request a check when the
software and protocol are ready. Give explicit steps: starting screen and mode, who starts the run,
when to lock/unlock, how long to move, what to note, whether to pause or finish, and the exact state
to leave on return. Do not start the timer before the runner is ready or leave the end state
implicit.

Use a small group of willing runners, ideally across multiple runs. Ask about the original problem,
then observe whether the feature reduces friction, gives useful information or earns repeat use.
Record confusing and unwanted behavior alongside successes. Detection needs labeled real starts
and warm-ups; audio needs listening comparisons. Software tests establish mechanics, while runner
feedback establishes usefulness. Neither automatically establishes novelty or broad demand.
