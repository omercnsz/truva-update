package com.truva

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "selected_apps")
data class AppEntity(
    @PrimaryKey val packageName: String, // com.instagram.android gibi benzersiz kimlik
    val label: String,                   // Uygulama adı
    val isSystemApp: Boolean,            // Sistem uygulaması mı?
    val isActive: Boolean = true         // Tünelden geçsin mi?
)
