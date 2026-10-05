package com.copiloto.motorista.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RideHistoryEntity::class, PendingAlertEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class CopilotoDatabase : RoomDatabase() {

    abstract fun rideHistoryDao(): RideHistoryDao

    abstract fun pendingAlertDao(): PendingAlertDao

    companion object {
        @Volatile
        private var instance: CopilotoDatabase? = null

        /** v4: adds `origin` to queued alerts without wiping local data (history + queue). */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE pending_alert ADD COLUMN origin TEXT NOT NULL DEFAULT 'APP'",
                )
            }
        }

        fun get(context: Context): CopilotoDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CopilotoDatabase::class.java,
                    "copiloto.db",
                )
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration() // safety net for unknown paths only
                    .build().also { instance = it }
            }
    }
}
