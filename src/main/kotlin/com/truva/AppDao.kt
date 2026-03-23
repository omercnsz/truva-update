package com.truva

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM selected_apps") suspend fun getAllApps(): List<AppEntity>

    // reactive stream of all apps; emits whenever any row changes
    @Query("SELECT * FROM selected_apps") fun getAllAppsFlow(): Flow<List<AppEntity>>

    @Query("SELECT * FROM selected_apps WHERE isActive = 1")
    suspend fun getActiveApps(): List<AppEntity>

    // reactive stream of active apps; emits on every insert/delete/update
    @Query("SELECT * FROM selected_apps WHERE isActive = 1")
    fun getActiveAppsFlow(): Flow<List<AppEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertApp(app: AppEntity)

    @Delete suspend fun deleteApp(app: AppEntity)

    // ----- proxy management -----
    @Query("SELECT * FROM proxies")
    fun getAllProxies(): kotlinx.coroutines.flow.Flow<List<ProxyEntity>>

    @Query("SELECT * FROM proxies") suspend fun getAllProxiesList(): List<ProxyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertProxy(proxy: ProxyEntity)

    @Delete suspend fun deleteProxy(proxy: ProxyEntity)

    @Query("UPDATE proxies SET isSelected = 0") suspend fun deselectAllProxies()

    @Query("UPDATE proxies SET isSelected = 1 WHERE id = :proxyId")
    suspend fun selectProxy(proxyId: Int)

    @Query("SELECT * FROM proxies WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedProxy(): ProxyEntity?

    @Transaction
    suspend fun setActiveProxy(proxyId: Int) {
        deselectAllProxies()
        selectProxy(proxyId)
    }

    // Kill-switch için aktif uygulama sayısını sorgulama
    @Query("SELECT COUNT(*) FROM selected_apps WHERE isActive = 1")
    suspend fun getActiveAppCount(): Int

    // settings tablosuna erişim
    @Query("SELECT * FROM settings WHERE id = 0")
    fun getSettingsFlow(): kotlinx.coroutines.flow.Flow<SettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateSettings(settings: SettingsEntity)

    // ═══════════════════════════════════════════
    // Region Profile Management
    // ═══════════════════════════════════════════

    @Query("SELECT * FROM region_profiles ORDER BY lastUsedAt DESC")
    fun getRegionProfilesFlow(): Flow<List<RegionProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRegionProfile(profile: RegionProfileEntity)

    @Query("UPDATE region_profiles SET isSelected = 0") suspend fun deselectAllRegionProfiles()

    @Query(
            "UPDATE region_profiles SET isSelected = 1, lastUsedAt = :timestamp WHERE profileId = :profileId"
    )
    suspend fun selectRegionProfile(profileId: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM region_profiles WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedRegionProfile(): RegionProfileEntity?

    @Transaction
    suspend fun setActiveRegionProfile(profileId: String) {
        deselectAllRegionProfiles()
        selectRegionProfile(profileId)
    }
}
