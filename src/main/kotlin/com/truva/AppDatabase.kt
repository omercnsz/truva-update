package com.truva

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
        entities =
                [
                        AppEntity::class,
                        ProxyEntity::class,
                        SettingsEntity::class,
                        RegionProfileEntity::class,
                        SimProtectionEntity::class],
        version = 12,
        exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
    abstract fun simProtectionDao(): SimProtectionDao

    companion object {
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
                                        .build()
                        INSTANCE = instance
                        instance
                    }
        }
    }
}
