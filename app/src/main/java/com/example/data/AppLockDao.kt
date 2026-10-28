package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LockedAppDao {
    @Query("SELECT * FROM locked_apps ORDER BY appName ASC")
    fun observeAllLockedAppRecords(): Flow<List<LockedAppEntity>>

    @Query("SELECT * FROM locked_apps WHERE isLocked = 1")
    suspend fun getCurrentlyLockedAppsSnapshot(): List<LockedAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLockedApp(entity: LockedAppEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAllLockedApps(entities: List<LockedAppEntity>)

    @Query("UPDATE locked_apps SET isLocked = 0")
    suspend fun unlockAllApps()

    @Query("SELECT COUNT(*) FROM locked_apps")
    suspend fun getRecordCount(): Int
}

@Dao
interface SecurityProfileDao {
    @Query("SELECT * FROM security_profiles ORDER BY id ASC")
    fun observeProfiles(): Flow<List<SecurityProfileEntity>>

    @Query("SELECT * FROM security_profiles ORDER BY id ASC")
    suspend fun getProfilesSnapshot(): List<SecurityProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: SecurityProfileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllProfiles(profiles: List<SecurityProfileEntity>)

    @Query("UPDATE security_profiles SET isActive = CASE WHEN id = :profileId THEN 1 ELSE 0 END")
    suspend fun setActiveProfile(profileId: Int)

    @Query("DELETE FROM security_profiles WHERE id = :profileId")
    suspend fun deleteProfile(profileId: Int)
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 100")
    fun observeRecentLogs(): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AuditLogEntity)

    @Query("DELETE FROM audit_logs")
    suspend fun clearAllLogs()

    @Query("SELECT COUNT(*) FROM audit_logs")
    suspend fun getLogCount(): Int
}
