package com.fox.app.graph

import android.content.Context
import com.fox.app.data.db.FoxDatabase
import com.fox.app.data.profile.KnowledgeProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Read-only projection from the active FOX knowledge profile into the existing
 * Amin Unified Graph contract. FOX remains the knowledge source; this object
 * only exposes a visual read model.
 */
object FoxGraphProjection {

    @JvmStatic
    fun snapshotJson(context: Context): String = runBlocking {
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val profileStore = KnowledgeProfileStore(appContext)
            val profile = profileStore.activeProfile()
            val database = FoxDatabase.getInstance(appContext, profile.id)

            val nodes = database.nodeDao().getAll()
            val edges = database.edgeDao().getAll()

            val nodeIds = nodes.map { it.nodeId }.toSet()
            val nodeJson = JSONArray()
            val relationJson = JSONArray()

            nodes.forEach { node ->
                val graphId = graphNodeId(profile.id, node.nodeId)
                nodeJson.put(
                    JSONObject()
                        .put("id", graphId)
                        .put("sourceNodeId", node.nodeId)
                        .put("title", node.title)
                        .put("summary", buildSummary(node.documentType, node.reviewStatus))
                        .put("detail", buildDetail(node))
                        .put("groupId", "group:fox-knowledge")
                        .put("domainId", "knowledge:fox")
                        .put("entityType", "knowledge")
                        .put("nodeType", node.documentType)
                        .put("status", graphStatus(node.status, node.reviewStatus))
                        .put("profileId", profile.id)
                        .put("profileName", profile.name)
                        .put("reviewStatus", node.reviewStatus)
                        .put("sourceStatus", node.sourceStatus ?: "")
                        .put("version", node.version ?: "")
                        .put("tags", node.tags)
                        .put("owner", node.owner ?: "")
                        .put("confidence", node.confidence ?: JSONObject.NULL)
                        .put("driveFileId", node.driveFileId),
                )
            }

            edges.forEach { edge ->
                if (!edge.targetResolved || edge.fromNode !in nodeIds || edge.toNode !in nodeIds) {
                    return@forEach
                }
                relationJson.put(
                    JSONObject()
                        .put("id", "fox-edge:${profile.id}:${edge.edgeId}")
                        .put("from", graphNodeId(profile.id, edge.fromNode))
                        .put("to", graphNodeId(profile.id, edge.toNode))
                        .put("type", edge.relation.ifBlank { "related_to" })
                        .put("status", "active")
                        .put("profileId", profile.id)
                        .put("gate", JSONObject().put("enabled", true))
                        .put("commandChain", JSONArray()),
                )
            }

            JSONObject()
                .put("format", "fox-unified-graph-projection")
                .put("version", 1)
                .put("profileId", profile.id)
                .put("profileName", profile.name)
                .put("sourceLabel", profile.sourceLabel ?: "")
                .put("nodeCount", nodeJson.length())
                .put("relationCount", relationJson.length())
                .put("nodes", nodeJson)
                .put("relations", relationJson)
                .toString()
        }
    }

    private fun graphNodeId(profileId: String, nodeId: String): String =
        "fox:$profileId:$nodeId"

    private fun buildSummary(documentType: String, reviewStatus: String): String =
        listOf(documentType, reviewStatus).filter { it.isNotBlank() }.joinToString(" · ")

    private fun buildDetail(node: com.fox.app.data.db.NodeEntity): String = buildString {
        append("node_id: ").append(node.nodeId)
        append("\ndocument_type: ").append(node.documentType)
        append("\nreview_status: ").append(node.reviewStatus)
        if (!node.sourceStatus.isNullOrBlank()) {
            append("\nsource_status: ").append(node.sourceStatus)
        }
        if (node.tags.isNotBlank()) {
            append("\ntags: ").append(node.tags)
        }
    }

    private fun graphStatus(status: String, reviewStatus: String): String {
        val normalizedReview = reviewStatus.lowercase()
        return when {
            status.equals("blocked", ignoreCase = true) -> "blocked"
            normalizedReview in setOf("generated", "reviewed", "pending", "pending_verification") -> "pending"
            else -> "active"
        }
    }
}
