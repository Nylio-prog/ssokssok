# ssokssok

Android app (Kotlin, Jetpack Compose, Room) for studying Korean with decks and FSRS spaced repetition.

## Layout

- `app/src/main/java/com/nylio/ssokssok/`
  - `data/`: Room entities and DAOs (`db/`), `Repository` (session building, imports, stats), `Settings`.
  - `srs/Scheduler.kt`: FSRS-5 with three ratings (Hard→Again, Medium→Hard, Easy→Good).
  - `korean/`: Hangul decomposition, cloze `{{ }}` parsing, 한자 field parsing, typed-answer checking with jamo-level diff.
  - `importer/DeckFormat.kt`: text/TSV/CSV/JSON/Anki parsing, JSON export, AI prompt. Spec in `docs/deck-format.md`.
  - `ui/`: one package per screen; theme in `ui/theme/Theme.kt`.
- Deck names are paths: folders are separated by `::` (`서울대 한국어+::3A::1-1 시작과 만남`); `DeckPath` splits them and `ui/home/DeckTree.kt` builds the home tree. No decks are seeded on first launch.
- Cards have an optional `hanja` field (`新=새 신; 入=들 입`), parsed by `korean/Hanja.kt`. There is no romanization.
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
