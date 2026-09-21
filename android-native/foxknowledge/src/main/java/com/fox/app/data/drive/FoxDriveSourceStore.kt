package com.fox.app.data.drive

import android.content.Context
import android.net.Uri
import com.fox.app.data.profile.KnowledgeProfileStore

/**
 * Profile-scoped Android SAF source.
 *
 * Each knowledge profile remembers one persisted read-only tree URI. The persisted
 * Android permission remains owned by the app; this store only records which URI
 * belongs to which FOX save slot.
 */
class FoxDriveSourceStore(
    context: Context,
    private val profileId: String,
) {
    private val profileStore = KnowledgeProfileStore(context.applicationContext)

    fun getTreeUri(): Uri? =
        profileStore.profile(profileId)?.treeUri?.let(Uri::parse)

    fun setTreeUri(uri: Uri, sourceLabel: String? = null) {
        profileStore.updateSource(profileId, uri, sourceLabel)
    }

    fun clearTreeUri() {
        profileStore.clearSource(profileId)
    }

    fun hasTreeUri(): Boolean = getTreeUri() != null
}
