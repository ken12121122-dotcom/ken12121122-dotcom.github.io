package com.fox.app.data.drive

/** One `.md` file's Drive-side metadata, enough to drive sync_state diffing without downloading content. */
data class DriveFile(
    val id: String,
    val name: String,
    val modifiedTime: String,
    val md5Checksum: String?,
)

class DriveAuthException(message: String) : Exception(message)
class DriveApiException(message: String) : Exception(message)

/**
 * Read-only access to the Drive knowledge base. KB-APP-001 fixes this as
 * read-only for P0 ("P0：Drive → App（唯讀）") — writeback is a later phase
 * and stays gated behind KB_COORDINATOR/OWNER review regardless of what
 * this interface can technically do.
 */
interface DriveAdapter {
    suspend fun listMarkdownFiles(folderId: String? = null): List<DriveFile>
    suspend fun downloadFileContent(fileId: String): String
}
