package com.kharcha.app.data

import android.content.Context
import androidx.room.Room

// A single process-wide Room instance shared by the UI and background sync worker.
object SyncDatabaseFactory {
    @Volatile private var instance: KharchaDatabase? = null
    fun get(context: Context): KharchaDatabase = instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(context.applicationContext, KharchaDatabase::class.java, "kharcha.db")
            .addMigrations(KharchaDatabase.MIGRATION_1_2, KharchaDatabase.MIGRATION_2_3)
            .build().also { instance = it }
    }
}
