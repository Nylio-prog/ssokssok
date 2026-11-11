# Deck file format

ssokssok imports two formats. Pick whichever is easier to produce. The app shows both, with copy buttons, under **Import cards**.

## Text (recommended for hand-written or AI-generated decks)

One card per line. Fields are separated by a **tab** (or `|`, `;`, or `,` for CSV). Only the first two fields are required.

```
# deck: 서울대 한국어+::3A::1-1 시작과 만남
# description: Starting university, meeting new people
# symbol: 1-1
korean	english	hanja	example	example_translation	notes	tags	type
신입생	freshman; new student	新=새 신; 入=들 입; 生=날 생	신입생 환영회가 내일 있어요.	The welcome party for freshmen is tomorrow.			word
동아리	(university) club		사진 동아리에 가입했어요.	I joined the photography club.			word
저{{는}} 학생이에요.	I am a student.				는 marks the topic	particles	sentence
```

| Field | Meaning |
|---|---|
| `korean` | The Korean side. Wrap a part in `{{ }}` to make a fill-the-blank card; add a hint with `{{는::topic}}`. |
| `english` | The meaning. Separate other accepted answers with `;` (and `,` for single words). |
| `hanja` | Optional Sino-Korean breakdown, one part per syllable separated by `;`: the character, `=`, then its Korean meaning (훈) and reading (음), e.g. `新=새 신; 入=들 입; 生=날 생`. The character can be left out (`눈 설`), and an English gloss added in brackets (`雪=눈 설 (snow)`). Leave empty for native words. |
| `example`, `example_translation` | Optional example sentence and its translation. |
| `notes` | Optional tip: conjugation, nuance, mnemonic. |
| `tags` | Optional, separated by spaces or commas. |
| `type` | `word`, `sentence` or `grammar`. Guessed when left out. |

Rules:

- The header line is optional. Without it, columns are read in the order of the table above. With it, you can use any order, and names such as `front`/`back`, `ko`/`en`, `meaning` or `pronunciation` are understood.
- `# deck: Name` starts a new deck, so one file can hold several decks. Put decks in folders with `::`, as in Anki: `# deck: 서울대 한국어+::3A::1-1 시작과 만남` shows up under 서울대 한국어+ › 3A. `# description:`, `# symbol:` (up to three characters for the deck badge) and `# type:` (default type for the following lines) are optional.
- Other lines starting with `#` are ignored. That covers Anki's plain-text export headers, so **Anki "Notes in Plain Text" exports import directly** (HTML and `[sound:]` tags are stripped).
- If a line has the English first and the Korean second, the app swaps them.
- Romanization isn't used. A `romanization` column is skipped, and so is a latin-only third column in files without a header (the old layout).
- Lines with fewer than two fields are skipped and listed in the preview.

## JSON

```json
{
  "decks": [
    {
      "name": "Topics::Food & Drinks",
      "description": "Words for ordering at a restaurant",
      "symbol": "밥",
      "cards": [
        { "ko": "음식", "en": "food", "hanja": "飮=마실 음; 食=먹을 식", "example": "한국 음식을 좋아해요.", "exampleEn": "I like Korean food.", "tags": ["food"] },
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
