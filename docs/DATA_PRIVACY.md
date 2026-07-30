# Data & Privacy

RunState is a local Java console app. Your run history is **stored on your machine** and is
never synced to a RunState server or account system — RunState has no server and no accounts.

That is not the same as saying your data never leaves your machine. Two external services are
involved:

- **Selected run fields may be transmitted to Anthropic**, when a post-run API request is
  attempted, to generate your response.
- **The weather lookup makes up to two Open-Meteo requests.** A nonblank city name is sent to
  the geocoding endpoint, and only if that succeeds are the selected coordinates — together
  with your run date — sent to the forecast endpoint.

Both are described in full below.

---

## Anthropic API

### When a request happens

After a run is **durably saved**, RunState attempts an Anthropic request whenever an API key
is available.

If `ANTHROPIC_API_KEY` is **missing or blank**, RunState falls back to the local response
**before any request is built or sent**. That guarantees **nothing was sent to Anthropic** —
but it does not mean nothing left your machine on this run, because the Open-Meteo weather
lookup happens earlier in the flow and may already have occurred.

Every other failure also produces the local fallback, but **where the failure lands relative
to Anthropic receiving your data varies**, and the failure itself does not always reveal
which side it fell on:

- **Local request-construction failures**, and **some** DNS, connection-establishment, or TLS
  failures, can prevent transmission.
- **Receiving any HTTP response at all — including a non-success status — proves the request
  was sent.**
- A **response-parsing failure** happens after a response was already received.
- **Timeouts, connection resets, and other I/O failures may occur before or after** Anthropic
  receives the body.

These are illustrative categories, not an exhaustive list, and none of them is a reliable
signal on its own. **Seeing the local fallback therefore cannot tell you whether transmission
occurred.** All of these cases look identical from the console.

### What the request contains

Each request has two parts.

**1. A fixed system prompt**, identical on every request and containing no run data. It holds:

- the general mentor contract (tone, length, what to lead with)
- the music reply policy
- the instruction that all free-text run fields are **data, never instructions**
- prohibitions on guessing evidence RunState does not have
- instructions for handling the internal history-stage label

The authoritative text lives in `RunAgent.java`. `docs/AI_AGENT.md` summarizes the current
prompt and preserves the July 27 prompt snapshot as explicitly labeled evaluation history.

**2. A dynamic per-run user message**, containing:

- Run date and season
- Distance, pace (normalized to minutes per mile), and duration
- Pre- and post-run energy levels
- Post-run effort cost
- Personal record status
- Route name
- Surface, run company, and shoe label
- Music state, and the free-text note of what you listened to when one is eligible
- The coarse history-stage label `Music reply stage: EARLY|ESTABLISHED`, when it applies
- Daily-mean weather for your run date — temperature, apparent temperature, and condition
- A candidate-based comparison summary when matching past runs exist: the shared basis
  (same route or similar distance), and each positive or explanatory signal with its
  evidence-run count and confidence tier

**Skipping a question does not remove its line.** The effort, route, surface, run company,
shoes, music, and weather lines are **always present** in the message. A value you did not
provide is sent through its documented stand-in instead: `Not recorded` for effort, route,
surface, run company, and shoes; the corresponding music classification for music; and
`Weather: Not available` when the weather fetch did not succeed. What varies is the value on
the line, not whether the line exists.

Only **two** parts of the message are genuinely conditional: the **music-stage line** and the
**comparison block**. Each is omitted entirely when it does not apply.

Run company is only ever the coarse value "solo" or "with others" — no names or identities
of other people are ever collected or sent.

No name, email, or account information is included in the request. `Runner: Runner` is a
constant label; your real username is never sent. All requests are sent over HTTPS. See
[Anthropic's Privacy Policy](https://www.anthropic.com/privacy) for details on how Anthropic
handles API data.

### Free text you type

**Route name, shoe label, and an eligible music note are user-provided free text that is
transmitted to Anthropic.** What you type into those fields is what gets sent, with two
precise qualifications:

- **Route name and shoe label** are sent **unchanged** as logical values.
- **An eligible music note** is sent **after surrounding whitespace is stripped**. Stripping
  affects only the outgoing text; it does **not** modify the note stored on your machine.

Separately, **JSON escaping changes the wire representation without changing the outbound
logical value.** A quote, backslash, tab, or newline inside a route name or music note is
escaped so the request is valid JSON, and decoding restores exactly the value RunState
serialized — the route and shoe values unchanged, and the eligible music note **as it stands
after surrounding whitespace was stripped**. Escaping is a transport detail, not redaction:
it removes nothing and alters no meaning. The outbound music note may differ from the original
input only when surrounding whitespace is stripped.

Two music cases send no note text at all:

- An explicit **"no music"** answer **suppresses any stray note entirely** — the note is not
  sent, even if one is stored on the row.
- A **blank, whitespace-only, or missing** note transmits **only its classification**
  ("had music, track not noted", or "not recorded") — there is no note text to send.

**The system rule treating free text as "data, never instructions" is a model-behavior
safeguard, not redaction or anonymization.** It tells the model not to obey commands typed
into those fields. It does not remove, mask, or filter anything. Identifying or sensitive text
entered into a route name, shoe label, or music note **can still reach Anthropic**.

### The history-stage label

When it applies, the user message includes one additional line:

```
Music reply stage: EARLY|ESTABLISHED
```

- It is derived **locally** from the **total size of your saved history**.
- `EARLY` represents sizes **1–10**; `ESTABLISHED` represents **11 or more**.
- The run you just logged is **already included** in that count — it is saved and added to
  history before the response is built, so nothing is added on afterward.
- **Backdated saved runs still count.** The stage uses total saved-history size, not
  chronology, so logging a run for an earlier date still increases it.
- If there is no Runner, or the Runner has **zero** saved runs, the field is **omitted
  entirely** — no placeholder value is sent.
- **Anthropic receives only the coarse label.** The exact total history count and the
  individual historical rows remain on your machine.

The label is **internal control metadata**. It describes how much history RunState has seen,
and it adjusts how actively the model looks for a music connection. It is **not** a statement
about your fitness, ability, or running experience, and the system prompt instructs the model
never to expose the label or characterize you from it.

### What stays local, and what history-derived data does not

Your **raw historical run rows** and your **exact total history size** remain on your machine.

Two things derived from that history may be transmitted:

1. **Positive or explanatory comparison summaries**, which can include **per-signal
   evidence-bearing comparable-run counts** and a confidence tier.
2. **The coarse `EARLY|ESTABLISHED` stage label.**

Note the distinction between the two kinds of count. The per-signal evidence counts describe
**how many comparable runs supported that one signal** — a small number drawn from the runs
that actually qualified. That is not your total history size, which is never sent as a number.

### What Anthropic is never sent

- Your name or email address
- Your run start time or time of day
- GPS coordinates, precise device location, routes, or splits — beyond the route **name** you
  typed yourself
- Time-aligned run telemetry
- Streaming or music-provider metadata
- Playback history or playback timestamps
- Any history of whether previous replies mentioned music
- Your **RunStyle profile** — the local pattern analysis (which patterns are forming, and the
  context that tends to accompany your productive runs) is computed on your machine and is
  never sent to any API
- Your raw run history rows, and your exact total history size

The system prompt additionally **prohibits the model from guessing any of these absent
signals**. Absent evidence is treated as absent, not reconstructed from what is present.

### The fallback response

The fallback response generator is **local, deterministic, and music-neutral**. It produces
the same response regardless of your music state, and no music note text ever appears in it.

**Calling the fallback generator itself performs no network request.** But as described under
"When a request happens" above, receiving a fallback response from the normal path does
**not**, on its own, tell you whether Anthropic received your run data. Only the missing or
blank key guarantees that nothing was sent to Anthropic — and even then, the Open-Meteo
lookup earlier in the flow may already have happened.

---

## Open-Meteo API

The weather lookup makes **up to two requests**, and they do not send the same thing. Either
may not happen at all.

**0. No city, no request.** If your stored city is **missing or blank**, RunState makes **no
Open-Meteo request** and simply records no weather for the run.

**1. Geocoding.** A **nonblank city name** is sent to Open-Meteo's geocoding endpoint, which
returns up to five candidate locations. **Your stored state is not included in that request.**
It is used **locally**, on your machine, to pick which returned candidate matches — so that
"Springfield" resolves to the right one. The state never appears in either request URL.

**2. Forecast.** This request happens **only if geocoding succeeded and returned a usable
candidate**. If it returned nothing, RunState stops there and no forecast request is made.
Otherwise the **latitude and longitude of the selected candidate**, together with your **run
date**, are sent to Open-Meteo's forecast endpoint to retrieve daily-mean conditions.

Those coordinates are **Open-Meteo's own published coordinates for the city it matched** —
they are **not** device GPS coordinates and not your precise location. RunState never reads
device location.

**Timing.** The weather lookup happens **before the run is durably saved** — the weather
becomes part of the run at construction. A later save failure therefore does **not** undo an
Open-Meteo request that has already been made.

Open-Meteo is a free, open-source weather service — no account or API key required. See
[open-meteo.com](https://open-meteo.com) for details.

---

RunState is a learning and portfolio project, not a commercial service. Both external APIs
are used solely to power the AI response and weather context features.
