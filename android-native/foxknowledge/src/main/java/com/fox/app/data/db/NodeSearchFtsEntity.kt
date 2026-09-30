package com.fox.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * Standalone FTS4 index over title/tags/content (SearchEngine P0 per KB-APP-002:
 * "第一階段：標題／Tag／Frontmatter／Content／Relations 的本機全文搜尋"). Kept as a
 * plain FTS4 table (not `contentEntity`-linked) because NodeEntity's primary key
 * is a String node_id, and Room's content-linked FTS requires the shadowed
 * entity's rowid to be the primary key.
 */
@Entity(tableName = "node_search")
@Fts4(notIndexed = ["nodeId"])
data class NodeSearchFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Int? = null,

    val nodeId: String,
    val title: String,
    val tags: String,
    val content: String,
)
