# ssokssok

Android app (Kotlin, Jetpack Compose, Room) for studying Korean with decks and FSRS spaced repetition.

## Layout

- `app/src/main/java/com/nylio/ssokssok/`
  - `data/`: Room entities and DAOs (`db/`), `Repository` (session building, imports, stats), `Settings`.
  - `srs/Scheduler.kt`: FSRS-5 with three ratings (Hard→Again, Medium→Hard, Easy→Good).
  - `korean/`: Hangul decomposition, cloze `{{ }}` parsing, typed-answer checking with jamo-level diff.
  - `importer/DeckFormat.kt`: text/TSV/CSV/JSON/Anki parsing, JSON export, AI prompt. Spec in `docs/deck-format.md`.
  - `ui/`: one package per screen; theme in `ui/theme/Theme.kt`.
- `app/src/main/assets/starter.json`: decks seeded on first launch.
- Each card has two progress rows (direction 0 = Korean→English, 1 = English→Korean). Cloze cards only use direction 0.

## Checks

```bash
./gradlew testDebugUnitTest assembleDebug
```

## Making a release

When asked to "create a release":

1. Make sure `main` is committed, pushed and CI is green.
2. Pick the next version (semver; bump minor for features, patch for fixes) from the latest tag: `git describe --tags --abbrev=0`.
3. `git tag vX.Y.Z && git push origin vX.Y.Z`.
4. The `Release` workflow builds the signed APK and publishes the GitHub release with generated notes. Watch it with `gh run watch` and check the release has `ssokssok-vX.Y.Z.apk` attached.

Version code is derived from the tag (`X*10000 + Y*100 + Z`), so tags must increase. Signing secrets are already set on the repo; the keystore lives outside the repo in `../ssokssok-signing/` and must never be committed.
