package com.truva

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
        entities =
                [
                        AppEntity::class,
                        ProxyEntity::class,
                        SettingsEntity::class,
                        RegionProfileEntity::class,
                        SimProtectionEntity::class],
        version = 14,
        exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
    abstract fun simProtectionDao(): SimProtectionDao

    companion object {
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN nitroDpiAppMode TEXT NOT NULL DEFAULT 'all'")
                db.execSQL("ALTER TABLE settings ADD COLUMN nitroDpiApps TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE
                    ?: synchronized(this) {
                        val instance =
                                Room.databaseBuilder(
                                                context.applicationContext,
                                                AppDatabase::class.java,
                                                "truva_database"
                                        )
                                        // ⚠️ DİKKAT: Bu, şema artışında tüm verileri siler!
                                        // Üretim sürümü için Room Migration nesneleri yazılmalı.
                                        // Bkz:
                                        // https://developer.android.com/training/data-storage/room/migrating-db-versions
                                        .fallbackToDestructiveMigration()
                                        .addMigrations(MIGRATION_13_14)
                                        .build()
                        INSTANCE = instance
                        instance
                    }
        }
    }
}
