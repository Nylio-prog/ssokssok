package com.nylio.ssokssok.importer

import com.nylio.ssokssok.data.db.CardEntity
import com.nylio.ssokssok.data.db.CardType
import com.nylio.ssokssok.data.db.DeckEntity
import com.nylio.ssokssok.korean.Hangul
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

data class ParsedCard(
    val korean: String,
    val english: String,
    val romanization: String = "",
    val example: String = "",
    val exampleTranslation: String = "",
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val type: CardType = CardType.WORD,
)

data class ParsedDeck(
    val name: String?,
    val description: String = "",
    val symbol: String = "",
    val cards: List<ParsedCard>,
)

data class ParseResult(
    val decks: List<ParsedDeck>,
    val warnings: List<String>,
    val format: String,
) {
    val cardCount get() = decks.sumOf { it.cards.size }
}

class DeckFormatException(message: String) : Exception(message)

/**
 * Reads the ssokssok deck formats:
 *  - JSON: {"decks":[{"name":..,"cards":[{"ko":..,"en":..}]}]}, a single deck object, or a bare array of cards.
 *  - Text: one card per line, fields separated by tabs, "|", ";" or ",". "# deck: Name" starts a deck.
 *    Plain-text exports from Anki ("Notes in Plain Text") are read too.
 */
object DeckFormat {
    private val json = Json { prettyPrint = true }

    enum class Field { KOREAN, ENGLISH, ROMANIZATION, EXAMPLE, EXAMPLE_TRANSLATION, NOTES, TAGS, TYPE, IGNORE }

    private val FIELD_NAMES: Map<String, Field> = buildMap {
        listOf("korean", "ko", "kr", "kor", "hangul", "front", "word", "한국어", "한글", "term")
            .forEach { put(it, Field.KOREAN) }
        listOf("english", "en", "eng", "meaning", "back", "translation", "definition", "영어", "뜻")
            .forEach { put(it, Field.ENGLISH) }
        listOf("romanization", "rom", "roman", "romanisation", "pronunciation", "romaja", "발음")
            .forEach { put(it, Field.ROMANIZATION) }
        listOf("example", "ex", "sentence", "example_ko", "exampleko", "예문")
            .forEach { put(it, Field.EXAMPLE) }
        listOf("example_translation", "exampletranslation", "example_en", "exampleen", "ex_en", "example translation")
            .forEach { put(it, Field.EXAMPLE_TRANSLATION) }
        listOf("notes", "note", "grammar", "explanation", "memo", "hint", "설명")
            .forEach { put(it, Field.NOTES) }
        listOf("tags", "tag").forEach { put(it, Field.TAGS) }
        listOf("type", "kind", "cardtype", "card_type").forEach { put(it, Field.TYPE) }
    }

    private val DEFAULT_COLUMNS = listOf(
        Field.KOREAN, Field.ENGLISH, Field.ROMANIZATION, Field.EXAMPLE,
        Field.EXAMPLE_TRANSLATION, Field.NOTES, Field.TAGS, Field.TYPE,
    )

    fun parse(text: String, fileName: String? = null): ParseResult {
        val clean = text.removePrefix("﻿").trim()
        if (clean.isEmpty()) throw DeckFormatException("The file is empty.")
        val fallbackName = fileName?.substringAfterLast('/')?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
        return if (clean.startsWith("{") || clean.startsWith("[")) {
            parseJson(clean, fallbackName)
        } else {
            parseText(clean, fallbackName)
        }
    }

    // ---------------------------------------------------------------- JSON

    private fun parseJson(text: String, fallbackName: String?): ParseResult {
        val root = try {
            Json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw DeckFormatException("This looks like JSON but it could not be read: ${e.message?.lineSequence()?.firstOrNull()}")
        }
        val warnings = mutableListOf<String>()
        val decks = when (root) {
            is JsonObject -> when {
                root.key("decks") is JsonArray -> (root.key("decks") as JsonArray).mapIndexedNotNull { i, el ->
                    (el as? JsonObject)?.let { jsonDeck(it, fallbackName, warnings, "Deck ${i + 1}") }
                }
                root.key("cards") is JsonArray -> listOf(jsonDeck(root, fallbackName, warnings, "Deck"))
                else -> throw DeckFormatException("The JSON needs a \"cards\" or \"decks\" list.")
            }
            is JsonArray -> {
                val objects = root.filterIsInstance<JsonObject>()
                if (objects.isNotEmpty() && objects.all { it.key("cards") is JsonArray }) {
                    objects.map { jsonDeck(it, fallbackName, warnings, "Deck") }
                } else {
                    listOf(ParsedDeck(fallbackName, cards = jsonCards(root, null, warnings, "Card")))
                }
            }
            else -> throw DeckFormatException("The JSON needs a \"cards\" or \"decks\" list.")
        }
        val nonEmpty = decks.filter { it.cards.isNotEmpty() }
        if (nonEmpty.isEmpty()) throw DeckFormatException("No cards found. Each card needs at least Korean and English.")
        return ParseResult(nonEmpty, warnings, "JSON")
    }

    private fun jsonDeck(obj: JsonObject, fallbackName: String?, warnings: MutableList<String>, label: String): ParsedDeck {
        val name = obj.str("name", "deck", "title")?.ifBlank { null } ?: fallbackName
        val defaultType = obj.str("type", "defaultType")?.let { CardType.fromKey(it) }
        val cards = (obj.key("cards") as? JsonArray) ?: JsonArray(emptyList())
        return ParsedDeck(
            name = name,
            description = obj.str("description", "desc").orEmpty(),
            symbol = obj.str("symbol", "icon").orEmpty().take(2),
            cards = jsonCards(cards, defaultType, warnings, if (name != null) "\"$name\" card" else "$label card"),
        )
    }

    private fun jsonCards(arr: JsonArray, defaultType: CardType?, warnings: MutableList<String>, label: String): List<ParsedCard> =
        arr.mapIndexedNotNull { i, el ->
            val o = el as? JsonObject
            if (o == null) {
                warnings += "$label ${i + 1}: not an object, skipped"
                return@mapIndexedNotNull null
            }
            val ko = o.str("korean", "ko", "kr", "front", "hangul", "word", "term").orEmpty()
            val en = o.str("english", "en", "meaning", "back", "translation", "definition").orEmpty()
            makeCard(
                fields = mapOf(
                    Field.KOREAN to ko,
                    Field.ENGLISH to en,
                    Field.ROMANIZATION to o.str("romanization", "rom", "roman", "pronunciation").orEmpty(),
                    Field.EXAMPLE to o.str("example", "ex", "sentence").orEmpty(),
                    Field.EXAMPLE_TRANSLATION to o.str("exampleTranslation", "example_translation", "exampleEn", "example_en", "exEn").orEmpty(),
                    Field.NOTES to o.str("notes", "note", "grammar", "explanation", "hint").orEmpty(),
                    Field.TAGS to (o.key("tags")?.let { t ->
                        if (t is JsonArray) t.mapNotNull { (it as? JsonPrimitive)?.content }.joinToString(" ") else (t as? JsonPrimitive)?.content
                    } ?: ""),
                    Field.TYPE to o.str("type", "kind").orEmpty(),
                ),
                defaultType = defaultType,
                warnings = warnings,
                where = "$label ${i + 1}",
            )
        }

    private fun JsonObject.key(name: String): JsonElement? =
        this[name] ?: entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    private fun JsonObject.str(vararg names: String): String? {
        for (n in names) {
            when (val v = key(n)) {
                null, JsonNull -> continue
                is JsonPrimitive -> return v.content
                is JsonArray -> return v.mapNotNull { (it as? JsonPrimitive)?.content }.joinToString("; ")
                else -> continue
            }
        }
        return null
    }

    // ---------------------------------------------------------------- Text

    private class DeckBuilder(var name: String?) {
        var description = ""
        var symbol = ""
        var defaultType: CardType? = null
        val cards = mutableListOf<ParsedCard>()
    }

    private val DIRECTIVE = Regex("^#\\s*([A-Za-z_ ]+?)\\s*:\\s*(.*)$")

    private fun parseText(text: String, fallbackName: String?): ParseResult {
        val lines = text.lines()
        val warnings = mutableListOf<String>()
        val decks = mutableListOf<DeckBuilder>()
        var current = DeckBuilder(fallbackName)
        var currentUsed = false
        var columns: List<Field> = DEFAULT_COLUMNS
        var headerChecked = false
        var delimiter: Char? = null

        fun startDeck(name: String) {
            if (currentUsed || current.cards.isNotEmpty()) decks += current
            current = DeckBuilder(name.trim().ifEmpty { fallbackName })
            currentUsed = true
            headerChecked = false
        }

        for ((index, raw) in lines.withIndex()) {
            val line = raw.trimEnd('\r')
            if (line.isBlank()) continue
            if (line.startsWith("#")) {
                val m = DIRECTIVE.find(line.trim())
                if (m != null) {
                    val key = m.groupValues[1].trim().lowercase()
                    val value = m.groupValues[2].trim()
                    when (key) {
                        "deck", "name" -> startDeck(value.substringAfterLast("::"))
                        "description", "desc" -> current.description = value
                        "symbol", "icon" -> current.symbol = value.take(2)
                        "type" -> current.defaultType = CardType.fromKey(value)
                        "columns", "fields" -> {
                            columns = value.split(Regex("[,\\t|;]")).map { FIELD_NAMES[it.trim().lowercase()] ?: Field.IGNORE }
                            headerChecked = true
                        }
                        "separator" -> delimiter = when (value.lowercase()) {
                            "tab" -> '\t'; "comma" -> ','; "semicolon" -> ';'; "pipe" -> '|'; "space" -> ' '
                            else -> value.firstOrNull() ?: delimiter
                        }
                        else -> Unit // Anki headers (#html, #notetype column...) and comments
                    }
                }
                continue
            }

            val sep = delimiter ?: detectDelimiter(lines).also { delimiter = it }
            val fields = splitFields(line, sep).map { cleanField(it) }

            if (!headerChecked) {
                headerChecked = true
                val mapped = fields.map { FIELD_NAMES[it.trim().lowercase()] }
                if (fields.size >= 2 && mapped.count { it != null } >= 2 && mapped[0] != null) {
                    columns = mapped.map { it ?: Field.IGNORE }
                    continue
                }
            }

            val map = mutableMapOf<Field, String>()
            fields.forEachIndexed { i, value ->
                val f = columns.getOrNull(i) ?: return@forEachIndexed
                if (f != Field.IGNORE && value.isNotBlank()) map[f] = value
            }
            makeCard(map, current.defaultType, warnings, "Line ${index + 1}")?.let { current.cards += it }
        }
        if (current.cards.isNotEmpty() || currentUsed) decks += current

        val parsed = decks.filter { it.cards.isNotEmpty() }.map {
            ParsedDeck(it.name, it.description, it.symbol, it.cards.toList())
        }
        if (parsed.isEmpty()) {
            throw DeckFormatException("No cards found. Put one card per line: Korean, a tab (or |), then English.")
        }
        val format = when (delimiter) {
            '\t' -> "Tab-separated text"
            '|' -> "Pipe-separated text"
            ';' -> "Semicolon-separated text"
            ',' -> "CSV"
            else -> "Text"
        }
        return ParseResult(parsed, warnings, format)
    }

    private fun detectDelimiter(lines: List<String>): Char {
        val data = lines.filter { it.isNotBlank() && !it.startsWith("#") }.take(20)
        return when {
            data.any { '\t' in it } -> '\t'
            data.count { '|' in it } * 2 >= data.size -> '|'
            data.count { ';' in it } * 2 >= data.size -> ';'
            else -> ','
        }
    }

    /** Splits a line, honouring double quotes ("a, b" stays one field). */
    internal fun splitFields(line: String, sep: Char): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' && (sb.isBlank() || quoted) -> quoted = !quoted
                c == sep && !quoted -> { out += sb.toString(); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out.map { it.trim() }
    }

    private val TAG = Regex("<[^>]+>")
    private val SOUND = Regex("\\[sound:[^]]*]")

    private fun cleanField(s: String): String {
        var t = s.replace(Regex("(?i)<br\\s*/?>"), " / ")
        t = SOUND.replace(t, "")
        t = TAG.replace(t, "")
        t = t.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
        return t.replace(Regex("\\s+"), " ").trim().trim('/').trim()
    }

    private fun makeCard(
        fields: Map<Field, String>,
        defaultType: CardType?,
        warnings: MutableList<String>,
        where: String,
    ): ParsedCard? {
        var ko = fields[Field.KOREAN].orEmpty().trim()
        var en = fields[Field.ENGLISH].orEmpty().trim()
        if (ko.isEmpty() || en.isEmpty()) {
            warnings += "$where: needs both Korean and English, skipped"
            return null
        }
        // Forgive swapped columns: English first, Korean second.
        if (!Hangul.containsHangul(ko) && Hangul.containsHangul(en)) {
            val t = ko; ko = en; en = t
        }
        val typeField = fields[Field.TYPE]?.trim().orEmpty()
        val type = when {
            typeField.isNotEmpty() -> CardType.fromKey(typeField)
            defaultType != null -> defaultType
            else -> guessType(ko)
        }
        return ParsedCard(
            korean = ko,
            english = en,
            romanization = fields[Field.ROMANIZATION].orEmpty().trim(),
            example = fields[Field.EXAMPLE].orEmpty().trim(),
            exampleTranslation = fields[Field.EXAMPLE_TRANSLATION].orEmpty().trim(),
            notes = fields[Field.NOTES].orEmpty().trim(),
            tags = fields[Field.TAGS].orEmpty().split(Regex("[,\\s]+")).map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() },
            type = type,
        )
    }

    private fun guessType(korean: String): CardType {
        val plain = korean.replace(Regex("\\{\\{|\\}\\}"), "").trim()
        return when {
            plain.startsWith("-") || plain.startsWith("~") || plain.contains("+") -> CardType.GRAMMAR
            plain.endsWith(".") || plain.endsWith("?") || plain.endsWith("!") || plain.count { it == ' ' } >= 2 -> CardType.SENTENCE
            else -> CardType.WORD
        }
    }

    // ---------------------------------------------------------------- Export

    fun toJson(decks: List<Pair<DeckEntity, List<CardEntity>>>): String {
        val root = buildJsonObject {
            put("format", "ssokssok/1")
            putJsonArray("decks") {
                decks.forEach { (deck, cards) ->
                    add(buildJsonObject {
                        put("name", deck.name)
                        if (deck.description.isNotBlank()) put("description", deck.description)
                        if (deck.symbol.isNotBlank()) put("symbol", deck.symbol)
                        put("cards", buildJsonArray {
                            cards.forEach { c ->
                                add(buildJsonObject {
                                    put("ko", c.korean)
                                    put("en", c.english)
                                    if (c.romanization.isNotBlank()) put("rom", c.romanization)
                                    if (c.example.isNotBlank()) put("example", c.example)
                                    if (c.exampleTranslation.isNotBlank()) put("exampleEn", c.exampleTranslation)
                                    if (c.notes.isNotBlank()) put("notes", c.notes)
                                    val tags = c.tags.split(" ").filter { it.isNotBlank() }
                                    if (tags.isNotEmpty()) putJsonArray("tags") { tags.forEach { add(JsonPrimitive(it)) } }
                                    put("type", c.type)
                                })
                            }
                        })
                    })
                }
            }
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    /** A short sample shown in the format guide and used by the "copy template" button. */
    val TEXT_TEMPLATE = """
        # deck: Food & Drinks
        # description: Words for ordering at a restaurant
        # symbol: 밥
        korean	english	romanization	example	example_translation	notes	tags
        사과	apple	sagwa	사과를 먹어요.	I eat an apple.		food fruit
        물	water	mul	물 주세요.	Water, please.		drink
        맛있다	to be delicious	masitda	이 김치 정말 맛있어요!	This kimchi is really delicious!	Descriptive verb: 맛있어요 in polite speech	food
        저{{는}} 학생이에요.	I am a student.				는 marks the topic after a vowel	particles
    """.trimIndent()

    val JSON_TEMPLATE = """
        {
          "decks": [
            {
              "name": "Food & Drinks",
              "description": "Words for ordering at a restaurant",
              "symbol": "밥",
              "cards": [
                { "ko": "사과", "en": "apple", "rom": "sagwa", "example": "사과를 먹어요.", "exampleEn": "I eat an apple.", "tags": ["food"] },
                { "ko": "-고 싶다", "en": "to want to (do)", "type": "grammar", "notes": "Verb stem + 고 싶다", "example": "한국에 가고 싶어요.", "exampleEn": "I want to go to Korea." },
                { "ko": "저{{는}} 학생이에요.", "en": "I am a student.", "type": "sentence", "notes": "는 marks the topic after a vowel" }
              ]
            }
          ]
        }
    """.trimIndent()

    /** Prompt the learner can paste into any AI chat to generate a deck in this format. */
    fun aiPrompt(topic: String = "<TOPIC>", count: Int = 30, level: String = "beginner") = """
        Create a Korean study deck for a $level learner about "$topic" with $count cards.
        Output only a tab-separated text block in this exact format, no commentary:

        # deck: <deck name>
        # description: <one sentence>
        # symbol: <one Korean syllable that represents the deck>
        korean	english	romanization	example	example_translation	notes	tags	type

        Rules:
        - One card per line, fields separated by a single TAB.
        - type is word, sentence or grammar.
        - english: separate alternative meanings with ";".
        - example: a short natural sentence in polite speech (해요체) using the word; example_translation is its English.
        - notes: a short tip (irregular conjugation, nuance, mnemonic). Leave empty if nothing useful.
        - For grammar cards, put the pattern in korean (e.g. -고 싶다) and explain the usage in notes.
        - To practise particles or endings, write a cloze in korean with double braces, e.g. 저{{는}} 학생이에요.
    """.trimIndent()
}
