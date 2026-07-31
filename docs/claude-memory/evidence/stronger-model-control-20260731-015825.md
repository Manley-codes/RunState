# Stronger-model control — raw transcript

DIAGNOSTIC ONLY. Not V1 acceptance evidence.

- run at        : 2026-07-31T01:58:25.418263200
- baseline model: claude-haiku-4-5-20251001
- control model : claude-opus-5
- scenarios     : S1, S2, S11, S12
- iterations    : 3 per scenario
- planned calls : 12
- timeout       : 30s per request

## Frozen probe requests — verified before call 1

Source: `docs\claude-memory\evidence\creative-ceiling-probe-20260730-154503-requests.json`

| Scenario | Recorded SHA-256 | Verified | Control SHA-256 |
| --- | --- | --- | --- |
| S1 | `050E05E22871C5CC2EA34954E2C71F1B91DB1746BF89B310A14D8FDF0B1FC1CC` | yes | `399BA22126372E1C2EDF903348433314DD101E8C8A3238E6EB5943D6F74582A2` |
| S2 | `9DA5B6B53B6B870B81E717A9579F14203397657B95868052DB16BC43C468E21C` | yes | `E0BB2CB9FA4A27E6737559CDFA636F25179107F434ADD303E39A3161617A80C1` |
| S11 | `2DAEFF8E0D025FDF844471AD599169FB5CBDEAEFF56D72FA2E47BBE73905442C` | yes | `DADF99309C957EA8973C36E88EF88ECCC88EB0CDA98E49D67546DD26A39250C3` |
| S12 | `3FF23C16834F83C70ADE128D35FA0E753657433C0C699510B324A40F769C6E76` | yes | `9C1A5F306EA60EA4A1820D809AD2FA0ACBE36E7CC13E14E6423FDFBEE1C4C6CB` |

Hashes above are the APPROVED constants pinned in the runner, not values
read from the evidence file. The only difference between each frozen body
and the control body sent is
`"model": "claude-haiku-4-5-20251001"` -> `"model": "claude-opus-5"`. max_tokens stays 256; no temperature,
effort, or thinking field is sent.

## Un-blinding key — DO NOT READ BEFORE GRADING

Grade from the blind packet. Open this only after every output is graded
and the tally is written down.

- blind seed: 53216294482600

| Blind label | Scenario | Iteration |
| --- | --- | --- |
| A | S2 | 3 |
| B | S1 | 2 |
| C | S12 | 2 |
| D | S12 | 3 |
| E | S12 | 1 |
| F | S1 | 3 |
| G | S11 | 3 |
| H | S2 | 1 |
| I | S11 | 2 |
| J | S11 | 1 |
| K | S1 | 1 |
| L | S2 | 2 |
