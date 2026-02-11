package com.example.callbrowser.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.callbrowser.data.local.dao.CallDao
import com.example.callbrowser.data.local.dao.ContactDao
import com.example.callbrowser.data.local.dao.MessageDao
import com.example.callbrowser.data.local.entity.CallEntity
import com.example.callbrowser.data.local.entity.ContactEntity
import com.example.callbrowser.data.local.entity.MessageEntity

@Database(
    entities = [CallEntity::class, MessageEntity::class, ContactEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun callDao(): CallDao
    abstract fun messageDao(): MessageDao
    abstract fun contactDao(): ContactDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "call_browser_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}