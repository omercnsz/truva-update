package com.truva

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Kullanıcı tarafından seçilmiş bölge profili kaydı. Aktif profil spoofing koordinatörüne
 * uygulanır.
 */
@Entity(tableName = "region_profiles")
data class RegionProfileEntity(
        @PrimaryKey val profileId: String, // "US", "DE", "TR" vb.
        val displayName: String,
        val isSelected: Boolean = false,
        val lastUsedAt: Long = 0L
)
