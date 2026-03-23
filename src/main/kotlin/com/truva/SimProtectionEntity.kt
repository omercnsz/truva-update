package com.truva

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SimProtectionDao {
    @Query("SELECT * FROM sim_protection")
    fun getAllProtectedAppsFlow(): Flow<List<SimProtectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProtectedApp(app: SimProtectionEntity)

    @Delete
    suspend fun deleteProtectedApp(app: SimProtectionEntity)

    @Query("SELECT * FROM sim_protection WHERE packageName = :packageName AND userId = :userId LIMIT 1")
    suspend fun getProtectedApp(packageName: String, userId: Int): SimProtectionEntity?
}

@Entity(tableName = "sim_protection", primaryKeys = ["packageName", "userId"])
data class SimProtectionEntity(
    val packageName: String,
    val userId: Int,
    val isProtected: Boolean
)
