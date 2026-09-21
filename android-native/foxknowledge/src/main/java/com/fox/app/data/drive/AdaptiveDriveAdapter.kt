package com.fox.app.data.drive

/**
 * Chooses the user-scoped SAF source when present; otherwise keeps the existing
 * Google Sign-In/Drive REST path for installations whose OAuth client is configured.
 */
// SAF remains read-only; this selector changes source transport only, never governance authority.
class AdaptiveDriveAdapter(
    private val sourceStore: FoxDriveSourceStore,
    private val safAdapter: DriveAdapter,
    private val googleAdapter: DriveAdapter,
) : DriveAdapter {
    private fun active(): DriveAdapter =
        if (sourceStore.hasTreeUri()) safAdapter else googleAdapter

    override suspend fun listMarkdownFiles(folderId: String?): List<DriveFile> =
        active().listMarkdownFiles(folderId)

    override suspend fun downloadFileContent(fileId: String): String =
        active().downloadFileContent(fileId)
}
