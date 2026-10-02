package com.copiloto.motorista.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [RideHistoryEntity::class, PendingAlertEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class CopilotoDatabase : RoomDatabase() {

    abstract fun rideHistoryDao(): RideHistoryDao

    abstract fun pendingAlertDao(): PendingAlertDao

    companion object {
        @Volatile
        private var instance: CopilotoDatabase? = null

        fun get(context: Context): CopilotoDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CopilotoDatabase::class.java,
                    "copiloto.db",
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
