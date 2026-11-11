# ssokssok 쏙쏙

A Korean study app for Android: words, sentences and grammar, with decks you build yourself and spaced repetition that stays out of your way.

*쏙쏙 (ssokssok): the sound of something going in neatly, as in 머리에 쏙쏙 들어오다, "it goes straight into your head".*

## What it does

- **Decks in folders.** Group decks by book and lesson (서울대 한국어+ › 3A › 1-1 시작과 만남). Folders fold open on the home screen, can be studied as a whole, and a lesson's page has chips to jump to the other lessons of the same book.
- **Decks and cards.** Create decks, add a card in a few taps ("Save & next" keeps the deck and type for fast entry), edit or move cards between decks.
- **Words, sentences, grammar.** Each card has Korean, English, and optionally its 한자, an example sentence, notes and tags.
- **한자 breakdown.** Sino-Korean words show each syllable with its Korean meaning and reading (新 새 신 · 入 들 입 · 生 날 생). Tap a syllable to see the other words in your decks built on it.
- **Fill-the-blank cards.** Select part of a sentence in the editor and tap *Make blank*: `저{{는}} 학생이에요` quizzes just the particle. Great for particles and endings.
- **Choose how you study, every session.**
  - Direction: Korean → English, English → Korean, or both.
  - Answer by flipping the card, or by typing.
- **Typing is forgiving where it should be.** Answers are compared down to the jamo, so 갓어요 for 갔어요 counts as *almost* and shows exactly which letter slipped. Spacing-only differences pass with a note. English answers ignore case, articles and "to", and accept any alternative written with `;`.
- **Three answers.** *Hard* (didn't know), *Medium* (took a moment), *Easy* (instant). Each button shows when you'll see the card again. Swipe the card right for Easy, left for Hard. Undo is one tap.
- **FSRS scheduling.** The same modern memory model Anki now uses, with a target-recall setting (90% by default) and a daily new-card limit per deck.
- **Learning aids beyond plain flashcards.**
  - English → Korean unlocks for a word once you recognise it (in mixed mode), so production builds on recognition.
  - *Tough cards*: cards that keep slipping are flagged and can be drilled on their own.
  - *Practice* runs through cards without touching the schedule.
  - Korean text-to-speech on demand (the app is silent unless you tap *Listen*, or turn on autoplay in Settings); tap twice to hear it slower.
  - When typing, the app pre-selects a rating from correctness and speed.
- **Import from anywhere.** Text/TSV/CSV/JSON files, pasted text, Anki plain-text exports, or text shared from a chat app. A built-in prompt lets any AI assistant generate a deck in the right format. See [docs/deck-format.md](docs/deck-format.md).
- **Progress.** Streak, 18-week activity heatmap, 7-day forecast, recall rate, and a breakdown from new to mastered.
- Light and dark themes.

## Install

Download the latest `ssokssok-vX.Y.Z.apk` from [Releases](https://github.com/Nylio-prog/ssokssok/releases) and open it on your phone (allow installing from your browser or file manager when asked). Later releases install over the previous one and keep your data.

## Build

Requirements: JDK 17 and the Android SDK (platform 35).

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleDebug       # debug APK in app/build/outputs/apk/debug
```

Stack: Kotlin, Jetpack Compose (Material 3), Room, kotlinx.serialization. No network access, no accounts; everything stays on the device.

## Releases

Pushing a tag `vX.Y.Z` runs `.github/workflows/release.yml`, which tests, builds a signed release APK and attaches it to a new GitHub release. The version name comes from the tag and the version code is `X*10000 + Y*100 + Z`.

```bash
git tag v0.2.0
git push origin v0.2.0
```

Signing uses four repository secrets: `SSOK_KEYSTORE_BASE64`, `SSOK_KEYSTORE_PASSWORD`, `SSOK_KEY_ALIAS`, `SSOK_KEY_PASSWORD`. Without them the workflow still produces an APK, signed with a throwaway key that can't update an existing install.

## Credits

Fonts: [Gowun Dodum](https://fonts.google.com/specimen/Gowun+Dodum) and [Plus Jakarta Sans](https://fonts.google.com/specimen/Plus+Jakarta+Sans), both under the SIL Open Font License 1.1.
