package com.fox.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class UsageAction {
    OPENED,
    SEARCHED,
    REFERENCED,
    EDITED,
    LINKED,
    SKILL_USED,
    AI_RETRIEVED,
}

/**
 * App-local usage telemetry. Named app_usage_log (not usage_log) to stay distinct
 * from KB-PKM-002's knowledge-base reference log, per KB-APP-002's unresolved_gaps
 * note. Record-only for P0 — no scoring or pattern detection (KB-COORD-003).
 */
@Entity(
    tableName = "app_usage_log",
    indices = [Index("node_id"), Index("timestamp")],
)
data class AppUsageLogEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "log_id")
    val logId: Long = 0,

    @ColumnInfo(name = "node_id")
    val nodeId: String?,

    val action: UsageAction,

    val query: String?,

    @ColumnInfo(name = "agent_id")
    val agentId: String?,

    @ColumnInfo(name = "session_id")
    val sessionId: String?,

    val timestamp: Long,
)
