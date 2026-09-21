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
 * Read-only FOX -> Amin Graph projection for one explicit knowledge profile.
 *
 * The profile source remains canonical. This projection only translates the
 * local Room mirror into the graph contract consumed by the existing Wiki canvas.
 */
object FoxGraphProjection {
    @JvmStatic
    fun snapshotJson(context: Context, profileId: String): String = runBlocking {
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val profileStore = KnowledgeProfileStore(appContext)
            val profile = profileStore.profile(profileId)
                ?: return@withContext empty(profileId, "Unknown knowledge profile").toString()
            val database = FoxDatabase.getInstance(appContext, profile.id)
            val nodes = database.nodeDao().getAll()
            val edges = database.edgeDao().getAll()
            val known = nodes.map { it.nodeId }.toSet()

            val nodeJson = JSONArray()
            nodes.forEach { node ->
                nodeJson.put(
                    JSONObject()
                        .put("id", graphNodeId(profile.id, node.nodeId))
                        .put("sourceNodeId", node.nodeId)
                        .put("title", node.title)
                        .put("summary", listOf(node.documentType, node.reviewStatus)
                            .filter { it.isNotBlank() }.joinToString(" · "))
                        .put("detail", buildString {
                            append("node_id: ").append(node.nodeId)
                            append("\ndocument_type: ").append(node.documentType)
                            append("\nreview_status: ").append(node.reviewStatus)
                            if (node.tags.isNotBlank()) append("\ntags: ").append(node.tags)
                        })
                        .put("groupId", "group:fox-knowledge")
                        .put("domainId", "knowledge:fox")
                        .put("entityType", "knowledge")
                        .put("nodeType", node.documentType)
                        .put("status", when {
                            node.status.equals("blocked", ignoreCase = true) -> "blocked"
                            node.reviewStatus.lowercase() in setOf(
                                "generated", "reviewed", "pending", "pending_verification"
                            ) -> "pending"
                            else -> "active"
                        })
                        .put("profileId", profile.id)
                        .put("profileName", profile.name)
                        .put("reviewStatus", node.reviewStatus)
                        .put("sourceStatus", node.sourceStatus ?: "")
                        .put("version", node.version ?: "")
                        .put("tags", node.tags)
                        .put("owner", node.owner ?: "")
                        .put("driveFileId", node.driveFileId),
                )
            }

            val relations = JSONArray()
            edges.forEach { edge ->
                if (!edge.targetResolved || edge.fromNode !in known || edge.toNode !in known) {
                    return@forEach
                }
                relations.put(
                    JSONObject()
                        .put("id", "fox-edge:${profile.id}:${edge.edgeId}")
                        .put("from", graphNodeId(profile.id, edge.fromNode))
                        .put("to", graphNodeId(profile.id, edge.toNode))
                        .put("type", edge.relation.ifBlank { "related_to" })
                        .put("status", "active")
                        .put("gate", JSONObject().put("enabled", true))
                        .put("commandChain", JSONArray()),
                )
            }

            JSONObject()
                .put("format", "fox-graph-profile")
                .put("version", 1)
                .put("profileId", profile.id)
                .put("profileName", profile.name)
                .put("sourceLabel", profile.sourceLabel ?: "")
                .put("treeUriConfigured", !profile.treeUri.isNullOrBlank())
                .put("nodeCount", nodeJson.length())
                .put("relationCount", relations.length())
                .put("nodes", nodeJson)
                .put("relations", relations)
                .toString()
        }
    }

    private fun graphNodeId(profileId: String, nodeId: String) =
        "fox:$profileId:$nodeId"

    private fun empty(profileId: String, name: String) = JSONObject()
        .put("format", "fox-graph-profile")
        .put("version", 1)
        .put("profileId", profileId)
        .put("profileName", name)
        .put("sourceLabel", "")
        .put("treeUriConfigured", false)
        .put("nodeCount", 0)
        .put("relationCount", 0)
        .put("nodes", JSONArray())
        .put("relations", JSONArray())
}
