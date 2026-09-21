package com.fox.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.fox.app.data.profile.KnowledgeProfileStore
import java.util.concurrent.ConcurrentHashMap

@Database(
    entities = [
        NodeEntity::class,
        EdgeEntity::class,
        ContentEntity::class,
        SyncStateEntity::class,
        AppUsageLogEntity::class,
        NodeSearchFtsEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class FoxDatabase : RoomDatabase() {
    abstract fun nodeDao(): NodeDao
    abstract fun edgeDao(): EdgeDao
    abstract fun contentDao(): ContentDao
    abstract fun nodeSearchFtsDao(): NodeSearchFtsDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun appUsageLogDao(): AppUsageLogDao

    companion object {
        private val instances = ConcurrentHashMap<String, FoxDatabase>()

        fun getInstance(
            context: Context,
            profileId: String = KnowledgeProfileStore.DEFAULT_PROFILE_ID,
        ): FoxDatabase {
            val databaseName = databaseNameFor(profileId)
            return instances[databaseName] ?: synchronized(this) {
                instances[databaseName] ?: Room.databaseBuilder(
                    context.applicationContext,
                    FoxDatabase::class.java,
                    databaseName,
                ).build().also { instances[databaseName] = it }
            }
        }

        private fun databaseNameFor(profileId: String): String {
            // Preserve the original P0 database for the migrated FOX_MAIN slot so
            // the user's existing local projection is retained after upgrading.
            if (profileId == KnowledgeProfileStore.DEFAULT_PROFILE_ID) {
                return "fox-knowledge.db"
            }
            val safeId = profileId.replace(Regex("[^A-Za-z0-9._-]"), "_")
            return "fox-knowledge-$safeId.db"
        }
    }
}
