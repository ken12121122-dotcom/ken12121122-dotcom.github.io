package com.fox.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface NodeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(node: NodeEntity)

    @Query("SELECT * FROM nodes WHERE node_id = :nodeId")
    suspend fun get(nodeId: String): NodeEntity?

    @Query("SELECT * FROM nodes ORDER BY title")
    fun observeAll(): Flow<List<NodeEntity>>

    @Query("SELECT * FROM nodes WHERE review_status IN ('generated', 'reviewed') ORDER BY title")
    fun observePending(): Flow<List<NodeEntity>>

    @Query("SELECT * FROM nodes ORDER BY node_id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<NodeEntity>>

    @Query("DELETE FROM nodes WHERE node_id = :nodeId")
    suspend fun delete(nodeId: String)

    @Query("SELECT COUNT(*) FROM nodes")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM nodes")
    fun observeCount(): Flow<Int>

    @Transaction
    @Query("SELECT * FROM nodes n JOIN node_search fts ON n.node_id = fts.nodeId WHERE node_search MATCH :ftsQuery ORDER BY n.title")
    suspend fun search(ftsQuery: String): List<NodeEntity>
}

@Dao
interface EdgeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(edges: List<EdgeEntity>)

    @Query("DELETE FROM edges WHERE from_node = :nodeId")
    suspend fun deleteOutgoing(nodeId: String)

    @Query("SELECT * FROM edges WHERE from_node = :nodeId")
    fun observeOutgoing(nodeId: String): Flow<List<EdgeEntity>>

    @Query("SELECT * FROM edges WHERE to_node = :nodeId")
    fun observeIncoming(nodeId: String): Flow<List<EdgeEntity>>

    @Query("UPDATE edges SET target_resolved = (SELECT COUNT(*) > 0 FROM nodes WHERE nodes.node_id = edges.to_node)")
    suspend fun recomputeTargetResolution()
}

@Dao
interface ContentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contents: List<ContentEntity>)

    @Query("DELETE FROM contents WHERE node_id = :nodeId")
    suspend fun deleteForNode(nodeId: String)

    @Query("SELECT * FROM contents WHERE node_id = :nodeId ORDER BY sort_order")
    fun observeForNode(nodeId: String): Flow<List<ContentEntity>>
}

@Dao
interface NodeSearchFtsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: NodeSearchFtsEntity)

    @Query("DELETE FROM node_search WHERE nodeId = :nodeId")
    suspend fun deleteForNode(nodeId: String)
}

@Dao
interface SyncStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SyncStateEntity)

    @Query("SELECT * FROM sync_state WHERE drive_file_id = :driveFileId")
    suspend fun get(driveFileId: String): SyncStateEntity?

    @Query("SELECT * FROM sync_state")
    suspend fun getAll(): List<SyncStateEntity>

    @Query("SELECT * FROM sync_state WHERE sync_status != 'SYNCED'")
    fun observeUnsynced(): Flow<List<SyncStateEntity>>

    @Query("DELETE FROM sync_state WHERE drive_file_id = :driveFileId")
    suspend fun delete(driveFileId: String)
}

@Dao
interface AppUsageLogDao {
    @Insert
    suspend fun insert(entry: AppUsageLogEntity)

    @Query("SELECT * FROM app_usage_log ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<AppUsageLogEntity>>
}
