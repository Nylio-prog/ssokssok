# Deck file format

ssokssok imports two formats. Pick whichever is easier to produce. The app shows both, with copy buttons, under **Import cards**.

## Text (recommended for hand-written or AI-generated decks)

One card per line. Fields are separated by a **tab** (or `|`, `;`, or `,` for CSV). Only the first two fields are required.

```
# deck: Food & Drinks
# description: Words for ordering at a restaurant
# symbol: 밥
korean	english	romanization	example	example_translation	notes	tags	type
사과	apple	sagwa	사과를 먹어요.	I eat an apple.		food fruit	word
물	water	mul	물 주세요.	Water, please.		drink	word
저{{는}} 학생이에요.	I am a student.				는 marks the topic	particles	sentence
```

| Field | Meaning |
|---|---|
| `korean` | The Korean side. Wrap a part in `{{ }}` to make a fill-the-blank card; add a hint with `{{는::topic}}`. |
| `english` | The meaning. Separate other accepted answers with `;` (and `,` for single words). |
| `romanization` | Optional pronunciation help. |
| `example`, `example_translation` | Optional example sentence and its translation. |
| `notes` | Optional tip: conjugation, nuance, mnemonic. |
| `tags` | Optional, separated by spaces or commas. |
| `type` | `word`, `sentence` or `grammar`. Guessed when left out. |

Rules:

- The header line is optional. Without it, columns are read in the order of the table above. With it, you can use any order, and names such as `front`/`back`, `ko`/`en`, `meaning` or `pronunciation` are understood.
- `# deck: Name` starts a new deck, so one file can hold several decks. `# description:`, `# symbol:` (one or two characters for the deck badge) and `# type:` (default type for the following lines) are optional.
- Other lines starting with `#` are ignored. That covers Anki's plain-text export headers, so **Anki "Notes in Plain Text" exports import directly** (HTML and `[sound:]` tags are stripped).
- If a line has the English first and the Korean second, the app swaps them.
- Lines with fewer than two fields are skipped and listed in the preview.

## JSON

```json
{
  "decks": [
    {
      "name": "Food & Drinks",
      "description": "Words for ordering at a restaurant",
      "symbol": "밥",
      "cards": [
        { "ko": "사과", "en": "apple", "rom": "sagwa", "example": "사과를 먹어요.", "exampleEn": "I eat an apple.", "tags": ["food"] },
        { "ko": "-고 싶다", "en": "to want to (do)", "type": "grammar", "notes": "Verb stem + 고 싶다" }
      ]
    }
  ]
}
```

A single deck object (`{"name": ..., "cards": [...]}`) or a bare array of cards also works. Keys accept the same aliases as the text header (`korean`/`ko`/`front`, `english`/`en`/`back`, ...). This is also the format the app exports.

## Importing

- **Choose a file**: `.json`, `.tsv`, `.csv` or `.txt`.
- **Paste text**: paste straight into the import screen.
- **Share to ssokssok**: share text from a chat app, or open a deck file with ssokssok.

Cards are added to the deck named in the file (created if missing) or to a deck you pick. Cards already in that deck with the same Korean and English are skipped, so re-importing an updated file only adds the new lines.

## Generating a deck with an AI assistant

The import screen has a **Copy prompt** button. Type a topic ("ordering at a café", "TOPIK I verbs", "-아/어서"), copy the prompt, paste it into any chat assistant, then share or paste the answer back into ssokssok.
