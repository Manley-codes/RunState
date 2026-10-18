# How RunState music reflections developed

Updated October 17, 2026. Written for Manley to explain the feature, the decisions behind it,
and the mistakes that made the next version better.

RunState aims to help a runner feel seen after a run. The basic numbers come first. Then an
original spoken reflection connects something true about that run with the attitude, meaning
or character of a song the runner heard. A short moment from the recording finishes the thought.
The music can answer the reflection without the reflection naming the song or explaining it.

The October 17 milestone is three accepted automatic reflection and song pairings. Manley's
response was: "These, all three of these, are substantially better than the previous ones."
These were fictional listening scenarios, not three new runs or an outside-runner study.
At acceptance, generation used an isolated backend trial; connecting that process to normal
post-run requests is the next implementation step. Commit and deployment details belong in the
[current project state](project_current_state.md).

## The early experiments established the idea

Before the app could perform the sequence itself, Manley listened to reflections and manually
started a song passage afterward. Many pairings felt good to him. This established the creative
direction: the recording could make the reflection more memorable, playful and recognizable.
It did not establish reliable automation, so repeating that same manual exercise would not solve
the next problem.

The Android work then separated three jobs: notice the current song, create a reflection, and
control a short passage after speech. Reading a song title is different from obtaining audio.
Likewise, Musixmatch's text "snippet" is a lyric excerpt, not an audio file. The working audio
route plays through Spotify on the phone rather than downloading or recording the music.

## Connecting more music data did not immediately improve the writing

The first integration supplied song titles, artists and then Musixmatch theme and mood labels.
Real requests reached Claude, but reflections still often sounded like a run recap with general
encouragement. A successful API response proved that the connection worked; it did not prove
that the reflection was interesting.

Musixmatch supplied useful material: an interpretation of meaning, themes, moods, supporting
passages and sometimes a short text snippet. Its response was not the full song or an authoritative
artist biography. Mood labels alone were too broad to make every record sound distinctive.
Song mood also could not tell us how this particular runner felt.

We tried a more structured compose, edit and review pipeline, and later freer persona writing.
Neither produced a dependable creative improvement at that point. Some attempts invented effort,
struggle, motives, weather or growth. These failures showed that removing constraints alone did
not solve the problem, and that passing format or number checks did not establish factual truth.

## Separating song understanding from writing gave us a better direction

Manley brought a Claude planning handoff into the project. Its useful idea was to study one
matched song before showing the AI any run facts, then let a separate writing request combine
that study with the actual run. The handoff informed the implementation; its proposed examples
and restrictions were reviewed rather than copied wholesale.

The song study describes the record's attitude, delivery, title idea and usable imagery. It can
select only short, verified pieces from supplied lyric material. Artist context stays optional
and interpretive, so an invented biography cannot become evidence about either artist or runner.

An important input gap remained: the writer initially received condensed study notes rather than
the original bounded meaning, themes and moods. We supplied those fields alongside the notes.
That preserved more of what made the particular song interesting. Several resulting drafts still
felt generic or made unsupported claims, so more context was useful plumbing, not an instant cure.

The later accepted examples used Opus 5.5 for the song study and writer. Earlier Sonnet versions
and writing briefs were also tried. Because the study, writing and listening experience changed
across iterations, we cannot attribute the improvement to the model or Musixmatch alone. The
preserved reference is the complete process and accepted pairing.

## The musical finish became part of the writing task

Playing the opening of any song was not enough. Sunday Best initially began with instrumental
material where the intended connection needed a vocal entrance. Stronger sounded better to Manley
from its opening than from a later candidate. God's Plan improved when the selected fragment felt
like a deliberate response rather than the whole song starting again.

The writer now receives a brief description of the intended musical landing when one is available.
That helps it leave room for the recording to answer, instead of announcing or explaining the song.
The accepted passage locations were chosen and checked for specific recordings. This is curated
timing, not a general AI detector that can find the best hook in every song.

The voice sequence needed its own work. The generic lady voice became facts-only, slightly quieter
and slower. The custom ElevenLabs male voice supplies the reflection. A short volume ramp softens
that voice's entrance, and a 650 millisecond pause gives it room before the song. That ramp changes
the player's loudness at entry; it does not redesign the voice's personality.

One early passage kept playing instead of stopping. Later checks verified stop and restoration,
and the control code gained additional cleanup guards and an absolute passage endpoint. The
original failure's exact cause was not reproduced. Slight lyric cutoffs remain a later refinement;
Manley explicitly chose to focus on the reflections rather than keep extending snippets.

## Three different styles earned acceptance

| Example | Fictional situation | What Manley accepted |
| --- | --- | --- |
| Lovely Day by Bill Withers | Three-mile morning run, Feeling Good afterward | A recognizable connection between the morning run and the song's outlook, followed by its chorus. |
| Stronger by Kanye West | Four-mile evening run, no final Energy answer | A very short, confident persona without naming the artist or song. The words and opening shared a strong, smooth landing. |
| Good as Hell by Lizzo | Low before, Powered Up afterward, 28 active minutes | A playful voice with a light musical detail, followed by a short response from the recording. |

The Stronger reflection stays unchanged even though Manley often wants more length: its brevity
worked for that finish. Very short responses should be occasional variety, not the common default.
Direct connection, persona alone and persona with a detail are creative options rather than a
mandatory rotation. An indirect connection can work through delivery, contrast and shared attitude.

The first Good as Hell draft invented a departure, return and brunch, and blurred Energy with mood.
It was rejected before playback. An automatic revision removed those claims; that revised version
was the one accepted. A wrong Spotify link also resolved to a remix and was rejected before audio.
Those corrections matter: a good-sounding finish still needs the right recording and truthful facts.

All four Energy branches were generated for each scenario, but only the selected branch was heard.
Acceptance of those three pairings does not certify every unplayed branch or every future song.
The exact accepted text, facts, recording identities, times, model configuration and prompt
fingerprints are retained in the [accepted baseline](music_reflection_accepted_baseline.md).
The [research record](research_music_reflection_generation.md) keeps the underlying receipts.

## What each platform contributes

| Resource | Its role in this feature |
| --- | --- |
| Spotify and Android media sessions | Observe the currently available title and artist with permission; play, seek and pause the authorized current recording. Spotify supplies the audible song passage. |
| Musixmatch | Match the song and provide bounded song interpretation and supporting material for the study and writer. It does not supply this route's audio or a guaranteed hook timestamp. |
| Anthropic Claude | Study the song, then write four conditional reflections from the study, song context and real run evidence. These are two requests with different jobs, not a fleet of autonomous research agents. |
| ElevenLabs | Speak the selected reflection using Manley's designed voice, saved as `1st test voice`. The first, factual announcement remains Android speech. |
| Android and Room | Keep the run, optional check-ins and saved reflection durable; coordinate voice and Spotify playback without turning listening tests into saved real runs. |
| Render and GitHub | Render hosts the backend used by the phone; GitHub holds the committed source Render builds. A local trial or local edit reaches the hosted app only after the relevant commit is pushed and deployed. |
| Community timing references and listening feedback | Suggest possible passage boundaries, then check how a specific recording actually sounds. Approximate line starts do not establish exact word endings. |

## Decisions worth remembering

- Judge the whole spoken reflection and musical response. Mandatory artist names, literal lyric
  matches or a song explanation can weaken a pairing that already works through personality.
- Keep the runner's facts separate from song interpretation. Distance does not prove effort,
  confidence, hardship or progress. Starting and finishing Energy are separate self-reports.
- A paid catalog can still have missing analysis or match the wrong album, remix or credited artist.
  Do not silently treat another recording as equivalent or reuse its timestamps.
- Preserve exact prompts, model settings, source boundaries, passage descriptions and accepted
  outputs together. A prompt alone does not capture the voice, timing and recording that landed.
- Test the controls as well as the writing. The accepted phone auditions stopped the short passage,
  restored the earlier paused position and preserved all 32 saved records and inspected fields.

## A short explanation Manley can adapt

> I wanted the end of a run to feel personal rather than just read out numbers. I first proved
> the idea by listening to a reflection and then starting a song passage myself. Automating it
> exposed two separate problems: generic writing and awkward audio timing. Adding song labels
> alone did not fix the writing, so I separated song study from reflection writing and gave the
> writer more of the actual song context. I also learned to reject invented run details without
> removing personality. Then I tested three styles with different songs and adjusted the voices,
> pause and song entrance. Those examples finally worked for me. The remaining work is making
> that experience dependable in the normal app across more real runs and available songs.

For a detailed account of provider results, rejected attempts and device checks, use the
[research notebook](research_music_reflection_generation.md). For the current delivery boundary,
use [project state](project_current_state.md) and the [music feature register](music_feature_register.md).
Future work still includes broader song selection, automatic significant-passage discovery,
catalog gaps, genuine-run consistency and any provider requirements for wider distribution.
