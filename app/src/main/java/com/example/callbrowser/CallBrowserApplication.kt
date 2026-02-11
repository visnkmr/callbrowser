package com.example.callbrowser

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.callbrowser.data.sync.ContactSyncWorker
import visnkmr.apps.crasher.Crasher
import java.util.concurrent.TimeUnit

class CallBrowserApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Crasher for crash reporting
        Crasher(this)
            .setEmail("support@example.com")
            .setDebugMessage("CallBrowser Debug Build")
            .setCrashActivityEnabled(true)

        // Schedule periodic contact sync
        scheduleContactSync()
    }

    private fun scheduleContactSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(false)
            .build()

        val syncWorkRequest = PeriodicWorkRequestBuilder<ContactSyncWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ContactSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncWorkRequest
        )
    }
}