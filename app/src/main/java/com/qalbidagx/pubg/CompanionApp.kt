package com.qalbidagx.pubg

import android.app.Application
import com.qalbidagx.pubg.data.AppDatabase
import com.qalbidagx.pubg.data.ProfileRepository
import com.qalbidagx.pubg.service.Notifier
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
