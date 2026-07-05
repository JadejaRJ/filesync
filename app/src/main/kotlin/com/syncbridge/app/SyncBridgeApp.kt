package com.syncbridge.app

import android.app.Application
import androidx.work.Configuration
import com.syncbridge.app.sync.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class SyncBridgeApp : Application(), Configuration.Provider {

    @Inject lateinit var hiltWorkerFactory: androidx.hilt.work.HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(hiltWorkerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        NotificationHelper.ensureChannels(this)
    }
}
