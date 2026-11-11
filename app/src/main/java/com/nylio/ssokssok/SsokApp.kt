package com.nylio.ssokssok

import android.app.Application
import android.content.Context
import com.nylio.ssokssok.data.Repository
import com.nylio.ssokssok.data.SettingsStore
import com.nylio.ssokssok.data.db.AppDatabase
import com.nylio.ssokssok.tts.Speaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Text or a file handed to the app from elsewhere (share sheet, "open with"). */
data class IncomingImport(val text: String, val fileName: String?)

class AppContainer(context: Context) {
    val settings = SettingsStore(context)
    val database = AppDatabase.create(context)
    val repository = Repository(database, settings)
    val speaker = Speaker(context)
    val incomingImport = MutableStateFlow<IncomingImport?>(null)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

class SsokApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.scope.launch {
            container.repository.seedStarterIfNeeded {
                assets.open("starter.json").bufferedReader().use { it.readText() }
            }
        }
    }
}

val Context.container: AppContainer get() = (applicationContext as SsokApp).container
