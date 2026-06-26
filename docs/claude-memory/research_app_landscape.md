---
name: research-app-landscape
description: "Competitive landscape + research-backed product considerations for RunState (June 2026): differentiation, RPE science, privacy, and scope-creep watch."
metadata:
  type: reference
---

# Running-app landscape & considerations (researched June 26, 2026)

Web research done to surface things to consider before expanding RunState's feature set
(specifically before Phase 5 Step 2, weather). See [[design-weather-context]] for the build,
and [[user-goals]] for the scope-discipline commitment this reinforces.

## Strategic read — differentiate on identity, not features

There is no single "best" running app; runners typically use 2-3 together. The majors own clear lanes:
- Strava = social (segments, kudos, clubs)
- Garmin / Coros = device data depth (VO2max, training load, HRV, Body Battery)
- Runna = adaptive training plans (acquired by Strava in 2025)
- Nike Run Club = free, friendly, guided/voice runs

Implication: **RunState cannot and should not compete on feature count.** Its white space is the
combination none of them center: a tracker built around *how the run felt* + an AI agent with a real
*voice* that makes a run feel meaningful. Judge every new idea by: does it strengthen the
feeling-and-voice core, or just chase parity?

## RPE — the science under the "energy" system (on-identity)

RunState's pre/post "energy" is a friendly form of **RPE (Rate of Perceived Exertion)**, the established
sports-science measure of subjective effort. Useful findings:
- **Rating timing matters:** the longer after a run someone rates effort, the LOWER they rate it.
  RunState captures post-run energy right after the summary = good design. Backdated logs are less
  reliable; the agent should lean on same-day logs.
- **Session-RPE** (effort x duration) is a recognized training-load metric. RunState's
  `energy(1-3) x duration` could later become a simple, science-backed "effort load" number — an
  on-identity future analytic using data already captured. NOT scope creep.
- **Granularity tension (note, don't fix):** a 3-level scale is friendly but coarse for long-term
  pattern detection; standard RPE is 0-10. Revisit only if pattern detection feels too blunt.

## Privacy — the one genuinely new cross-cutting consideration

Across AI-coaching coverage, privacy is the top concern: sending user health/location data to
third-party AI servers without clear consent. For RunState this is concrete, not abstract:
- The agent already sends run data (distance, pace, energy, route) to **Anthropic's API** (third party).
- Weather adds **location**: city/state to **Open-Meteo**, and city may enter the agent prompt.
City-level data is coarse/low-risk, but two external services now receive run data.
Recommended action: a short "Data & Privacy" note in README/docs (what leaves the app, to whom, why).
Cheap, intentional, and a maturity signal employers notice. Tracked in [[design-weather-context]].

## Scope-creep watch — consciously RESIST (mostly already parked)

These are table stakes for the big apps and explicitly NOT RunState's lane as a console learning
project. The roadmap already parks most; naming them keeps them parked:
- GPS / automatic run tracking
- Social feed, segments, kudos, leaderboards
- Device/file integrations (FIT/GPX, Garmin/Apple Health sync)
- Recovery metrics (HRV, sleep, Body Battery)
- Proactive/adaptive coaching nudges ("great day for a PR")

## Sources

- Best running apps 2026 (MAVR): https://www.mavr.app/blog/best-running-apps-2026-complete-guide
- Best AI running coach apps 2026 (Running Genie): https://therunninggenie.com/blog/best-ai-running-coach-apps
- How AI is revolutionizing fitness coaching 2026 (Vora): https://askvora.com/blog/ai-fitness-coaching-2026
- RPE scale for running (Marathon Handbook): https://marathonhandbook.com/rate-of-perceived-exertion/
- RPE & subjective feedback (TrainingPeaks): https://www.trainingpeaks.com/learn/articles/what-are-rpe-and-subjective-feedback/
- Weather impact on running (The Running Week): https://www.therunningweek.com/post/weather-impact-running-performance-complete-guide
- Berlin Marathon environmental factors study (NCBI): https://www.ncbi.nlm.nih.gov/pmc/articles/PMC11482731/
- Open-Meteo Geocoding API: https://open-meteo.com/en/docs/geocoding-api
- Open-Meteo Historical Weather API: https://open-meteo.com/en/docs/historical-weather-api
