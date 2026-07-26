# Data & Privacy

RunState is a local Java console app. Your run data stays on your machine — it is never synced
to a RunState server, account system, or cloud service.

Two external services are used:

---

## Anthropic API

After each run is durably saved, a summary of that run is sent to Anthropic's API to generate
your post-run response. The data included in that request:

- Run date and season
- Distance, pace (normalized to minutes per mile), and duration
- Pre- and post-run energy levels
- Post-run effort cost
- Personal record status
- Route name (if entered)
- Surface, run company, and shoe label (if entered)
- Music state (Music / No music / Not recorded) and the free-text note of what you listened to
  (if entered)
- Daily-mean weather for your run date — temperature, apparent temperature, and condition —
  only when the weather fetch succeeded
- A candidate-based comparison summary when matching past runs exist: the shared basis
  (same route or similar distance), and each positive or explanatory signal with its
  evidence-run count and confidence tier. This is computed entirely on your machine;
  only the derived summary is sent, not individual historical run rows.

Run company is only ever the coarse value "solo" or "with others" — no names or identities
of other people are ever collected or sent.

No name, email, or account information is included in the request. All requests are sent
over HTTPS. See [Anthropic's Privacy Policy](https://www.anthropic.com/privacy) for details
on how Anthropic handles API data.

---

## Open-Meteo API

Your city and state are sent to Open-Meteo's geocoding API to retrieve coordinates. Those
coordinates are then used to request daily-mean weather conditions for your run date.
Open-Meteo is a free, open-source weather service — no account or API key required. No
personal information beyond city and state is sent. See
[open-meteo.com](https://open-meteo.com) for details.

---

## What never leaves the app

- Your name or email address
- GPS coordinates or precise device location
- Your raw run history — only the derived comparison summary described above may be sent;
  individual historical run rows do not leave the app
- Your RunStyle profile — the local pattern analysis (which patterns are forming, and the
  context that tends to accompany your productive runs) is computed on your machine and is
  never sent to any API

---

RunState is a learning and portfolio project, not a commercial service. Both external APIs
are used solely to power the AI response and weather context features.
