package com.fox.app.data.drive

import android.content.Context
import android.net.Uri

/**
 * Stores the user-selected Android Storage Access Framework tree URI.
 *
 * The URI is read-only from FOX's perspective. The persisted grant is issued by
 * Android's system picker and can point to a Google Drive-backed DocumentsProvider
 * without requiring FOX to own a Google OAuth client.
 */
class FoxDriveSourceStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getTreeUri(): Uri? =
        prefs.getString(KEY_TREE_URI, null)?.let(Uri::parse)

    fun setTreeUri(uri: Uri) {
        prefs.edit().putString(KEY_TREE_URI, uri.toString()).apply()
    }

    fun clearTreeUri() {
        prefs.edit().remove(KEY_TREE_URI).apply()
    }

    fun hasTreeUri(): Boolean = getTreeUri() != null

    companion object {
        private const val PREFS_NAME = "fox_drive_source"
        private const val KEY_TREE_URI = "saf_tree_uri"
    }
}
