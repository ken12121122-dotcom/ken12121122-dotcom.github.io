package com.fox.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local mirror of a Drive `.md` document's YAML frontmatter.
 * Canonical source stays the Drive file — this row is a disposable, rebuildable
 * index over it (KB-APP-002: "Room 內資料可重新由正式 .md 解析建立").
 */
@Entity(tableName = "nodes")
data class NodeEntity(
    @PrimaryKey
    @ColumnInfo(name = "node_id")
    val nodeId: String,

    val title: String,

    @ColumnInfo(name = "document_type")
    val documentType: String,

    val status: String,

    @ColumnInfo(name = "review_status")
    val reviewStatus: String,

    val version: String?,

    @ColumnInfo(name = "tags")
    val tags: String, // comma-joined, denormalized for FTS/display

    val owner: String?,

    val confidence: Double?,

    @ColumnInfo(name = "source_status")
    val sourceStatus: String?,

    @ColumnInfo(name = "drive_file_id")
    val driveFileId: String,

    @ColumnInfo(name = "remote_modified_time")
    val remoteModifiedTime: String?,

    @ColumnInfo(name = "content_hash")
    val contentHash: String,
)
