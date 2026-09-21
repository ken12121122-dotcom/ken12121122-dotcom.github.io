package com.fox.app.data.profile

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class KnowledgeProfile(
    val id: String,
    val name: String,
    val treeUri: String?,
    val sourceLabel: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Small local manifest for FOX knowledge "save slots".
 *
 * Each profile remembers its own persisted SAF tree URI. The canonical knowledge
 * remains in the selected source folder; this store only remembers how to reopen it.
 */
class KnowledgeProfileStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val legacyPrefs = appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    init {
        migrateLegacySourceIfNeeded()
        ensureAtLeastOneProfile()
    }

    fun listProfiles(): List<KnowledgeProfile> = synchronized(lock) { readProfilesLocked() }

    fun activeProfileId(): String = synchronized(lock) {
        val profiles = readProfilesLocked()
        val requested = prefs.getString(KEY_ACTIVE_ID, null)
        val active = profiles.firstOrNull { it.id == requested } ?: profiles.first()
        if (requested != active.id) prefs.edit().putString(KEY_ACTIVE_ID, active.id).apply()
        active.id
    }

    fun activeProfile(): KnowledgeProfile = profile(activeProfileId())
        ?: error("FOX knowledge profile missing")

    fun profile(id: String): KnowledgeProfile? = synchronized(lock) {
        readProfilesLocked().firstOrNull { it.id == id }
    }

    fun createProfile(name: String? = null): KnowledgeProfile = synchronized(lock) {
        val profiles = readProfilesLocked().toMutableList()
        val now = System.currentTimeMillis()
        val id = "kb-" + UUID.randomUUID().toString().substring(0, 8)
        val displayName = name?.trim().takeUnless { it.isNullOrEmpty() }
            ?: "知識庫 ${profiles.size + 1}"
        val profile = KnowledgeProfile(
            id = id,
            name = displayName,
            treeUri = null,
            sourceLabel = null,
            createdAt = now,
            updatedAt = now,
        )
        profiles += profile
        writeProfilesLocked(profiles)
        prefs.edit().putString(KEY_ACTIVE_ID, id).apply()
        profile
    }

    fun setActive(id: String): Boolean = synchronized(lock) {
        if (readProfilesLocked().none { it.id == id }) return false
        prefs.edit().putString(KEY_ACTIVE_ID, id).apply()
        true
    }

    fun updateSource(id: String, uri: Uri, sourceLabel: String?) = synchronized(lock) {
        val now = System.currentTimeMillis()
        val profiles = readProfilesLocked().map { profile ->
            if (profile.id == id) profile.copy(
                treeUri = uri.toString(),
                sourceLabel = sourceLabel?.trim().takeUnless { it.isNullOrEmpty() },
                updatedAt = now,
            ) else profile
        }
        writeProfilesLocked(profiles)
    }

    fun clearSource(id: String) = synchronized(lock) {
        val now = System.currentTimeMillis()
        val profiles = readProfilesLocked().map { profile ->
            if (profile.id == id) profile.copy(
                treeUri = null,
                sourceLabel = null,
                updatedAt = now,
            ) else profile
        }
        writeProfilesLocked(profiles)
    }

    private fun migrateLegacySourceIfNeeded() = synchronized(lock) {
        if (readProfilesLocked().isNotEmpty()) return
        val legacyUri = legacyPrefs.getString(LEGACY_TREE_URI_KEY, null)
        if (legacyUri.isNullOrBlank()) return
        val now = System.currentTimeMillis()
        val profile = KnowledgeProfile(
            id = DEFAULT_PROFILE_ID,
            name = "FOX_MAIN",
            treeUri = legacyUri,
            sourceLabel = "既有 FOX 知識庫",
            createdAt = now,
            updatedAt = now,
        )
        writeProfilesLocked(listOf(profile))
        prefs.edit().putString(KEY_ACTIVE_ID, DEFAULT_PROFILE_ID).apply()
    }

    private fun ensureAtLeastOneProfile() = synchronized(lock) {
        if (readProfilesLocked().isNotEmpty()) return
        val now = System.currentTimeMillis()
        val profile = KnowledgeProfile(
            id = DEFAULT_PROFILE_ID,
            name = "FOX_MAIN",
            treeUri = null,
            sourceLabel = null,
            createdAt = now,
            updatedAt = now,
        )
        writeProfilesLocked(listOf(profile))
        prefs.edit().putString(KEY_ACTIVE_ID, DEFAULT_PROFILE_ID).apply()
    }

    private fun readProfilesLocked(): List<KnowledgeProfile> {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("id", "").trim()
                    if (id.isEmpty()) continue
                    add(
                        KnowledgeProfile(
                            id = id,
                            name = item.optString("name", id),
                            treeUri = item.optString("treeUri", "").ifBlank { null },
                            sourceLabel = item.optString("sourceLabel", "").ifBlank { null },
                            createdAt = item.optLong("createdAt", 0L),
                            updatedAt = item.optLong("updatedAt", 0L),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeProfilesLocked(profiles: List<KnowledgeProfile>) {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
                    .put("treeUri", profile.treeUri ?: "")
                    .put("sourceLabel", profile.sourceLabel ?: "")
                    .put("createdAt", profile.createdAt)
                    .put("updatedAt", profile.updatedAt),
            )
        }
        prefs.edit().putString(KEY_PROFILES, array.toString()).apply()
    }

    companion object {
        const val DEFAULT_PROFILE_ID = "fox-main"
        private const val PREFS_NAME = "fox_knowledge_profiles"
        private const val KEY_PROFILES = "profiles_v1"
        private const val KEY_ACTIVE_ID = "active_profile_id"

        private const val LEGACY_PREFS_NAME = "fox_drive_source"
        private const val LEGACY_TREE_URI_KEY = "saf_tree_uri"
    }
}
