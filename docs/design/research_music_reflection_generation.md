---
name: research-music-reflection-generation
description: "Working research notebook for a grounded, music-aware reflection system — source ledger, failure evidence, and provisional system direction"
metadata:
  type: research
  status: active-working-notebook
  updated: 2026-10-16
---

# Music-aware reflection generation research

## Automatic provider integration — October 16, 2026 (current)

Manley approved executing the missing song-information connection, explicitly distinguishing it
from repeated manual reflection/music quality experiments. The live official schema was retrieved
successfully: server `https://api.musixmatch.com`; `GET /ws/1.1/matcher.track.get` with `q_track` and
`q_artist`, then `GET /ws/1.1/track.lyrics.analysis.get` with matched `track_id`; query authentication
is `apikey`. Relevant response fields and embedded status were checked without relying on guesses.

The new adapter and existing Anthropic provider/prompt v1.6 now implement this path for a supplied
unprepared identity. Only short theme/mood labels are used; raw lyric text, quotes, explanations and
entities are discarded. No whole-run ranking, arbitrary Spotify song selection or automatic hook
detection is claimed. Known curated cues bypass external lookup, and failures leave the established
reflection path usable. All **79 normal backend tests** passed with synthetic transport responses;
the explicitly gated live case is skipped by default.

The live Basic page and Stripe checkout showed **$49/month / 100 analysis calls per day**. Manley
explicitly approved the plan and then reported checkout complete/API dashboard available. He handles
the purchase/terms/key creation; no purchase was performed by assistant. An initial checkout click
was auto-review blocked before explicit price approval and retried only after that approval. He was
given a hidden-prompt PowerShell block to save `MUSIXMATCH_API_KEY` in Windows User environment
settings outside Git, with no secret file, and he confirmed it saved.

**Real automatic integration proved:** the opt-in live check passed in **4.938 seconds** for
**Lovely Day / Bill Withers**, a public song with no prepared catalog notes. Musixmatch returned
themes **optimism and positivity; love and support** and moods **love; joy; empowerment; reflection;
inspiration**. A witness asserted that those exact real labels were in the request forwarded by
the production Claude transport, with no lyric/quote/explanation/audio fields or handwritten cue.
Four nonblank candidates were generated from synthetic **3.00 miles of sample distance in 32
minutes**, with conditional Energy. No phone history, listening test or phone configuration change.
Credentials stayed in memory/process environment; none were printed or stored in source. The
process exited normally and left no server or USB route running.

Sample Feeling Good output, retained as engineering evidence rather than creative acceptance:

> 3.00 miles of sample distance in 32 minutes, finishing around 11 p.m. feeling good. You came in at
> moderate energy and turned that into a quality evening run.

This proves one real automatic song-information→reflection connection, not whole-catalog coverage,
user acceptance of this reply, whole-run ranking, hook timing or earlier-track playback. Provider
agreement was not independently cleared. Hosted activation still requires pushed code, Render's
own `MUSIXMATCH_API_KEY` and deployment. No repeat manual-pairing test is needed.

## Execution checkpoint — October 16, 2026

**User review and follow-up:** Manley heard the automated sequence and said it went well. The
connection was not obvious, and the initial 0–10 s played mostly the beat. He wants this song's
early vocal hook; other pairings can fit by atmosphere without a forced direct reference. The
earlier manual notes below had already specified just before the singing. Cue v2 now uses
15.5–25.5 s and a more specific original editorial connection: one good thing to take from
completing the run, respecting each Energy branch. Official catalog/label pages confirmed the
song/early-hook identity but supplied no recording timestamps; bounded community timing lookups
returned no useful synced entries. The automated Spotify audition then passed in 32.821 seconds:
the controlled Feeling Good reply handed off to the revised passage and original paused-position
restoration was confirmed. Manley answered **"Yes, both landed well"** about vocal entrance and
reflection fit. Keep this v2 timing and direction for this original recording; this does not verify
other editions, new songs or automatic chorus detection. The exact six-table/32-run checksum was
unchanged afterward, and temporary backend/USB forwarding/test package were removed.
No lyrics were stored or sent to the reflection provider; no song was downloaded. The test can
choose a fictional Feeling Good branch and expose its generated text solely for connection review.

Manley clarified that earlier manual reflection/song pairings landed well in his experience
roughly 90–95% of the time; this is his reported experience, not a measured acceptance rate.
The next problem is **automation**. The passage should reveal/support the connection in the
reflection, including replies that do not name the artist or song. Select from actual run music,
keep the runner and their real facts central, and aim for recognition, productivity and enjoyment.
Start with one writing AI and reliable app helpers; multiple background research/DJ agents are
not required for this first execution. ElevenLabs was used in earlier listening work, but this
build retains Android TTS and the existing temporary instrumental for unpaired legacy cases.

Execution is now implemented/built/installed for one initial curated song, Sunday Best / Surfaces,
with native current-song playback and independent editorial context. The revised hook offset
has passed the automated listening check above. It is not a claim of automatic chorus
detection, arbitrary earlier-song selection or all-song coverage. Backend v1.5, capability/echo
compatibility and truthful old-host fallback are built. **58 backend tests, 488 JVM tests in each
variant, all APK builds and eight silent Moto tests passed; 32 saved runs/all six user tables are
unchanged.** The explicitly approved real automated Moto check then passed: one live sample
reflection handed off to the selected ten-second Spotify opening and restored the original paused
position, with all six saved-data tables unchanged. Temporary backend and USB forwarding were
removed. Final buffering-interruption hardening and five new regression checks passed in both
488-test suites, with the rebuilt app and eight silent phone checks passing again. No second live
request/listening repeat was needed for that failure-path correction. User feedback is recorded
above; compatible Render activation remains pending. No more broad
provider search is needed before this bounded trial. New file reasons, data boundaries and current
limits live in [the development checkpoint](project_current_state.md#automated-song-passage-first-version--real-automated-phone-check-passed).

## Broader provider review and personal-prototype path — October 16, 2026 (latest)

Manley asked to recover the project's earlier alternatives and broaden the actual-song/reflection
research. His immediate goal is a useful personal/portfolio prototype, not a mainstream streaming
product. Separate practical API/account/playback obstacles from later distribution conditions;
uncertain release scope is not proof that a bounded private mechanics test cannot be performed.
No DRM, access-control or credential workaround is needed for the ordinary routes examined here.
Keep Musixmatch Analysis as a possible later semantic input, with no purchase now.

**Direct answer about Spotify:** the earlier approved Moto experiment really did pause the current
song, move back about five seconds, play about ten seconds and restore/resume. It was temporary
test software, removed afterward, so no new playback button/feature is visible in the installed app.
These current-session controls are a practical first prototype mechanism without a new subscription
or SDK. It is not yet the integrated speech-to-song experience, a chorus detector or proof that any
earlier song can be selected. Do not repeatedly rediscover the successful basic control experiment.
The useful next step is a bounded full sequence with identity/cancellation/restoration checks.

### Recovered history and current-source findings

The review covered current/ignored project notes, relevant source and reachable Git history,
including the old `docs/claude-memory/` location. The original July alternatives were **Last.fm**
and **ListenBrainz** for similarity/discovery, plus GetSongBPM/light tagging for tempo information.
Apple Music appears in the August 3 reflection-to-preview proposal. Later notes add Jamendo and
directly licensed independent songs. No saved project mention of Deezer, Audius, SoundCloud,
Tidal, Qobuz, Napster or 7digital was found before this review; the less-popular app Manley remembers
cannot be identified with certainty. See [original proposal](parked_music_recommendation.md#parked--reflection-to-song-preview-handoff).

| Route | Practical result and fit | Setup or limitation relevant to the next experiment |
| --- | --- | --- |
| Existing Spotify session | **Real Moto control test already passed.** Best first route for a short part of the song currently playing. | Existing notification access; no new developer registration. Earlier-song/URI selection and combined sequence still untested. Rules for distribution remain separate; no blanket OS-controller exemption claimed. |
| Deezer public previews | **Live public search and audio-header checks passed without credentials.** A possible fixed-preview fallback for familiar songs, even if listening happened in another app. | A preview is the provider-chosen passage, not arbitrary access to a full song. Match exact recording/version and skip missing previews. Current developer terms limit audio use to private/family noncommercial circumstances; a conference/public app needs separate consideration. [Public catalog](https://api.deezer.com/search), [terms](https://developers.deezer.com/termsofuse). |
| Apple public previews / MusicKit | **iTunes Search and a preview-audio header check passed.** Separately, native MusicKit Android documents account authentication, in-app playback, seek and pause. | iTunes preview access is not MusicKit integration; promotional conditions apply to previews. MusicKit requires developer credentials, user authorization and applicable subscription access. Not an instant unrestricted replacement. [MusicKit](https://developer.apple.com/musickit/), [preview API](https://developer.apple.com/library/archive/documentation/AudioVideo/Conceptual/iTuneSearchAPI/). |
| Audius | **Anonymous search and real full-stream headers passed.** Fast independent-song prototype with documented app/music-player streaming support. | Different catalog from Spotify. Track-specific license, creator/API settings and attribution matter; streaming rights do not establish lyrics/AI rights. [API](https://docs.audius.co/api/), [Open Music License](https://audius.org/open-music-license.pdf). |
| Epidemic Sound Partner API | Documented free prototype access, real vocal/instrumental songs, mood/BPM metadata and explicit LLM soundtracking/MCP workflows. | Account/API key needed; up to 50 downloads on the free prototype plan, not commercial go-live. Its own catalog rather than the runner's entire Spotify catalog. No account/call tested. [Developer access](https://developers.epidemicsound.com/docs/), [MCP](https://developers.epidemicsound.com/docs/mcp/). |
| Feed Clips | Particularly relevant new lead: actual major-label clips, curated representative sections or user-created portions up to 60 seconds, delivered for app playback. | Credentials, signed 24-hour URLs, play reporting and a scoped agreement/custom pricing. No public instant price or account test. Published terms contain additional combination/retention/territory conditions; exact reflection use belongs in the agreed scope. [Product](https://www.feed.fm/clips/music-api), [API](https://clips.feed.fm/docs/), [pricing](https://www.feed.fm/pricing-page), [terms](https://www.feed.fm/clips-terms-conditions). |
| Jamendo | Still a plausible actual-song alternative: free noncommercial API use, explicit per-track Creative Commons licensing, stream/mood fields. | Own legitimate developer client ID needed. Official documented demo credential returned application error 11 (suspended), despite HTTP 200; no successful catalog proof. No unrelated keys tried. [API terms](https://devportal.jamendo.com/api_terms_of_use), [track fields](https://developer.jamendo.com/v3.0/tracks). |
| SoundCloud | Current official API documents playable/preview/blocked track access and public playback using app credentials. | New application credentials require an Artist Pro subscription; not chosen over the cheaper immediate routes. No account, API credentials or playback test. [Registration](https://developers.soundcloud.com/docs/api/register-app), [guide](https://developers.soundcloud.com/docs/api/guide). |

### What was actually tested this review

Independently chosen **One More Time / Daft Punk** matched on Deezer and Apple's iTunes Search.
One HTTP HEAD per preview followed normal provider redirects and returned **200 audio/mpeg**
(Deezer) and **200 audio/x-m4p** (Apple). These are hosted audio-response proofs, not Android
decoding, audible quality or an unrestricted reuse licence. Original/radio/edit variants were
present, so title/artist alone cannot safely pick a version.

Audius anonymous search returned **Saxfonia - Ritter & MaviC (Original Mix)** by MAVIC, track
`MgEwx`, 331 seconds, streamable and ungated. A HEAD of its documented stream endpoint returned
**200 audio/mpeg**, byte-range support and nonzero length. The track reports All rights reserved;
the provider's Open Music License supports music-player streaming, subject to track/creator/API
conditions, but does not justify arbitrary derivative or external-AI uses. No audio body was read,
played, downloaded or saved in any of these checks. No private listening/run data was supplied.

### Reflection direction and recommendation

The older project already distinguishes **learning what music means to this runner** from buying
generic song labels. Last.fm tags and ListenBrainz/MusicBrainz identity data can help with context,
but neither is a full lyrics-meaning engine or a recording source. A small deliberately curated
set of songs, with our own checked descriptive notes and selected positions, is a candidate for an
honest first prototype without a paid lyric-analysis dependency. Unknown material must remain
unknown. This is a proposal, not newly created song data, a lyrics-ingestion permission or a live
model test. Preserve existing good writing/listening examples rather than restarting calibration.

**Recommended order for this bounded task:** keep Spotify and prove complete own-facts speech →
current-song passage → restoration first; assess Deezer fixed previews as the low-setup familiar-song
fallback; use Audius or Epidemic for a controlled independent-song lane if needed. Apple remains
credible when the extra account/SDK setup is justified. Feed Clips is the most directly relevant
major-label app-clip product found, but pricing/access are not yet known. No forced provider switch,
Musixmatch purchase, full playlist project or third-party account setup was performed. A song chosen
from another catalog as a closing track must not be represented as music actually heard during
the run. The original vision is reflection-song pairing, not merely any available background sound.

Source review also confirms local media observation is already provider-neutral, whereas the cloud
reflection selector deliberately accepts Spotify observations only. Another music app would not
automatically become approved AI input. Existing speech-to-instrumental ownership/cancellation
provides a reusable place for a future passage implementation; it does not need an unrelated player
rewrite. This review changed existing documentation only; no new files, app/device settings,
subscriptions, messages, Git mutations or deployment.

## Musixmatch Basic assessment — October 16, 2026 (latest)

Manley asked whether Google's proposed **$49/month** native theme/mood analysis route is genuinely
useful, and made recorded-song playback after the spoken reflection a condition to settle **before
paying**. This is a research request, not purchase or email authorization. No account, paid API song
request, provider message, phone playback or production change was made during this assessment.

**Finding: promising semantic input; not a complete licensed reflection-and-recording solution.**

- [Basic pricing](https://www.musixmatch.com/pro/api/pricing) includes 100 analysis calls/day within
  5,000 total calls/day. It excludes lyrics caching and synced lyrics. The published restriction is
  specifically lyrics caching; it does not settle whether derived labels or generated reflections
  may remain in saved History. Do not infer either blanket permission or a blanket label ban.
- The [Analysis product](https://www.musixmatch.com/pro/api/analysis) explicitly markets structured
  signals for AI products. The [overview](https://docs.musixmatch.com/api-reference/analysis/analysis-overview)
  and [schema](https://docs.musixmatch.com/openapi.json) describe moods, themes and meaning, including
  an example theme of struggle and resilience. These are provider examples, not live paid results.
  This could support meaningful connections to run facts without sending raw lyrics to Claude.
  Song-level lyrical interpretation is not acoustic energy/BPM, a runner's emotional state or
  evidence that the song caused better performance. No chorus-selection/audio offsets are
  documented in the analysis response. Entity text offsets are not recording timestamps.
- [Current snippet documentation](https://docs.musixmatch.com/api-reference/lyrics-catalog/track-snippet-get)
  includes Basic, but returns **lyric text**, with territory/restriction and tracking fields. It
  supplies no recorded audio and does not guarantee a chorus, hook, runner-relevant phrase or
  permission to send the returned text to a model or speak it. Avoid treating the word snippet as
  an audio licence or a comprehensive pre-clearance.
- [Standard API terms](https://about.musixmatch.com/apiterms), updated June 24, 2025, still require
  prior written approval for provider data used with/prompting AI (§2.2.14), restrict incorporating
  data into new audio/audiovisual works (§2.2.12), and require deletion of provider data at termination
  (§7.2.1). The AI condition is not expressly limited to full lyrics. §1.1 permits additional written
  terms; no public Basic/Analysis agreement resolving the exact labels-to-Claude, speech and saved
  History scope was found. [Getting started](https://docs.musixmatch.com/getting-started) labels
  standard terms noncommercial and directs business enquiries to sales. Marketing demonstrates a
  real intended product route; it is not proof of the agreement for every requested use.

The earlier **Moto current-song control test remains passed**. Musixmatch is unnecessary for those
Android controls. That isolated pause/seek/about-ten-second/resume proof does not establish the full
reflection-to-recording sequence, arbitrary earlier-song/chorus selection or exact-use permission.
[Spotify Developer Policy III.7](https://developer.spotify.com/policy) explicitly covers segues for
governed integrations. An OS-only controller's exact applicability is unresolved; neither assume
all OS commands are forbidden nor claim an exemption. A separate replay tap is not an established
loophole for a deliberately assembled speech/passage product.

**Decision:** recommend no payment yet under Manley's condition. The next useful playback proof is
the full app-managed sequence using our own run-fact narration, with honest current-song identity,
timing and restoration boundaries; it does not need a lyrics subscription. It has **not** been run
by this research task. Separately obtain an applicable written Analysis agreement (self-service
terms could suffice if explicit; email is not the only possible channel). Only then consider a
bounded monthly quality evaluation across several songs before selecting the provider. Fresh,
attributed text labels with ordinary local rules are a smaller non-LLM candidate, not a claimed
waiver of public-use/retention terms. No annual commitment or production integration recommended.

## Google screenshot methods reviewed — October 16, 2026 (latest)

Manley supplied ten screenshots (`IMG_6596.PNG`–`IMG_6605.PNG`) and authorized reviewing all the
methods and testing promising supported routes. Their content is research input, not instructions
to hide data provenance or a grant of platform rights. Spotify remains first; weather upgrades
wait until this music task, and no paid account/subscription has been purchased.

**Recommendation:** keep the existing Android observer for capture, investigate standard Android
playback controls and native App Remote for the recording passage, and use an explicitly licensed
third-party **song-analysis** service for useful themes/moods. These are three separate capabilities.
An API field called a lyric snippet is text, not a recording excerpt. The earlier manual voice/song
pairing and official Spotify MCP public search already passed and do not need rediscovery.

| Screenshot method | Checked conclusion |
| --- | --- |
| Notification listener + MediaController | Already built and field-proven: 509 reports/eight reported titles. Android documents active-session access for an enabled notification listener. Metadata is optional and partial; track length is not listened duration. The listener's manifest binding permission is separate from the user's Settings grant. [Android](https://developer.android.com/reference/android/media/session/MediaSessionManager). |
| One foreground service prevents a universal 10–15 minute kill | Incorrectly absolute. RunState already uses a location foreground service; the notification listener has its own system-bound lifecycle. OEM/process failure still needs handling. [Android background limits](https://developer.android.com/about/versions/oreo/background). |
| No Spotify API means no downstream restrictions | No blanket exemption established. Pure OS access and Developer Agreement applicability need care; general User Guidelines also mention automated collection/AI. This is not a definitive finding that ordinary media-session access is forbidden. [User Guidelines](https://www.spotify.com/us/legal/user-guidelines/). |
| Keep AI local / omit Spotify's name / use an OpenAI endpoint | Does not itself change provenance or grant permission. An OpenAI endpoint is remote processing. Use a supported licensed route instead of hiding the provider. [Spotify terms](https://developer.spotify.com/terms). |
| Web Playback SDK inside the Android app | Browser JavaScript player, not the native Android SDK. App Remote controls installed Spotify and documents play/seek/pause; it needs registration, matching signing fingerprints and user authorization. Exact on-demand track/seek capability and account limits must be verified. [Android SDK](https://developer.spotify.com/documentation/android), [Web SDK](https://developer.spotify.com/documentation/web-playback-sdk). |
| Five users / 250k monthly active users | Current Web API quota materials describe a small allowlisted development mode and organization/250k-MAU extended-quota eligibility. Do not project those exact limits onto every Android OS or App Remote-only integration; no App Remote distribution exemption is established either. [Quota modes](https://developer.spotify.com/documentation/web-api/concepts/quota-modes). |
| MusicBrainz for genres/moods | Core identity metadata is CC0; supplementary tags have different licensing. Useful for identity matching, not an unrestricted lyrics/moods/audio service or a way to relabel Spotify-derived observations. [Data licence](https://musicbrainz.org/doc/About/Data_License). |
| Genius API for full lyrics | No documented full-lyrics endpoint found. Official authenticated resources cover metadata, artists, annotations/referents and search; scraping lyric pages is a separate operation. [Official docs](https://docs.genius.com/). |
| Musixmatch / another licensed provider for interpretation | Promising: current Musixmatch Analysis product explicitly describes structured song signals as AI inputs. Standard terms still require written AI approval and contain audio-work restrictions. Resolve the actual product agreement, retention and spoken-reflection scope before purchase. [Analysis](https://www.musixmatch.com/pro/api/analysis), [API terms](https://about.musixmatch.com/apiterms). |
| Apple MusicKit alternative | A real native Android authentication/playback SDK, with its own agreements and user-initiation/full-song/control requirements. Not automatic clearance for the requested combined reflection/excerpt. Spotify remains first. [MusicKit](https://developer.apple.com/musickit/). |

### Concrete tests and newly useful leads

- **Native current-song passage control passed on the Moto.** After Manley left Spotify playing,
  read-only inspection found seek/pause/URI/search control flags. A temporary instrumentation test
  then used RunState's actual target context and existing notification-listener grant, confirming
  those capabilities without Spotify SDK/client credentials. Manley separately approved an actual
  interruption: current-song pause → seek about five seconds back → play about ten seconds → pause
  → restore the original position and resume. The final 11.979-second test reported inspection,
  playback and restoration confirmation all true. One earlier attempt stopped before commands
  because a prerequisite was not confirmed; a diagnostic read then found metadata/position/duration
  and adequate remaining time. No title, artist, URI or position was printed or sent off the phone.
  The test never selected another song, changed a queue or started a run. This is a real SDK-free
  current-session control route, not merely a mock or an ADB-shell privilege assumption.
  Arbitrary-song `playFromUri` was advertised but **not executed**; exact audible termination,
  different-device/account behavior and automatic reflection→passage permission remain unproven.
  [Android controls](https://developer.android.com/reference/android/media/session/MediaController.TransportControls).
- **Temporary-file/device cleanup completed:** the sole temporary source was
  `android/app/src/androidTest/java/com/runstate/mobile/music/TemporarySpotifyControlProbe.kt`.
  It was needed to prove actual app-permission access and the approved control sequence. It and
  the temporary `.test` phone package were removed, normal test APK regenerated without the probe,
  and RunState reopened. All **32** saved runs and every original five-table stored field retained
  SHA-256 `618c51b4f1154f4d84d1076f0363628da9455cd87cee4d7c37a62769ba8e5a76`.
  This round changes no production app behavior, app permissions or preferences and adds no
  permanent source/asset/document file. No Git mutation, deployment or purchase occurred.
- **Independent catalog API passed:** one MusicBrainz public recording query for independently
  chosen `Lovely Day` / Bill Withers returned HTTP 200 and 38 matches. The first three core results
  had the same title/artist and score 100 but different recording IDs/lengths. Title/artist alone
  cannot safely select the exact recording. No phone song data was sent and no local file created.
- **External analysis demo passed:** LyricIQ public search and the exact `Lose Yourself` / Eminem
  standard-title report showed top emotion Anger, sentiment Somewhat Positive and top theme
  Coarse Language. This proves a real semantic service, but that content label alone is not a rich
  motivational interpretation or evidence of the runner's feelings. Other versions appeared in
  search. No account, lyrics quotation, audio or private run/listening data was used. Two additional
  proposed comparisons were not performed after the browser became unavailable; do not count them.
  [Official product](https://www.lyricfind.com/lyriciq), [public demo](https://lyriciq.lyricfind.com/).
- **Paid analysis lead verified in the live provider pages:** Musixmatch Basic lists **$49/month**,
  100 analysis calls/day, 500 lyrics calls/day, 5,000 total calls/day and no lyrics caching. The
  explicitly AI-oriented Analysis product makes this a practical lead; payment is not yet approval
  for every use. No free plan was listed. [Current pricing](https://www.musixmatch.com/pro/api/pricing).
  The Claude integration documentation currently says it is forthcoming; no ready MCP hookup was
  inferred. [Integration page](https://docs.musixmatch.com/integrations/claude).
- **Analysis response shape inspected, not a paid API song call:** initial semantic candidates are
  `message.body.analysis.moods.main_moods` and `themes.main_themes[].theme`. Allowlist only needed
  bounded fields after permission; discard `quotes`, matched lyric text, entity matches and other
  unnecessary classifications. `meaning.explanation` is free text and cannot be presumed free of
  quotation. Validate `message.header.status_code` as well as HTTP status.
  [Overview](https://docs.musixmatch.com/api-reference/analysis/analysis-overview),
  [public schema](https://docs.musixmatch.com/openapi.json).
- **Actual recording remains separate:** iTunes previews have promotional-use restrictions; lyric
  licences do not grant master-recording playback rights. Jamendo National/International individual
  licences explicitly include apps and passage edits, while Standard excludes apps. That is a
  possible licensed-recording fallback, not a licence to Spotify's catalog or an automatic provider
  switch. [Apple preview conditions](https://developer.apple.com/library/archive/documentation/AudioVideo/Conceptual/iTuneSearchAPI/),
  [Jamendo app licensing](https://support-licensing.jamendo.com/individual-licenses).

### Musixmatch clarification request — prepared only, not sent

Hello Musixmatch team,

I'm building RunState, a native Android running app and computer-science portfolio project. Your
Analysis product describes structured song signals as inputs for AI products, and Basic currently
lists $49/month with 100 analysis calls/day. I would like to confirm the agreement for a bounded use
before subscribing.

We want to obtain a correctly matched song's theme/mood labels, send only those approved derived
fields to Anthropic Claude alongside our own run facts and optional runner feedback, then show
and speak a short generated post-run reflection using Android TextToSpeech. We would not send
raw lyrics, quotations, matched lyric text or a recording to the model. Musixmatch would receive
track identifiers, not our GPS route, runner feedback or listening-history list.

Does Basic cover this use, including the written AI approval described in §2.2.14 and any relevant
audio-work condition in §2.2.12? May we retain the minimal approved labels as frozen evidence with
the run and retain the generated reflection, given the plan's caching restrictions? What attribution
and conditions apply to a private student prototype, a conference demonstration and later public
distribution? If a separate agreement is needed, please identify the appropriate plan and process.

Song identity may originate from an opted-in Android media-session observation; platform-specific
downstream-use questions remain separate and we do not assume your licence waives another
platform's terms. We also understand that lyric analysis does not license a recorded-song passage.
Please confirm the specific permitted scope in writing before we purchase or integrate it.

## Deeper Spotify review — October 16, 2026 (latest)

**Spotify remains first choice; public search through its official MCP server and Claude API now works.**
Manley's intuition about deeper integrations is supported. Sources were checked October 16;
older announcements are historical evidence, not confirmation that those features still operate.
This review supplements the earlier checkpoint below: a bounded custom-backend call succeeded,
while account authorization and RunState's exact permitted uses remain unresolved.

### Official Spotify MCP lead and bounded access result

Anthropic's [verified Spotify connector](https://claude.com/marketplace/connectors/spotify) is marked
Made by Spotify, added April 2026, and publishes `https://mcp-gateway-external-pilot.spotify.net/mcp`.
The directory lists `create_playlist`, `get_currently_playing`, `search`, `add_to_library`,
`fetch_tracks` and `Remove_from_library`; this differs from the actual five-tool server listing
observed below. Neither list includes seek, pause or timed-excerpt control. The directory says
sign-in is not required, but this does not establish unauthenticated access to account data.
Anthropic's [remote MCP server catalog](https://platform.claude.com/docs/en/agents-and-tools/remote-mcp-servers)
also lists Spotify for the Claude API. Its [MCP connector guide](https://platform.claude.com/docs/en/agents-and-tools/mcp-connector)
documents custom Messages API integration, tool allowlists and developer-provided OAuth tokens.
This is a documented backend lead, not merely a consumer-product announcement or unofficial wrapper.

An earlier direct, unauthenticated `initialize` request identified `RunStateFeasibility/0.1` with
protocol `2025-06-18` and received HTTP 403 HTML without a `WWW-Authenticate` challenge. It read
no account/music data and established no session. That local result did not establish that the
documented Claude API route was blocked.

The subsequent approved proof used Claude Messages API with `mcp-client-2025-11-20`, Spotify's
published endpoint, all tools disabled by default and only `search` enabled. A public easy-run
track query returned HTTP 200, one actual `mcp_tool_use` named `search`, and `mcp_tool_result`
with `is_error=false`. **Public search through a custom Claude API request is proven.** No Spotify
account, listening history, currently-playing state, playlists, library or RunState run facts were
used. The existing approved Anthropic key went only to Anthropic; no account changes or persistent
files were made. Usage was 4,347 input/273 output tokens; `stop_reason=max_tokens` means this is
tool-path proof, not a completed prose-quality evaluation.

A second approved Claude API call used the documented `mcp-client-2026-09-15` interface, all tools
disabled and `tool_choice=none`. It returned HTTP 200 with `mcp_tool_listing` and text, `end_turn`,
30 input/8 output tokens and **zero tool calls**. The actual server advertised five tools:
`search`, `remove_from_library`, `save_to_library`, `generate_playlist`, `get_currently_playing`.
There were two Anthropic calls total: the public-search proof and this capability listing.
No Spotify account was linked. These returned descriptions report server capabilities/boundaries;
they are not a general grant for run correlations, AI reflections or other downstream uses.

- `get_currently_playing` requires an authenticated Free or Premium user and an explicit, direct
  user request about current playback. Its description says not to call proactively or for other
  playback requests. Do not assume this tool supports automatic background run observation.
- `search` is described as read-only, supporting public/activity/similar searches and authenticated
  personal library/history searches. It requires a prompt and BCP 47 language, plus Spotify links
  with listed entities. It is not for lyrics, biographies, transcripts, music facts/news or audio
  analysis. Public search success does not prove the authenticated variants work for RunState.
- `generate_playlist` requires Premium and explicit playlist-creation intent; playlist generation
  remains outside the requested reflection/passage scope. No seek/pause/timed-passage tool appeared.

No further API calls are authorized in this checkpoint.

The connector's Documentation/Support links lead to general Spotify Support. Request guidance on
account authorization/currently-playing access and the applicable terms for RunState's own Claude
API backend, plus permission for song/run correlation and generated reflections. Ask specifically
whether an approved automated same-run observation route exists without violating the direct-user-
request restriction. These questions remain open; custom API access is not universally blocked.
Timed passage control is a separate unresolved capability and permission question, and Premium
is not established as its solution.

| Official evidence | What it establishes, and its limit |
| --- | --- |
| [Nike/Spotify, July 2015](https://community.spotify.com/forum/en/discussion/1174914/nike-running-delivers-new-ways-to-motivate-runners) | The announced partnership offered iOS Premium Pace Stations: target pace/preferences informed BPM and a personalized 100-song playlist. Current [Nike help](https://www.nike.com/help/a/nrc-music) documents Spotify catalog access on iOS and separate-app listening; it does not establish that Pace Stations still exists. |
| [Runkeeper's 2015–January 2016 analysis](https://runkeeper.com/cms/training/this-music-may-help-you-run-faster/) | Runkeeper really published song/pace correlations and described a personal pace-per-song recap. Current [music help](https://help.runkeeper.com/en/hc/how-to-listen-to-music-while-using-runkeeper) describes a Spotify redirect, external Android listening and music ducking/muting for cues. Its legacy Google Play Music wording limits confidence about current app details; historical analytics permission is not established today. |
| [Strava launch, April 12, 2023](https://newsroom.spotify.com/2023-04-12/spotify-strava-integration-stream-track-workout/) and [current Strava help](https://support.strava.com/en-us/articles/15401669-spotify-and-strava) | Play/pause/resume/skip/browse was a genuine integration, but Strava now says it is discontinued. The help page gives no exact discontinuation date. Separate Spotify listening remains possible. |
| [adidas Nite Jogger case study](https://ads.spotify.com/en-US/inspiration/adidas-nite-jogger-case-study/) | Spotify describes a custom campaign using listening profiles, tempo/mood and personalized playlists. This is evidence of direct collaboration, not a current adidas Running AI permission. The [January 2025 adiClub offer](https://newsroom.spotify.com/2025-01-21/set-a-course-of-self-improvement-with-spotify-plus-a-special-offer-from-adidas/) separately concerns Premium trials. |
| [Spotify Running Mode, July 30, 2026](https://newsroom.spotify.com/2026-07-30/running-mode-playlist/) | Spotify itself offers workout/tempo/taste-based music and optional spoken cues; launch was Premium iOS in seven markets including the US. No public third-party Running Mode API was found. |
| [Spotify conversational AI, July 14, 2026](https://newsroom.spotify.com/2026-07-14/talk-to-spotify-announcement-beta/) and [selected DJ integrations](https://support.spotify.com/us/article/dj-integration/) | Spotify's own AI can discuss listening; approved DJ products can mix up to four tracks subject to their conditions. These are real product-specific capabilities, not permission for any external LLM or new app to do the same. |

### Permission, payment and the next useful path

The [general Spotify User Guidelines](https://www.spotify.com/us/legal/user-guidelines/), item 5,
also restrict automated information collection and AI ingestion. This is a stronger barrier than
the earlier uncertainty about whether the Developer Agreement covers a pure Android observer:
**no SDK/API does not establish an exemption.** The exact treatment of OS-reported title/artist
still needs written clarification; this research is not a definitive ruling that Android's media
session mechanism itself is prohibited. Consent and inference without training are not documented
exceptions to these restrictions. The existing opted-in cloud path is not newly cleared by this review.

[Premium](https://support.spotify.com/us/article/your-premium-benefits/) buys listening features;
the [App Remote README](https://github.com/spotify/android-sdk/blob/master/app-remote-lib/README.md)
requires it for single-track playback. It does not buy external AI, correlation or excerpt rights.
[Developer Terms IX.1](https://developer.spotify.com/terms) recognizes an executed Separate Agreement,
so a custom arrangement is a legitimate question, not a promised outcome. No public self-service
paid licence for RunState's exact AI-plus-passage use was found. The organization/250,000-MAU
[Web API partner criteria](https://developer.spotify.com/documentation/web-api/concepts/quota-modes)
are not a realistic current student route; extra quota is not a waiver of content restrictions.

If the intended use is cleared, the smallest playback proof is a user-directed foreground App
Remote controller for a chosen track: register the app, obtain consent, verify on-demand/seek
capabilities, then exercise play/seek/pause and observed state. The earlier checkpoint records its
limits; neither exact ten-second termination nor complete playback restoration is guaranteed.
Ordinary audio-focus ducking for run announcements is a separate behavior from deliberately
sequencing a generated reflection into a selected passage. Do not declare all ducking prohibited.
Manley **already** heard the manual reflection-then-Spotify pairings and liked them; that experiment
is recorded below and does not need repeating as a new discovery or permission test.

### Independent evidence and previously considered alternatives

[MusicBrainz core data](https://musicbrainz.org/doc/About/Data_License) offers a genuinely independent
CC0 metadata basis; [its database breakdown](https://musicbrainz.org/doc/MusicBrainz_Database)
distinguishes core titles/artists/identifiers from supplementary annotations/tags/ratings under
CC BY-NC-SA. API service conditions are [separate](https://musicbrainz.org/doc/MusicBrainz_API), and
[MetaBrainz does not license recordings](https://metabrainz.org/contact). A candidate design could
use independently obtained core metadata plus the runner's original notes and RunState's own facts.
Mapping captured Spotify titles to MusicBrainz IDs, copying them manually or forwarding them through
scrobbling does not establish independent provenance or clear the original downstream restrictions.
CC0 metadata also does not grant lyrics, artwork, recording or composition playback rights.

Earlier project candidates are preserved in [parked music recommendation](parked_music_recommendation.md):
Last.fm/ListenBrainz similarity and separately licensed lyrics such as Musixmatch. They are not newly
approved providers, substitutes for recording rights, or a reason to repeat the old idea search.
Apple MusicKit and Jamendo were checked in the earlier checkpoint below and have their own limits.
A directly licensed published song remains a concrete fallback if Spotify cannot permit the exact
experience: obtain recording/composition, passage-editing, app/demo distribution and any AI-input
permissions explicitly. No replacement song or provider switch is selected.

### Ready-to-send clarification request — prepared only, not sent

The [Spotify Developer Community](https://community.spotify.com/forum/en/categories/spotify-for-developers)
is an official starting point; [Spotify support](https://support.spotify.com/us/article/contact-us/)
can also be asked to route the question. Request the responsible policy/partnership team and written
scope confirmation; an ordinary forum/support answer is not itself an executed Separate Agreement.

Hello Spotify Developer Support,

I'm a computer-science student building RunState, an Android running app and portfolio project.
Anthropic lists Spotify's official MCP server in its connector directory and Claude API catalog.
We successfully called its public search tool through our own Claude Messages API request, with
only search enabled and no Spotify account data, listening history or run facts supplied. An earlier
direct unauthenticated initialization returned 403, but the documented Claude API route succeeded.
One subsequent tool-listing-only request, with all tools disabled and no tool execution, showed
five server tools. The currently-playing description requires an authenticated Free or Premium
account and a direct user request about current playback; it disallows proactive calls. We have
not linked a Spotify account or used that tool. Please confirm the permitted scope for RunState,
supported account authorization and applicable terms, including student or paid options. Is there
an approved automated same-run observation route that does not violate this direct-request
restriction? May permitted metadata support song-versus-run correlations and generated reflections,
and does that permission differ from Android, App Remote or Web API metadata?

I also want to clarify the existing observation route. The prototype can observe
Spotify title, artist and playback state through Android media sessions with notification access,
without Spotify APIs or audio capture. A separate opt-in can send one observed title/artist to
Anthropic for inference in a post-run reflection. Does this fall under the User Guidelines' AI or
automated-collection restrictions, and would the answer differ for App Remote/Web API metadata?
Would linking observed songs to independently recorded run facts and runner feedback for personal
song/run-fit insights require separate permission, even if the analysis stays local?

The reflection would use our own measured run facts and optional runner feelings; music would not
change those facts. We also want an optional ten-second passage of an actual song after the speech
finishes, played inside Spotify through App Remote with Premium and user authorization, without
extracting, recording, remixing or redistributing audio. Please distinguish a separate user tap to
start the passage from automatic sequencing after speech. Is either permitted, including seeking
to a selected timestamp and pausing after the passage? Is there a supported way to preserve the
runner's prior playback context? This request is separate from ordinary OS audio ducking for run
announcements while the runner independently listens to Spotify.

Please explain any different conditions for a private student prototype, a conference demonstration
and later public distribution, including App Remote-only registration/user limits. If any part needs
a written exception or Separate Agreement, which team handles it, what eligibility applies, and is
there a paid licensing or partnership route open to a project at this stage? Please identify the
specific permitted scope and relevant terms rather than treating Premium or extra API quota as
permission for AI processing or the combined audio experience. Thank you.

## Spotify feasibility checkpoint — October 16, 2026

**Spotify remains the first choice.** This checkpoint records official-source research and open
permission questions, not a completed integration, account setup or provider approval. Preserve the
earlier creative and device findings below as history; they do not clear these provider constraints.

### Confirmed capabilities and restrictions

- **Playback route:** App Remote controls playback inside the installed Spotify app. Its
  [PlayerApi](https://spotify.github.io/android-sdk/app-remote-lib/docs/com/spotify/android/appremote/api/PlayerApi.html)
  exposes play, seek, pause and player-state subscriptions as separate operations. Spotify's
  [SDK README](https://github.com/spotify/android-sdk/blob/master/app-remote-lib/README.md)
  requires Premium for a single-track URI and checking the user's on-demand capability.
- **Setup:** the [Android getting-started guide](https://developer.spotify.com/documentation/android/tutorials/getting-started)
  requires developer registration/client ID, redirect URI, package/fingerprint registration,
  installed Spotify and user authorization for `app-remote-control`. Android notification access
  is a different permission and does not supply this authorization.
- **Policy:** [Developer Policy](https://developer.spotify.com/policy) III.7 prohibits segues,
  mixing and overlap with other audio; III.13 prohibits analysis of Spotify Content/Service,
  including derived user metrics/profiles; III.14 prohibits AI training **and ingestion** of Spotify
  Content. V.2 limits preview clips to promotion and excludes personalization/messaging products.
  [Developer Terms](https://developer.spotify.com/terms) II.8 includes metadata and user data in
  Spotify Content; IV.2.1 repeats the AI restriction. These are not audio-only or training-only rules.
- **Web API scale:** [quota modes](https://developer.spotify.com/documentation/web-api/concepts/quota-modes)
  currently allows five authenticated, allowlisted development users and requires a Premium app
  owner. Its published extended-quota criteria include an organization and 250,000 monthly active
  users. Scope this finding to Web API; App Remote-only distribution is not resolved here.
  The [July 2026 changelog](https://developer.spotify.com/documentation/web-api/references/changes/july-2026)
  raises client IDs per developer from one to 25, superseding the older one-ID limit; development
  quotas are shared per developer account. This does not expand the five-user allowance.
- **Android observation:** [MediaSessionManager](https://developer.android.com/reference/android/media/session/MediaSessionManager)
  permits enabled notification listeners to obtain active-session controllers. This establishes an
  Android access mechanism, not Spotify permission for every downstream use.

### RunState interpretation and unresolved boundaries

- App Remote supplies candidate controls for playing a chosen passage, but no documented atomic
  play-at-offset-for-ten-seconds operation or complete queue/context restoration transaction.
  Separate play/seek/pause calls cannot establish an exact hard stop or unchanged restoration;
  process loss, connection failure and user intervention require a later bounded reliability design.
- A generated spoken reflection followed automatically by a Spotify passage raises the III.7
  segue restriction even when the sounds do not overlap. Treat this as a permission barrier pending
  explicit clarification; the existence of playback controls does not clear the combined experience.
  Preview clips are not a substitute licence for personalized post-run audio.
- For Spotify API/SDK data, sending title/artist into an LLM appears incompatible with the ingestion
  prohibition. Deriving song/run-effort relationships or a personal trust ledger also appears within
  the analysis restriction. Local deterministic analysis and runner consent are not stated exceptions.
- Pure Android media-session observation without Spotify APIs/SDKs is a different technical route.
  Applicability of the Spotify developer agreement to that route remains unresolved; no official
  exception clearing observed Spotify metadata for AI or run correlations was found. Do not describe
  the route as provider-cleared or issue a definitive contractual ruling from this research alone.
- The shipped, separately consented one-title/artist cloud-reflection path is recorded in
  `design_music_selection_system.md`. Its technical success and user consent do **not** establish
  Spotify clearance. This checkpoint records that gap; it does not change code or stored replies.

### Recommended next bounded decision

Keep Spotify first while resolving the exact use case before implementing a passage feature or
expanding music intelligence. A user-directed [Open in Spotify link](https://developer.spotify.com/documentation/android/tutorials/content-linking)
and reflections based on independently collected run facts/runner feelings are candidates for a
simpler interim scope. Independently licensed music/metadata is an alternative to evaluate if the
Spotify barriers prevent the intended experience; it is not a selected replacement.

**Draft clarification questions for Spotify — not sent:**

1. Is a user-triggered spoken post-run reflection followed by a short Spotify passage allowed under
   III.7, and does automatic sequencing require a separate agreement?
2. Can one Android-observed Spotify title/artist be supplied to an LLM solely for a reflection,
   without training, and does the answer differ for App Remote/Web API metadata?
3. May independently recorded run facts and runner feedback be linked to observed tracks to derive
   personal run-fit relationships, or would III.13 require separate written permission?
4. What registration/distribution limits apply to an App Remote-only personal demo and later public
   app, and what attribution/display obligations apply to this exact experience?
5. Is there a supported bounded-passage/restore route that preserves the user's playback context,
   and what agreement would be needed if the standard developer terms do not cover it?

### Alternatives checked after the Spotify barriers

Spotify remains first. [Apple MusicKit](https://developer.apple.com/musickit/) also supplies an
Android player, but [Apple's agreement](https://developer.apple.com/support/terms/apple-developer-program-license-agreement/)
section 3.3.6(D) requires user-initiated playback, full-song access and standard controls, restricts
synchronization, and limits music metadata to the playback/playlist experience. It is not a cleared
replacement for this automatic personalized passage. No external-AI permission was established.

[Jamendo Licensing's sale terms](https://licensing.jamendo.com/en/legal/termsofsales) contemplate
licensed cuts/fades and audio synchronization, with mobile distribution depending on the licence.
However, [its use terms](https://licensing.jamendo.com/en/legal/termsofuse) separately restrict
AI/text-data mining. Whether one app licence covers many generated personal reflections also needs
an explicit answer. No account, purchase, download or alternative-provider integration was made.

A published independent song licensed directly for the exact use is a narrower fallback worth
evaluating if Spotify cannot permit it. Verify recording and composition permissions, passage
editing, Android distribution/demo use and attribution; separately establish any material supplied
to the reflection model. A playback licence is not automatically an AI licence. No replacement song
or new audio asset has been selected; the approved original instrumental remains in place.

### Local execution from this research

Prepared, without installing on the phone during the GPS test: a manual Start-screen Spotify
launcher, per-controller observation failure isolation, and History that retains run/reflection
records when an optional music read fails. These do not authorize an SDK, choose a track, create
a snippet, change preferences, expand cloud sharing or claim complete soundtrack coverage.
Build/test evidence and the exact file ledger are in `project_current_state.md`.

## Status and purpose

This is a **working research notebook**, not a final production prompt or implementation plan. It
keeps sources, user feedback and limitations together. The October 3 checkpoint contains the approved
first-version creative direction; the delivery contract lives in `design_run_response_system.md`.
The earlier research-first pace below is historical, not the current implementation queue.

**Current October 14 checkpoint:** live reflection text/reopen and one Moto screen-off GPS
capture check have passed. Manley approved a temporary original instrumental after Completion
speech; its local integration and provenance are recorded in the October 14 source decision below.
Final build, all 76 emulator screen cases and Moto receipt → live reflection → instrumental
hearing passed. Actual-song pairing remains later. Manley subsequently reported Render Live at
`bcde0c4`, containing no-selection prompt v1.3, and public health returned 200/ok. A subsequent
connected-Moto Sample passed bounded live writing/cold-reopen QA: factual no-selection wording,
no invented/missing feelings or question, 21 completed runs with prior 20 retained and exact stored
fields unchanged across restart. This one output does not establish broad quality. The earlier
instrumental hearing check used hosted v1.2. A second fresh v1.3 Sample then passed physical
Spotify compatibility: Manley heard facts → reflection → cue while Spotify became quieter or
paused and returned normally by itself; focus logs and PLAYING status support that handoff.
Music observation/sharing stayed off and RunState issued no Spotify play command or song/audio
sharing. There are now two bounded no-selection output passes and 22 completed runs, with every
prior 21 run/reflection/transition/location field hash unchanged. This one Moto/session result does
not establish wider-device/call/Bluetooth compatibility or general writing quality. Active visual
review remains open after the bounded native pass recorded in `project_current_state.md`;
actual-song pairing stays open.

Manley's direction as of September 17, 2026:

- Build a repeatable **system** for producing and evaluating reflections instead of hand-writing a
  separate answer for every combination of run, feeling, and song.
- Keep Manley directly involved in music and reflection choices. Research may inform those choices;
  it does not make them automatically.
- Research replies should be organized and relevance-first. Lead with the few findings that matter
  now, distinguish evidence from inference, and leave full source detail behind links unless Manley
  asks to go deeper.

## Resume point — October 3, 2026

**Creative baseline accepted for first implementation; improve it later.** Manley clarified that
wrapping up means moving to a functional generated-reflection feature in Android, not parking the
writer and implementing only stored samples. Continue with Anthropic, updating the older prompt
and integration where needed. Start with controlled run/music inputs; live text generation moves
ahead of GPS and automatic music capture. Do not require another writing batch, research round,
or the historical 36-output evaluation before beginning implementation. This is a product decision
to build a first version, not evidence that general generation quality is already reliable.

- **Ready to retain:** the accepted examples below, factual boundaries, and read-first/listen-second
  ElevenLabs setup. These are a provisional creative reference and possible controlled demo material,
  not a proven automatic writer or a production voice choice.
- **Latest accepted reuse:** Manley said both examples below work. No separate reading/listening
  grades or repeated-use judgments were supplied. Both use fictional facts and assume
  "Run It Up" by Offset & Key Glock (2025) played; no actual playback is established.
  - Morning: 2.7 miles / 29 minutes, finished 6:45 a.m., Low → Spent, no PR or effort rating.
    "2.7 miles before seven, with low energy at the start. You ran it up early. Now you’re feeling
    spent—sometimes this is what putting in the work looks like."
  - Evening: 3.2 miles / 35 minutes, finished 6:50 p.m., Moderate → Feeling Good, no PR.
    "Run it up then! 3.2 miles in 35 minutes, and you finished feeling good—that’s a real quality run."
- **Still unproven:** dependable fresh generation, range across repeated runs, and automatic creative
  screening. Two text-only AI reviewers matched 2/8 and 4/8 of a small set of existing user ratings;
  neither result establishes a reliable replacement for Manley's judgment. Near-verbatim reuse is
  not evidence of creative variety. The examples also do not form all four Energy branches for one
  shared run, so they must not be combined as if their different metrics described the same run.
- **Actual implementation baseline:** Android still uses Room v3 and ends at Saved with fixture
  metrics and Start another run. The Java console already calls Anthropic. Neither the new mobile
  backend nor the generated reflection/History journey exists. The first-version delivery brief
  now lives in `design_run_response_system.md`; storage is one part, not the whole milestone.
- **Scope boundaries:** inputs must remain clearly identified as fixtures and match the saved run.
  Never attach fixed-distance, early-morning or music claims from these examples to an unrelated
  emulator run. GPS, automatic music capture, snippets, ElevenLabs app integration and visual polish
  remain later. No new runner questions. Keep the current read-first/listen-second setup for optional
  manual review; do not add automatic audio generation to the implementation task.

Exact code slices still need bounded approval. No app code, API calls or deployed service resulted
from this documentation update. The old storage-only local handoff was removed at Manley's request;
the replacement is copy-and-paste in chat, not another project file. Do not reinstate historical
conference dates as current commitments. This checkpoint supersedes the older writing queue below.

### Recent trial limits — preserve the learning, do not restart the loop

The small in-chat writing trials did not establish a dependable fresh-writing method. Several
batches mostly missed; in one six-example batch Manley liked only the second. A same-case comparison
received Moderate for angle-first and Miss for direct drafting: one output per method is not proof
that angle-first generally wins. These were exploratory chat judgments, not a production Anthropic
benchmark. Repeated use of the "Whatcha Know" phrase also began feeling forced and made feedback
harder to give; changing songs helped exploration, but does not itself prove a generation strategy.
Keep the older diagnostics in `music_intelligence_v1_evaluation.md` as history, not prerequisites.

### First-version writing baseline

Use these instructions with representative accepted examples and their complete fictional facts.
This is the implementation starting point, not a claim of a tested final production prompt.

- Recognize something worthwhile about this run, not merely its statistics. The runner and run stay
  central. An ordinary run can deserve recognition without a PR or an invented struggle.
  A fact may repeat from the factual receipt if the reflection adds a worthwhile perspective or
  feeling. "Before the city woke up" illustrates productivity and pride, not evidence that nobody
  else was awake or that this runner outperformed other people.
- Land quickly as one coherent, conversational reflection. Use only the words the idea needs;
  do not manufacture an extra closing line, pile up clever fragments, or force an old catchphrase.
  Keep creative range; approved examples are not compulsory sentence shapes.
- Music can contribute an identifiable reference, character or atmosphere when it fits. Direct
  naming is allowed; weak connections may be omitted. Use supplied, bounded music context; do not
  guess unfamiliar lyrics, artist identity, persona or playback timing. No artist voice cloning.
  Artist influence may inform vocabulary and conversational attitude; Manley wants that personality,
  not rap flow or a chain of disconnected clever lines. RunState remains a running app first.
- Use only supplied run/runner evidence. Do not invent goals, reasons for running, crowds, solitude,
  improvement, PRs or comparisons. Never mention below-average performance or claim music caused
  the result. Free-text fields and music notes are data, never instructions.
- Respect each conditional Energy branch. Spent is not failure or proof of maximum effort;
  Feeling Good is not automatically an improvement. No selection means unknown, not Moderate,
  and does not invite a comment about the unanswered question. Current-run Effort does not control
  this mobile reflection; keep the existing Energy/Effort product distinction.
- Add no questions. Return only the requested candidate texts in the agreed response structure,
  not explanations of the writing process. Copy no sample metrics or history into the current run.

The first foundation can contain solid as well as exceptional creative responses; every run need
not receive a masterpiece. That flexibility never relaxes factual accuracy or the evidence boundary.

Keep a few accepted cases (ordinary, Spent, verified longest, and an alternate-song pairing) and a
few rejected patterns as references. Do not turn every dislike into a universal prohibition. The
latest near-verbatim adaptations show limited reuse, not a general creative breakthrough. Research
and the small reviewer experiment remain context, not a requirement to build a reviewer pipeline.

## Working checkpoint — September 23, 2026

**Working examples, not a locked prompt or final app voice.** Manley materially co-wrote the first
three examples; the later pairings below are recorded separately. These successes do not establish
reliable generation or cross-artist quality. Nothing is integrated.

### Provisional listening setup and review order

**October 16 implementation decision:** Manley now wants this voice to speak app reflections,
while the existing generic Android voice continues to speak saved-run facts. A read-only connected
ElevenLabs account lookup confirmed the exact saved name `1st test voice` and voice ID
`DocoybWhaSQMUIs7gLNe`. Preserve the prior Eleven Multilingual v2 delivery initially. This is a
chosen integration direction, not an installed change: backend credentials, bounded exact-text
speech delivery and cancellation-safe audio playback remain to implement after hosted music
activation. Only the selected final reflection should be synthesized, followed by the existing
song handoff; do not synthesize all four Energy candidates or restart manual pairing experiments.

**October 17 implementation checkpoint:** the reflection-only backend/Android path is now built
and installed on Moto, with the fixed voice/model above, stored voice settings preserved and
transient in-memory audio. Facts remain local. Normal backend tests passed 101 checks/one paid-live
skip; Android passed 496 JVM tests per variant, all APKs, and 23 silent device cases/two deliberate
live skips. All 32 saved runs/fields stayed unchanged. Manley saved the private backend key on
Render; user commit/push, Render deployment, provider acceptance and actual hearing proof remain.
The current Live music backend does not yet
advertise voice support, so the installed app retains local fallback. Manley creates the speech-only
key privately; the prepared 10,000-credit limit caps usage per refresh period and buys no credits.
See the current-state record for the five necessary new source/test files and exact boundaries.

**October 17 activation/acceptance supersedes the pending status above:** Manley pushed through
`cb23da6`; Render deployed it successfully in 55.2 seconds and retained song-cue support alongside
voice capability. The explicitly opted-in real hosted Moto check then passed in **40.732 seconds**.
It automatically generated a new sample Feeling Good reflection with controlled Lovely Day/Bill
Withers context, spoke local sample facts, played actual custom-voice MP3 with natural completion
and no local fallback, then completed the instrumental. Manley confirmed **"Yes, all three sounded
right."** All 32 saved runs/every inspected field remained unchanged; private inspection copies and
the temporary test APK were removed. No hand-written reflection, manual pairing, song download,
source edit or Git mutation in activation. This proves the chosen voice transition, not automated
vocal-hook discovery or whole-run song ranking. The existing Sunday Best passage remains separate.

- **ElevenLabs:** Manley generates and auditions recordings there using his saved designed voice,
  `1st test voice`, and **Eleven Multilingual v2**. His description: "A middle-aged African American male,
  friendly, confident, conversational, fun, productive and motivational energy."
- Keep voice/model/settings unchanged within a comparison; note deliberate changes. Exact slider
  values were not captured. This is an original designed voice, not an artist clone or production choice.
- **Read first, listen second:** Manley first reads and judges how the wording sounds to him. Then he
  hears the **same text** in his ElevenLabs voice and gives a separate reaction. Preserve both, even
  when delivery improves a disliked line; History also needs readable text. Unreported playback
  feedback stays unconfirmed. Listening judgments below are Manley's reports, not assistant auditions.

### Three working examples

All three are **fictional**, with **"Run It Up" — Offset & Key Glock (2025)** assumed observed.
Unreported goals, effort and music causation remain unknown. Moderate → Feeling Good and Low → Spent
each stay at the same internal Energy level; neither establishes an energy change or effort intensity.

**Ordinary afternoon run:** 2.8 miles in 30 minutes, 4:10–4:40 p.m.; Moderate → Feeling Good;
no personal best.

> Run it up then! 2.8 miles in 30 minutes, and you finished feeling good—that’s a real quality run.

Manley supplied the quality-run interpretation and accepted this tightened wording. **Listening:
unconfirmed.** Generic swagger and time-saving endings missed; he wanted meaningful recognition of
the work and finish together. "Quality" is affirmation, not a training grade or a judgment against Spent.

**New longest run:** 4.6 miles in 47 minutes, finishing at 6:55 p.m.; Moderate → Powered Up;
the fictional history establishes a previous longest distance of 4.2 miles.

> 4.6 miles—your longest run yet. You ran it up, and you finished powered up.

Manley's shortened opening, retained as a complete candidate. **Listening for this edit: unconfirmed.**
He called the longer draft close, but rejected "talk your talk tonight" as worn/corny. The opening
already connected achievement, music and Energy; its extra closing weakened the impact.

**Spent morning run:** 3.1 miles in 31 minutes on a Houston park loop, 6:12–6:43 a.m.; Low → Spent;
no effort report or performance comparison.

> Three miles before seven, on a morning you started low on energy. You put up the numbers. Now
> you’re feeling spent—sometimes this what hard work looks like.

Manley's preferred wording, conversational phrasing preserved. **He explicitly liked it in his
ElevenLabs voice:** productivity with encouragement. Earlier reassurance implied disappointment in
reading, though voice helped; "let those miles speak for themselves" missed in both. "Hard work" here
recognizes the whole run, not a rule that Spent proves hard or maximum effort.

### Later checkpoint — two successful pairings and the snippet experiment

**Shared fictional case:** Wednesday, 6:48–7:20 p.m.; 3.0 miles in 32 minutes;
Moderate → Feeling Good. Monday's 2.4 miles and Tuesday's 2.6 make eight miles across three runs
this week. No PR, goal, effort report or long-term habit is established. Each song is assumed heard
in a separate version of this case, not evidence of actual playback or a music-caused change.

**Nappy Roots — "Good Day":**

> Three runs into the week, and you finished tonight feeling good. You don’t have to save all the
> celebration for Friday—there’s a good piece of Wednesday right here.

**Surfaces — "Sunday Best":**

> Eight miles by Wednesday, and you’re finishing tonight feeling good. That’s a pretty good way
> to leave a run—with the work done and something to smile about.

Manley said he would personally enjoy receiving either after a run, preferring the second. These
were liked before adding snippets; they were not repaired through the earlier co-writing process.
This is feedback on these examples, not a claim that the same words suit every run.

**Listening experiment:** Manley played the ElevenLabs reflection, then manually started a chosen
song passage in Spotify's Windows browser player. He reported that both pairings sounded good.
For "Sunday Best," he deliberately cued just before the singing about feeling good, rather than
starting at the beginning, and said the connection was clear. The "Good Day" passage and exact
start times, gaps and durations were not captured. These are his listening reports, not assistant
audio judgments or a controlled comparison.

**Useful finding:** passage choice and entrance timing deserve separate attention from the wording.
Manual switching made the handoff rough. Manley is now confident the idea can work well once the
connection is smooth; transfer to other pairings, repeat use and automatic selection remain untested.

**Parked, not rejected:** smooth playback, audio sourcing and provider integration. No combined
recording or automated handoff was built. A feasibility check found that Spotify's
[Developer Policy, III.7](https://developer.spotify.com/policy) restricts developer-system segues
between Spotify content and other audio; production permission is unresolved. This is separate from
the user's manual listening experiment and does not establish that every possible delivery route
is unavailable. No purchases, downloads of songs or Android changes were made.

**October 12 follow-up:** Manley wants the music snippet paired with spoken reflection when it
fits the implementation sequence. Preserve the manual experiment's positive feedback, then
choose a permitted audio source and test the entrance timing after the voice reply and music
evidence are stable. The request does not establish a Spotify playback permission or turn the
current media-session observations into complete-song evidence.

**October 14 final source decision — temporary original instrumental approved:** Manley briefly
preferred waiting for an actual song, then explicitly chose to use the instrumental until that is
possible. The final approval supersedes the deferral. Its value is testing the speech-to-music
entrance, cancellation and local playback independently of a song provider. It does not establish
the intended semantic reflection/song pairing, automatic passage selection or Spotify permission.

The current bounded implementation appends one 10-second cue only to successfully completed
Completion reflection speech, after the factual saved-run receipt. History stays voice-only.
Stop audio, navigation, a new run, app pause and disposal/rotation invalidate owned playback;
focus/speech/player failures do not launch or resume a cue. Final integrated verification passed
287 JVM tests per Android variant, all three APK builds and all 76 emulator screen cases, including
ten new audio checks and actual-player playback. A real footer overlay intercepted the Stop touch;
an isolated rerun confirmed the failure, then a measured footer/weighted scroll viewport fixed
the production layout. The touch assertion was preserved and the isolated/full suite passed.

Moto received the APK without a reset. One explicit Sample run (0.05 mi, 1:03 elapsed, 27 seconds
active) received a live v1.2 reflection with no Energy choice or music sharing. Manley confirmed
the saved receipt, exact reflection and instrumental were "all in order and landed perfectly."
Focus logs recorded voice completion, the music-focus request 32 ms later and focus abandonment
10.782 seconds afterward in that check; this is not a guaranteed future entrance latency.
There was one hosted reflection-family request for the new Sample run and no network generation
for cue playback. Stay-awake was restored to 0 with no GPS service/wake lock remaining. Actual-song
selection/passage, Spotify content permission and wider external audio compatibility remain open.
The later one-Moto Spotify yield/return check in the current status above passed separately.

**Asset provenance:** `android/app/src/main/res/raw/runstate_reflection_instrumental.mp3` was
generated once with ElevenLabs Music v2 on October 14, 2026: instrumental-only, warm mellow
hip-hop around 90 BPM with electric piano, bass and restrained drums. No artist, title, lyric or
artist-voice reference was supplied. The asset is 258,514 bytes; SHA-256 is
`80C37987BC985C54BEBF613E618DA3EAFCB742750EDC9F988CD6E0C678A0A114`.
Generation references: flow `A1PrdpEGAiNEg3O1dCg2`, generation `uimfwx3mhqx7N2OnvXp2`.
Completion credits `Eleven Music`; [ElevenLabs Music terms](https://elevenlabs.io/music-terms)
are the source terms, not a claim of exclusive copyright or an actual-song licence.
The MP3 is bundled once in the Android APK. There is no production ElevenLabs integration,
per-run generation/network cost, API credential or signed download URL in the app/repository.
The one-time asset-generation step is separate from Anthropic's live reflection generation.
Choose an actual song and permitted playback route later; media-session metadata still does not
grant playback rights. The local cue adds no song evidence, Room field or schema migration.

**Historical September direction (superseded by October 3):** return to developing the reflections. Retain these examples and the positive
snippet feedback without making playback polish a prerequisite for the next writing test.

### Additional Log History references — later user feedback

Exact sample text already lives in the [Run Complete / Log History prototype](../../prototypes/run-complete-log-history/run-complete-log-history.dc.html)
(`LOG` records). Link to it rather than duplicating all five replies. These are mockup references,
not verified records of actual runs. Feedback below is Manley's report; no assistant audio judgment
or separate numerical grade is implied.

| Song | Feedback worth carrying forward |
| --- | --- |
| Larry June — "6am in Sausalito" | Strongest reference: liked completely, wanted no wording changes, and the song pairing connected very well. Preserve the creative wording as a reference, not reusable factual claims about other runs. |
| Nas — "The World Is Yours" | Mostly keep; remove "nobody else on the bridge." Especially liked the final line and its connection into the chosen song passage. "Empty city" is also not established as literal fact by this preference. |
| Kanye West — "Runaway" | Good, below the Larry June example for him; the snippet brought it to life through atmosphere and motivational delivery more than exact semantic matching. |
| J. Cole — "Power Trip" | Liked reading and ElevenLabs delivery, including natural direct song naming. The weaker snippet connection did not invalidate the reflection itself. |
| GloRilla — "TGIF" | Liked the vibe and snippet pairing while noting technical adjustments were needed. This does not approve inferring run intent from cadence or from the song. |

### Lessons to carry forward, not mandatory templates

- **Recognition over recap:** ordinary runs deserve meaning, not facts followed by generic swagger.
- **Land quickly, as a whole:** tighten for impact and rhythm, not a fixed length or cleverness in every clause.
- **Endings must contribute:** avoid worn catchphrases and disconnected slogans. Artist character serves the run.
- **Respect Energy:** Spent is not disappointment; Feeling Good does not prove improvement or music causation.

### Historical writing-session workflow — not the current implementation queue

Retain this as an optional method when creative exploration resumes, not a required sequence before
the first build. Related examples may be batched; explain the question and stopping point first.

1. **Choose one question and scenario.** Establish facts and unknowns; label fiction. No new required runner questions.
2. **Draft one or two responses.** Get first reactions before explaining the intended effect or revising.
3. **Read, then listen in ElevenLabs.** Keep reactions separate. Hit / Close / Miss is optional; never invent a rating.
4. **Revise the specific weakness.** Preserve facts; distinguish wording from delivery changes. Case preferences are not universal rules.
5. **Checkpoint, then try another run.** Keep representative examples and reasons, distinguishing first attempts from co-edits. Do not require Manley to write every outcome.

**Historical proposed step (superseded by the October 3 resume point):** try one fresh run case with a researched song, judging the reflection first
on the page and then in the same provisional ElevenLabs voice. Test whether recognition carries to
a different run without requiring another long co-editing cycle. Snippet timing remains parked.
This is not authorization for code, training or paid API integration.

**Keep this notebook lean:** explain scope before saving; update this checkpoint only for meaningful
learning, not every edit. No transcript, duplicate plans or automatic promotion of preferences to rules.

## Reading priority

The labels below matter. Peer-reviewed research, company engineering reports, product reviews, and
craft articles can all help, but they do not carry the same evidentiary weight.

### Quick route through the research

These are **uses of the research**, not extra tasks or an approved multi-stage AI architecture:

- **Meaning, not just creative description:** [sports storytelling](https://www.sportscasterlife.com/tell-better-stories-stats/)
  and [HPI's computer-tailored reflections study](https://doi.org/10.1093/heapro/dat069). Authentic
  context can make a number matter; support choice, acknowledge real accomplishment, and convey
  understanding. Do not invent personal backstory or add questions to reproduce a study protocol.
- **Human taste guiding generation:** [Spotify's narrative-generation research](https://research.atspotify.com/2024/12/contextualized-recommendations-through-personalized-narratives-using-llms)
  and [persona/style evaluation](https://futureagi.com/blog/evaluating-llm-personas-style-2026/).
  Preserve representative examples and reasons; judge truth separately from voice. Stable values
  should coexist with expressive variety. Embedding similarity is not proof of meaning or quality;
  vendor scores, tools and fine-tuning are not requirements for this first build.
- **Trust checks, not the whole creative ambition:** the [hallucination survey](https://doi.org/10.1145/3571730)
  and the [Cycling Weekly](https://www.cyclingweekly.com/news/strava-says-its-new-ai-feature-is-not-a-novelty-but-i-think-its-pointless)
  / [T3](https://www.t3.com/active/strava-athlete-intelligence-i-tried) reviews below. Avoid unsupported
  comparisons, invented significance and empty praise. Historical reviews are not a current audit
  of Strava or a definition of RunState's identity.
- **Delivery as a separate contributor:** [Popular Science's Spotify DJ launch report](https://www.popsci.com/technology/spotify-ai-dj/)
  helps distinguish material selection, writing and speech. Our read/listen/snippet feedback also
  keeps those effects separate; production audio and smooth snippet playback remain later.

Manley's selected excerpts and highlights live locally in
`C:\Users\Owner\OneDrive\Documents\MusicReflectionResearch`. He asked that all selected material be
considered, with extra attention to highlights. `Evaluating LLM Personas, Style.docx` and `HPI.docx`
mainly point to the full articles rather than exhaustive extracts. This is a local research pointer,
not a repository dependency or instruction to reread everything before implementation.

### Priority 1 — foundations for the system

1. **[Contextualized Recommendations Through Personalized Narratives using
   LLMs](https://research.atspotify.com/2024/12/contextualized-recommendations-through-personalized-narratives-using-llms)**
   — Spotify research/engineering article, December 2024.
   - **Why it matters:** the closest operational model in this set. Spotify reports that ordinary
     zero-shot and few-shot prompting was not enough. Music editors supplied golden examples and
     continuing feedback about attribution, tone, and factual errors; the technical teams added
     prompt work, instruction tuning, adversarial tests, curated data, and formal evaluation.
   - **RunState use:** start with Manley-approved and rejected examples plus separate truth, voice,
     and cultural-fit checks. Fine-tuning is a later possibility, not the starting requirement.
   - **Limit:** this is a company-authored report, not a peer-reviewed controlled study, and clicks
     or listening behavior do not prove that a runner felt seen.

2. **[Motivational interviewing in a web-based physical activity intervention: questions and
   reflections](https://doi.org/10.1093/heapro/dat069)** — peer-reviewed study in *Health
   Promotion International*; [Oxford publisher
   page](https://academic.oup.com/heapro/article/30/3/803/620985).
   - **Why it matters:** it directly studies computer-tailored physical-activity reflections. The
     randomized comparison found value in combining open-ended expression with structured answers;
     its definition of a skillful reflection is especially relevant because the reflection adds
     grounded meaning or emphasis rather than merely repeating the person's words.
   - **RunState use:** treat autonomy, competence and relatedness as design lenses: respect the
     runner's choice, recognize something real, and show understanding rather than generic warmth.
     Use the existing evidence and optional Energy inputs; this is not approval for more questions,
     a runner-note feature, or current-run Effort to control the immediate mobile reflection.
   - **Limit:** this is pre-LLM research, the motivation findings were mixed, and it does not prove
     that RunState-style creative reflections improve long-term running behavior.

3. **[Survey of Hallucination in Natural Language
   Generation](https://doi.org/10.1145/3571730)** — peer-reviewed survey in *ACM Computing
   Surveys*, 2023.
   - **Why it matters:** it supplies the strongest truth vocabulary in this list. An intrinsic
     hallucination contradicts the supplied evidence; an extrinsic hallucination adds content that
     the evidence does not support. The survey includes data-to-text generation, RunState's closest
     technical category.
   - **RunState use:** truth should be a separate pass/fail gate, not blended into a single
     creativity score. Each factual or causal clause should trace to approved run, runner, history,
     or music evidence.
   - **Limit:** it is a broad survey rather than a ready-made RunState evaluation method, and much
     of the reviewed work predates today's production LLMs.

### Priority 2 — evaluation method and shipped failure evidence

4. **[Evaluating LLM Personas, Style, and Persona Drift Across
   Turns](https://futureagi.com/blog/evaluating-llm-personas-style-2026/)** — Future AGI vendor
   engineering guide, 2026.
   - **Why it matters:** the most immediately actionable evaluation article here. It recommends a
     bounded voice description, 10–20 matched in-voice/out-of-voice examples, a small hard-rule set,
     separate per-response scoring, and calibration against human raters.
   - **RunState use:** Manley's ratings remain the authority. The useful adaptation is not mainly
     multi-turn chat drift; it is drift across a collection of reflections into generic AI wording,
     repeated openings, fake artist color, or one formula.
     Protect truthful, respectful values without treating playful versus understated expression as
     unwanted drift. Its judge/embedding mechanisms are possible later diagnostics, not proven gates.
   - **Limit:** it promotes the vendor's own tooling and is not peer-reviewed. Its numeric targets
     are examples, not standards RunState should inherit.

5. **[Strava says its new AI feature is 'not a novelty' — but I think it's
   pointless](https://www.cyclingweekly.com/news/strava-says-its-new-ai-feature-is-not-a-novelty-but-i-think-its-pointless)**
   — first-person product review in *Cycling Weekly*, November 2024.
   - **Why it matters:** a compact failure-case inventory from a shipped product: repeating visible
     data, comparing without knowing the activity's purpose, over-reading titles, reversing what a
     user wrote, making unsupported performance claims, and producing impressive-sounding filler.
   - **RunState use:** turn these examples into adversarial fixtures and rejection checks.
   - **Limit:** this is one journalist's review with colleague anecdotes, not evidence of failure
     rates across all Strava users.

6. **[I've spent four months with Strava's AI-powered Athlete Intelligence
   feature](https://www.t3.com/active/strava-athlete-intelligence-i-tried)** — long-term product
   review in *T3*, September 2024.
   - **Why it matters:** it identifies the positive side of the same problem. The reviewer found the
     response more useful when it reacted to the runner's own description, while the lack of goals
     and training-plan context kept other summaries upbeat but vague.
   - **RunState use:** more verified context creates more possible angles, but a user's note must be
     treated as first-person context rather than mined recklessly for claims.
   - **Limit:** this is still one review, not a controlled study.

### Priority 3 — architecture and writing craft

7. **[How Spotify's AI DJ works](https://www.popsci.com/technology/spotify-ai-dj/)** — *Popular
   Science* launch reporting, February 2023.
   - **Why it matters:** it describes three separable layers: Spotify personalization chooses the
     material, generative AI scripts commentary alongside human writers, and Sonantic supplies the
     voice. A writers' room and cultural experts set the subject matter and phrasing before AI
     scales and tailors it.
   - **RunState use:** keep evidence/angle selection, reflection writing, and eventual spoken
     delivery as distinct responsibilities. AI scales editorial judgment; it does not replace it.
   - **Limit:** it is secondary reporting about a launch-era beta, not a current technical spec.

8. **[How to Tell Better Stories with
   Stats](https://www.sportscasterlife.com/tell-better-stories-stats/)** — practitioner craft article
   from Sportscaster Life.
   - **Why it matters:** numbers are the foundation, while timing, context, and significance create
     the story. That closely matches the difference between restating 1.52 miles and finding what
     that run meant.
   - **RunState use:** authentic personal context can make a statistic meaningful, including a
     runner-reported musical discovery or association. More measurements alone are not the answer.
   - **Limit:** its illustrative stories include aspirations, family and practice history. Do not
     infer such backstory from statistics; this is craft guidance, not evidence of an app benefit.

9. **[Praise vs Recognition: Why Employees Need More Than "Great
   Job"](https://www.fringe.us/news/praise-vs-recognition)** — workplace-recognition vendor article.
   - **Why it matters:** its useful distinction is between generic praise and specific recognition:
     naming what was observed and why it mattered makes someone feel genuinely seen.
   - **RunState use:** this is a drafting lens for moving beyond "Good job." Identity statements
     should come from repeated history, not be declared from one run.
   - **Limit:** the article provides little research support for its broader psychology claims.

10. **[Data Storytelling: How Spotify Wrapped Hooks the
    World](https://storysoft.io/data-storytelling-spotify-wrapped/)** — Storysoft marketing analysis.
    - **Why it matters:** brevity, creative abstraction, identity framing, and visual presentation
      can make personal data emotionally legible and shareable.
    - **RunState use:** learn from the compression and presentation, not just the copy.
    - **Limit:** the article praises language that is both specific and vague enough to fit many
      people. That Barnum-like shortcut is precisely what RunState should avoid if the goal is to
      make one runner feel accurately seen.

### Additional relevant research — retained for later, not new feature commitments

These preserve promising directions from the broader discussion without reopening discovery or
changing the text-first implementation. Sources checked October 3; the creativity paper's original
PDF was inaccessible in this check, so its official repository abstract is linked instead.

11. **[Music-evoked autobiographical memories in everyday life](https://journals.sagepub.com/doi/10.1177/0305735619888803)**
    — Jakubowski and Ghosh, peer-reviewed diary study. Across 31 people and seven days, music-cued
    memories were often spontaneous, vivid and positive or mixed. **Possible use:** a song's
    runner-endorsed personal association, not only its title or public meaning, may make a run
    memorable. **Limit:** observational evidence, not proof of a RunState motivational benefit.

12. **[Designing Documentary Informatics](https://elsden.me/wp-content/uploads/2015/03/designing-documentary-informatics.pdf)**
    — Elsden and colleagues, design research. Tracking can support remembering and self-expression,
    not only monitoring and improvement. **Possible use:** a meaningful run record without turning
    RunState into a training coach. **Limit:** exploratory work around a speculative wedding service,
    not a tested running product.

13. **[A Quantified Past: Towards Design for Remembering with Personal Informatics](https://elsden.me/wp-content/uploads/2015/03/hci-journal-q-past-camera-ready-shared.pdf)**
    — Elsden, Kirk and Durrant, qualitative study of 15 long-term self-trackers. **Possible use:**
    people help make records meaningful; collecting more data alone does not create that meaning.
    **Limit:** design opportunities from a small interview study, not established feature effects.

14. **[Generative AI enhances individual creativity but reduces the collective diversity of novel content](https://discovery.ucl.ac.uk/id/eprint/10195027/)**
    — Doshi and Hauser, 2024 controlled writing experiment. AI ideas improved individual story
    evaluations while making stories more similar. **Possible use:** preserve Manley's original
    associations and review variety across a set, not just polish per response. **Limit:** short
    fiction, not running reflections; it does not establish inevitable sameness for our workflow.

**Parked opportunity:** immediate recognition and longer-term musical memory may be distinct kinds
of value. Personal associations must be supplied or endorsed, not guessed; unwanted reminders and
repetitive nostalgia are risks. No memory feature, extra runner questions, automatic snippet
selection, model training or evaluation service is approved by saving these references.

## What the negative feedback contributes

The negative sources do not argue that the RunState idea should be abandoned. They show the gap the
system has to close:

1. **Do not narrate the dashboard.** A reflection must add an earned interpretation, connection, or
   feeling; otherwise the visible metrics already did the job.
2. **Do not compare without purpose.** Pace, distance, and effort mean different things on a recovery
   run, first run back, speed session, or casual run. Silence is better than a false achievement.
3. **Do not mistake user text for literal fact.** A title or note is context, not automatic proof of
   terrain, intention, emotion, or outcome.
4. **Do not let positivity replace recognition.** Warmth without specificity becomes generic
   encouragement; specificity without truth becomes fake intimacy.
5. **Do not sound analytical merely to sound valuable.** Every clause should either be grounded,
   create a real connection, or earn its place through voice.
6. **Do not succeed only one response at a time.** A set can contain individually acceptable lines
   and still fail through repeated structures, openings, ideas, or emotional range.

## Provisional system hypothesis

**Historical exploration, not the October 3 implementation architecture.** The small later
angle-first comparison and reviewer results above did not validate this pipeline. Keep the useful
evidence and judgment distinctions; do not require these six stages or another evaluation round
before implementation:

1. **Assemble an evidence packet.** Keep measured run facts, comparisons, Energy/Effort, runner
   words, song-play evidence, and researched music context separate, with provenance and confidence.
2. **Select one angle.** Decide what is most worth reflecting before trying to write. The response
   does not need to mention every available fact.
3. **Generate genuinely different candidates.** Vary the idea and construction, not only synonyms.
   Artist/song knowledge is material to write with, not a paragraph to explain.
4. **Apply separate gates.** At minimum: factual truth, recognition/meaning, voice/creative value,
   product readiness, and variation across the set. One high average must not hide a truth failure.
5. **Use Manley's Hit / Close / Miss judgments with reasons.** Preserve approved lines, near misses,
   and rejected lines as calibration pairs. The reason is more reusable than the score alone.
6. **Improve retrieval and selection before considering model training.** A small, well-labeled
   example library and a reliable evaluation loop should exist before fine-tuning or a large prompt
   framework is justified.

This changes the unit of work. Manley does **not** need to author every possible reflection. His
highest-value role is defining taste through examples, explaining why a line lands or misses, and
settling the few product choices that govern the system.

## Research-response protocol

For future analysis in this area:

- Lead with the one to three findings most relevant to the current decision.
- Label claims as **source finding**, **RunState inference**, or **recommendation** when the
  distinction is not obvious.
- State source strength briefly: peer-reviewed research, company report, product review,
  practitioner guidance, or vendor/marketing analysis.
- Keep deeper summaries and caveats in this notebook or behind direct links. Do not turn every
  conversational response into a literature review.
- Research broadly enough to find contradictions and failure cases, then narrow back to the next
  product decision. No source becomes a RunState requirement without Manley's approval.

## Parked research questions — not implementation prerequisites

Work through these one at a time rather than opening all of them at once:

1. What minimum evidence packet gives the reflection enough context without burdening the runner?
2. What exact dimensions should Manley score when labeling a response Hit, Close, or Miss?
3. How should artist/song knowledge be sourced, bounded, and attributed internally so voice can be
   informed without fabricated facts or costume-like imitation?
4. How will the evaluation detect structural repetition and lost range across many responses?
