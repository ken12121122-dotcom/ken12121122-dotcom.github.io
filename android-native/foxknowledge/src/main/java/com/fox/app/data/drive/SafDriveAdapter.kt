package com.fox.app.data.drive

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Read-only Drive/DocumentProvider adapter backed by Android SAF.
 *
 * The user explicitly chooses one folder. FOX recursively reads Markdown files
 * under that tree and never requests write permission or mutates provider data.
 */
class SafDriveAdapter(
    context: Context,
    private val sourceStore: FoxDriveSourceStore,
) : DriveAdapter {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    override suspend fun listMarkdownFiles(folderId: String?): List<DriveFile> =
        withContext(Dispatchers.IO) {
            val treeUri = sourceStore.getTreeUri()
                ?: throw DriveAuthException("尚未選擇 FOX 知識庫資料夾")

            val root = DocumentFile.fromTreeUri(appContext, treeUri)
                ?: throw DriveAuthException("無法開啟已選擇的知識庫資料夾，請重新選擇")

            if (!root.exists() || !root.isDirectory) {
                throw DriveAuthException("已選擇的知識庫資料夾已失效，請重新選擇")
            }

            val out = mutableListOf<DriveFile>()
            walk(root, out)
            out
        }

    override suspend fun downloadFileContent(fileId: String): String =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(fileId)
            val input = resolver.openInputStream(uri)
                ?: throw DriveApiException("無法讀取知識檔案：$fileId")
            input.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            }
        }

    private fun walk(node: DocumentFile, out: MutableList<DriveFile>) {
        for (child in node.listFiles()) {
            when {
                child.isDirectory -> walk(child, out)
                child.isFile && child.name?.endsWith(".md", ignoreCase = true) == true -> {
                    val uri = child.uri
                    out += DriveFile(
                        id = uri.toString(),
                        name = child.name ?: uri.lastPathSegment ?: "unknown.md",
                        modifiedTime = child.lastModified().toString(),
                        md5Checksum = null,
                    )
                }
            }
        }
    }
}
