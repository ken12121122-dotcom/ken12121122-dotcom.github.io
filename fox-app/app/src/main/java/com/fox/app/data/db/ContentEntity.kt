package com.fox.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One `## section` block of a node's body (正式知識 / 用途 / 未解決缺口 / ...), stored verbatim. */
@Entity(
    tableName = "contents",
    indices = [Index("node_id")],
)
data class ContentEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "content_id")
    val contentId: Long = 0,

    @ColumnInfo(name = "node_id")
    val nodeId: String,

    val section: String,

    val content: String,

    /** Order of this section within the source document, so re-render preserves document order. */
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
)
