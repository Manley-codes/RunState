# Data & Privacy

RunState is a local Java console app. Your run data stays on your machine — it is never synced
to a RunState server, account system, or cloud service.

Two external services are used:

---

## Anthropic API

After each run is logged, a summary of that run is sent to Anthropic's API to generate your
post-run response. The data included in that request:

- Run date and season
- Distance, pace, and duration
- Pre- and post-run energy levels
- Personal record status
- Route name (if entered)
- Surface (road, trail, track, treadmill, mixed), run company (solo or with others), and
  shoe label (if entered) — the optional run-context details
- Music: whether you ran with sound, and the free-text note of what you listened to
  (if entered)
- A short comparison summary derived from your recent runs (e.g. how this run's pace or
  energy compares to similar past runs) — computed on your machine; only the summary is sent
- Weather conditions for the run date (once the weather feature is enabled)

Run company is only ever the coarse value "solo" or "with others" — no names or identities
of other people are ever collected or sent.

No name, email, or account information is included in the request. All requests are sent
over HTTPS. See [Anthropic's Privacy Policy](https://www.anthropic.com/privacy) for details
on how Anthropic handles API data.

---

## Open-Meteo API (weather feature)

When weather data is fetched, your city and state are sent to Open-Meteo's geocoding API
to retrieve coordinates. Those coordinates are then used to request weather conditions for
your run date. Open-Meteo is a free, open-source weather service — no account or API key
required. No personal information beyond city and state is sent. See
[open-meteo.com](https://open-meteo.com) for details.

---

## What never leaves the app

- Your name or email address
- GPS coordinates or precise device location
- Any data outside of individual run entries
- Your RunStyle profile — the local pattern analysis of your runs (which patterns are
  forming, and the context that tends to accompany your productive runs) is computed on
  your machine and is never sent to any API. Only the individual run fields listed above
  leave the app; the profile built from them does not.

---

RunState is a learning and portfolio project, not a commercial service. Both external APIs
are used solely to power the AI response and weather context features.
