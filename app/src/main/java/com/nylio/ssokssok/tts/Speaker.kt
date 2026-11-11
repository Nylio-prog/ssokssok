package com.nylio.ssokssok.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import com.nylio.ssokssok.korean.Cloze
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/** Korean text-to-speech. A second tap on the same text plays it slower. */
class Speaker(private val context: Context) : TextToSpeech.OnInitListener {
    enum class Status { LOADING, READY, UNAVAILABLE }

    private var tts: TextToSpeech? = null
    private val _status = MutableStateFlow(Status.LOADING)
    val status: StateFlow<Status> = _status
    private var pending: String? = null
    private var lastText: String? = null
    private var lastAt = 0L

    private fun ensure() {
        if (tts == null) tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            _status.value = Status.UNAVAILABLE
            return
        }
        val res = engine.setLanguage(Locale.KOREAN)
        _status.value = if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            Status.UNAVAILABLE
        } else {
            Status.READY
        }
        pending?.let { speak(it) }
        pending = null
    }

    fun warmUp() = ensure()

    fun speak(text: String) {
        val clean = Cloze.plain(text).replace(Regex("[{}]"), "").trim()
        if (clean.isEmpty()) return
        ensure()
        val engine = tts
        if (engine == null || _status.value == Status.LOADING) {
            pending = clean
            return
        }
        if (_status.value != Status.READY) return
        val now = System.currentTimeMillis()
        val slow = clean == lastText && now - lastAt < 4_000
        engine.setSpeechRate(if (slow) 0.6f else 0.95f)
        lastText = if (slow) null else clean
        lastAt = now
        engine.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "ssok-${now}")
    }

    fun stop() {
        tts?.stop()
    }
}
