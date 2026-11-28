# U.S. trail discovery — November 28, 2026

Manley approved this after accepting the RS launcher icon on his Moto. Keep the accepted Start
layout and music/voice behavior. This work does not authorize public preview publication.

## Experience

- Start → Explore trails opens a searchable browser. It uses one usable foreground location
  per visit, rounded to two decimal places, or a city chosen by the runner. Near me explicitly
  refreshes the search area. No extra location listener, background tracking or run is started.
- Search matches names/cities in the bundled collection and current nearby results. Offline city
  suggestions let the runner choose another U.S. area; name search alone is not a nationwide
  search of every live trail. Without location, popular U.S. places remain browsable.
- Difficulty, surface and elevation-gain filters combine. Unknown details match Any, not a
  guessed difficulty, flat elevation or surface. Elevation bands are under 300 / 300–1,000 / over 1,000 ft.
- Cards/details show a whole real photo with a small rounded map at lower right. Portraits keep
  their full image inside a bounded frame. Where no usable photo exists, a labeled map replaces
  it. Real sourced path segments are drawn separately; missing lines are never invented.
- Park information remains. Photo/source/license details move to the information button;
  required map attribution stays accessible from each map. The old two-large-images layout and
  More places around Houston link are removed.
- Directions uses Android's default geo-intent handler. A verified entrance may be a destination;
  otherwise the map opens a trail-name search biased to the public trail area, with an explicit
  instruction to choose an entrance. The midpoint of a trail is never claimed as parking.
- The former Trails Open map action is now Settings → Location → Open map. Existing map/run
  behavior and GPS permission policy remain; Settings releases preview warmup and returning to
  the map establishes one listener again.

## Data and boundaries

- Bundled researched collection: 73 records, 21 Houston-area entries, 46 licensed photos. The
  catalog includes published route facts where available and eight documented elevation gains.
  This is a starting collection, not a complete national directory or popularity ranking.
- USGS National Digital Trails supplies named terrestrial paths around the chosen area
  (40 km). Explicit pedestrian-prohibited records are excluded. Sources can describe segments
  or networks, not complete runnable loops. No implied current-access or route-safety guarantee.
- U.S. Census 2025 Gazetteer supplies 32,350 place labels/coordinates for offline city search.
  Matching state abbreviations disambiguates names such as Portland, OR versus Portland, ME.
- HTTPS requests have response/time/node limits; at most two 200-feature pages per search.
  Partial/unavailable coverage is identified. Seven-day private cache holds at most 16 areas.
  City lookup and parsing run off the main thread. Leaving the foreground cancels network work.
- Commons photos use exact upload.wikimedia.org/thumb.wikimedia.org hosts, bounded downloads
  and decoded images. MapLibre snapshots reuse the existing OpenFreeMap map styling/network
  setup; two concurrent renderers maximum, timeouts, memory caches and lifecycle cancellation.
- No provider key, paid service, database migration, saved-run change or backend deployment.
  Existing Houston background experiment/assets remain available internally.

## Implementation and evidence

New production files: trails/TrailPlace.kt, TrailFilters.kt, TrailRepository.kt and
ui/TrailBrowserSheet.kt, TrailVisuals.kt. New assets: trails/curated_trails.json and us_cities.tsv.
Five focused test files cover parsing, geometry, filters/navigation, the browser and an explicit
opt-in public-network smoke test. Existing Start/map tests now follow Settings → Open map.

Final checks: 23 focused JVM tests passed; Debug/test APKs and Release vital lint passed.
All 13 final emulator checks passed, including seven isolated browser cases, five Start/map/GPS
regressions and real USGS/photo/map rendering. Review captures and final integration results are
private under docs/local/trails-2026-11-27/. An existing native-map
fixture needed explicit test-clock advancement because Active's unchanged custom frame loop
does not become idle with an automatically advancing clock. No production animation was changed.

Installed in place on the Moto. Installed APK hash matches the tested artifact
479776383ff97c76f62130769d6ad40c1fc4f81b7337cee5701d0890484400b4.
Before/after private receipts retain all 81 completed runs, two shoe pairs, all 12 table hashes
and all five preference files unchanged, with integrity OK and no foreign-key violations.
Google Maps resolves the geo handoff on the Moto; actual entrance selection/travel remains an
owner review. No phone run was started and the main app was never uninstalled.

Private provenance: docs/local/nov27-trail-catalog-sources.md. Bundled per-item URLs/credits and
third_party_notices.txt retain public source attribution. DATA_PRIVACY.md and in-app privacy
explain approximate area/photo requests and the default-maps handoff.

## Later owner review (one short session)

1. Open Explore trails in a familiar area; compare the selected city/nearby results and photo size.
2. Try another city and one filter; many public records lack ratings/elevation and are therefore
   excluded by specific filters. Check returning from Directions keeps the browser/run unchanged.
3. Choose the appropriate entrance in Maps and confirm the intended trail before travel. This is
   a maps handoff, not RunState turn-by-turn navigation.
4. Check Settings → Location → Open map and then return to Start. Existing Android outdoor
   voice/reflection/PR checks remain in the private roadmap; this does not complete those checks.

Coverage, more scenic licensed photos and exact entrances can expand later. One remote photo
(Jacksonville–Baldwin) returned 429 during source verification; unavailable images fall back to maps.
