package com.nylio.ssokssok

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nylio.ssokssok.data.ThemeMode
import com.nylio.ssokssok.ui.SsokApp
import com.nylio.ssokssok.ui.theme.SsokTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            val settings by container.settings.flow.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { dark },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { dark },
                )
                onDispose { }
            }
            SsokTheme(dark = dark) {
                SsokApp(container)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Accepts shared text (e.g. a deck generated in a chat app) and opened deck files. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val incoming = when (intent.action) {
            Intent.ACTION_SEND -> {
                val stream = intentStream(intent)
                when {
                    stream != null -> readUri(stream)
                    else -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { IncomingImport(it, null) }
                }
            }
            Intent.ACTION_VIEW -> intent.data?.let { readUri(it) }
            else -> null
        }
        if (incoming != null && incoming.text.isNotBlank()) container.incomingImport.value = incoming
    }

    @Suppress("DEPRECATION")
    private fun intentStream(intent: Intent): Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM)

    private fun readUri(uri: Uri): IncomingImport? = runCatching {
        val text = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return null
        var name: String? = null
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) name = c.getString(0)
        }
        IncomingImport(text, name ?: uri.lastPathSegment)
    }.getOrNull()
}
