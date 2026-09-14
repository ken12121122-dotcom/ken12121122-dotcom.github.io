package com.fox.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One `動詞:: [[目標]]` relation line from a node's body, per FOX_SCHEMA's
 * relation-type list (related_to / derived_from / depends_on / contradicts / ...).
 */
@Entity(
    tableName = "edges",
    indices = [Index("from_node"), Index("to_node")],
)
data class EdgeEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "edge_id")
    val edgeId: Long = 0,

    @ColumnInfo(name = "from_node")
    val fromNode: String,

    val relation: String,

    @ColumnInfo(name = "to_node")
    val toNode: String,

    /** True only if `to_node` currently resolves to a row in `nodes` — surfaced as a broken-link gap, never silently dropped. */
    @ColumnInfo(name = "target_resolved")
    val targetResolved: Boolean,
)
