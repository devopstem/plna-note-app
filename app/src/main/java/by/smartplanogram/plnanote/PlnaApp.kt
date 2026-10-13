package by.smartplanogram.plnanote

import android.app.Application
import by.smartplanogram.plnanote.data.OfflineRepository
import by.smartplanogram.plnanote.data.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PlnaApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Тема сразу на main (иначе не применяется)
        UserSettings.get(this).apply {
            applyThemeNow()
            applyLanguageNow()
        }
        appScope.launch {
            OfflineRepository.get(this@PlnaApp).ensureSynced()
        }
    }

    companion object {
        lateinit var instance: PlnaApp
            private set
    }
}
