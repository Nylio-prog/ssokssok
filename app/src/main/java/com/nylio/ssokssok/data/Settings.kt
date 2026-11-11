package com.nylio.ssokssok.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Which side the learner has to come up with. */
enum class DirectionMode(val label: String, val short: String) {
    RECOGNIZE("Korean → English", "한 → EN"),
    PRODUCE("English → Korean", "EN → 한"),
    MIXED("Both, mixed", "Mix"),
}

enum class AnswerMode(val label: String) {
    FLIP("Flip"),
    TYPE("Type"),
}

data class AppSettings(
    val newPerDay: Int = 15,
    val retention: Double = 0.90,
    val autoplayAudio: Boolean = true,
    val showRomanization: Boolean = true,
    val haptics: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val direction: DirectionMode = DirectionMode.RECOGNIZE,
    val answerMode: AnswerMode = AnswerMode.FLIP,
    val sessionSize: Int = 20,
    val starterSeeded: Boolean = false,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _flow = MutableStateFlow(read())
    val flow: StateFlow<AppSettings> = _flow.asStateFlow()
    val value: AppSettings get() = _flow.value

    private fun read() = AppSettings(
        newPerDay = prefs.getInt("newPerDay", 15),
        retention = prefs.getFloat("retention", 0.90f).toDouble(),
        autoplayAudio = prefs.getBoolean("autoplayAudio", true),
        showRomanization = prefs.getBoolean("showRomanization", true),
        haptics = prefs.getBoolean("haptics", true),
        theme = enumOr(prefs.getString("theme", null), ThemeMode.SYSTEM),
        direction = enumOr(prefs.getString("direction", null), DirectionMode.RECOGNIZE),
        answerMode = enumOr(prefs.getString("answerMode", null), AnswerMode.FLIP),
        sessionSize = prefs.getInt("sessionSize", 20),
        starterSeeded = prefs.getBoolean("starterSeeded", false),
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(_flow.value)
        prefs.edit()
            .putInt("newPerDay", s.newPerDay)
            .putFloat("retention", s.retention.toFloat())
            .putBoolean("autoplayAudio", s.autoplayAudio)
            .putBoolean("showRomanization", s.showRomanization)
            .putBoolean("haptics", s.haptics)
            .putString("theme", s.theme.name)
            .putString("direction", s.direction.name)
            .putString("answerMode", s.answerMode.name)
            .putInt("sessionSize", s.sessionSize)
            .putBoolean("starterSeeded", s.starterSeeded)
            .apply()
        _flow.value = s
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default
}
