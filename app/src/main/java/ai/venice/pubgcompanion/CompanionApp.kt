package ai.venice.pubgcompanion

import android.app.Application
import ai.venice.pubgcompanion.data.AppDatabase
import ai.venice.pubgcompanion.data.ProfileRepository
import ai.venice.pubgcompanion.service.Notifier
import kotlinx.coroutines.*

class CompanionApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannels(this)
        scope.launch {
            ProfileRepository(AppDatabase.get(this@CompanionApp).profiles()).ensureDefault()
        }
    }
}
