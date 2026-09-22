package com.fox.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SyncStatus {
    SYNCED,
    REMOTE_CHANGED,
    LOCAL_CHANGED,
    CONFLICT,
    ERROR,
}

/**
 * Per-file sync bookkeeping so a periodic sync can diff against Drive's
 * modifiedTime instead of re-downloading and re-parsing the whole vault
 * every run (KB-APP-002 "新增兩張表" — sync_state).
 *
 * Keyed by Drive file id (stable even before a file successfully parses),
 * not node_id (which only exists once frontmatter parses cleanly).
 */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "drive_file_id")
    val driveFileId: String,

    @ColumnInfo(name = "node_id")
    val nodeId: String?,

    @ColumnInfo(name = "remote_modified_time")
    val remoteModifiedTime: String?,

    @ColumnInfo(name = "local_modified_time")
    val localModifiedTime: Long?,

    @ColumnInfo(name = "remote_hash")
    val remoteHash: String?,

    @ColumnInfo(name = "local_hash")
    val localHash: String?,

    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus,

    @ColumnInfo(name = "last_error")
    val lastError: String?,
)
