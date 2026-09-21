package com.fox.app.data.sync

import com.fox.app.data.db.ContentEntity
import com.fox.app.data.db.ContentDao
import com.fox.app.data.db.EdgeDao
import com.fox.app.data.db.EdgeEntity
import com.fox.app.data.db.NodeDao
import com.fox.app.data.db.NodeEntity
import com.fox.app.data.db.NodeSearchFtsDao
import com.fox.app.data.db.NodeSearchFtsEntity
import com.fox.app.data.db.SyncStateDao
import com.fox.app.data.db.SyncStateEntity
import com.fox.app.data.db.SyncStatus
import com.fox.app.data.drive.DriveAdapter
import com.fox.app.data.parser.KbParser
import com.fox.app.data.validator.KbValidator
import java.security.MessageDigest

data class SyncSummary(
    val scanned: Int,
    val updated: Int,
    val unchanged: Int,
    val failed: Int,
    val failures: List<Pair<String, String>>, // fileName to error summary
)

/**
 * P0 sync pipeline: Drive -> KbParser -> KbValidator -> Room upsert, diffed
 * against sync_state so an unchanged file is skipped rather than re-parsed
 * every run. Read-only against Drive (KB-APP-001's P0 scope) — nothing here
 * ever writes back to a Drive file.
 *
 * A file that fails validation is recorded as an ERROR sync_state row with the
 * reason, and its previously-synced node (if any) is left untouched rather than
 * being overwritten by a bad parse.
 */
class SyncRepository(
    private val driveAdapter: DriveAdapter,
    private val nodeDao: NodeDao,
    private val edgeDao: EdgeDao,
    private val contentDao: ContentDao,
    private val ftsDao: NodeSearchFtsDao,
    private val syncStateDao: SyncStateDao,
    private val vaultFolderId: String? = null,
) {
    suspend fun syncOnce(): SyncSummary {
        val remoteFiles = driveAdapter.listMarkdownFiles(vaultFolderId)
        var updated = 0
        var unchanged = 0
        val failures = mutableListOf<Pair<String, String>>()

        for (file in remoteFiles) {
            val existingState = syncStateDao.get(file.id)
            if (existingState?.remoteModifiedTime == file.modifiedTime && existingState.syncStatus == SyncStatus.SYNCED) {
                unchanged++
                continue
            }

            try {
                val raw = driveAdapter.downloadFileContent(file.id)
                val contentHash = sha256(raw)
                val parsed = KbParser.parse(raw)
                val validation = KbValidator.validate(parsed)

                if (!validation.isValid || parsed.node == null) {
                    val reason = validation.errors.joinToString("; ")
                    syncStateDao.upsert(
                        SyncStateEntity(
                            driveFileId = file.id,
                            nodeId = existingState?.nodeId,
                            remoteModifiedTime = file.modifiedTime,
                            localModifiedTime = System.currentTimeMillis(),
                            remoteHash = file.md5Checksum,
                            localHash = contentHash,
                            syncStatus = SyncStatus.ERROR,
                            lastError = reason,
                        ),
                    )
                    failures += file.name to reason
                    continue
                }

                val node = parsed.node
                nodeDao.upsert(
                    NodeEntity(
                        nodeId = node.nodeId,
                        title = node.title,
                        documentType = node.documentType,
                        status = node.status,
                        reviewStatus = node.reviewStatus,
                        version = node.version,
                        tags = node.tags.joinToString(","),
                        owner = node.owner,
                        confidence = node.confidence,
                        sourceStatus = node.sourceStatus,
                        driveFileId = file.id,
                        remoteModifiedTime = file.modifiedTime,
                        contentHash = contentHash,
                    ),
                )

                edgeDao.deleteOutgoing(node.nodeId)
                edgeDao.insertAll(
                    parsed.edges.map { edge ->
                        EdgeEntity(fromNode = node.nodeId, relation = edge.relation, toNode = edge.target, targetResolved = false)
                    },
                )

                contentDao.deleteForNode(node.nodeId)
                contentDao.insertAll(
                    parsed.contents.map { section ->
                        ContentEntity(nodeId = node.nodeId, section = section.section, content = section.content, sortOrder = section.order)
                    },
                )

                ftsDao.deleteForNode(node.nodeId)
                ftsDao.insert(
                    NodeSearchFtsEntity(
                        nodeId = node.nodeId,
                        title = node.title,
                        tags = node.tags.joinToString(" "),
                        content = parsed.contents.joinToString(" ") { it.content },
                    ),
                )

                syncStateDao.upsert(
                    SyncStateEntity(
                        driveFileId = file.id,
                        nodeId = node.nodeId,
                        remoteModifiedTime = file.modifiedTime,
                        localModifiedTime = System.currentTimeMillis(),
                        remoteHash = file.md5Checksum,
                        localHash = contentHash,
                        syncStatus = SyncStatus.SYNCED,
                        lastError = null,
                    ),
                )
                updated++
            } catch (e: Exception) {
                val reason = e.message ?: e.javaClass.simpleName
                syncStateDao.upsert(
                    SyncStateEntity(
                        driveFileId = file.id,
                        nodeId = existingState?.nodeId,
                        remoteModifiedTime = file.modifiedTime,
                        localModifiedTime = System.currentTimeMillis(),
                        remoteHash = file.md5Checksum,
                        localHash = existingState?.localHash,
                        syncStatus = SyncStatus.ERROR,
                        lastError = reason,
                    ),
                )
                failures += file.name to reason
            }
        }

        edgeDao.recomputeTargetResolution()

        return SyncSummary(
            scanned = remoteFiles.size,
            updated = updated,
            unchanged = unchanged,
            failed = failures.size,
            failures = failures,
        )
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
