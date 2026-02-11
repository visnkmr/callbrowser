package com.example.callbrowser.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.callbrowser.data.local.AppDatabase
import com.example.callbrowser.data.repository.ContactRepository

class ContactSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val database = AppDatabase.getDatabase(applicationContext)
            val contactRepository = ContactRepository(applicationContext, database.contactDao())

            val success = contactRepository.syncContacts()

            if (success) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "contact_sync_worker"
    }
}
