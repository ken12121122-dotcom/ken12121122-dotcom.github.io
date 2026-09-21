package com.fox.app

import android.content.Context
import com.fox.app.data.db.FoxDatabase
import com.fox.app.data.drive.AdaptiveDriveAdapter
import com.fox.app.data.drive.FoxDriveSourceStore
import com.fox.app.data.drive.GoogleDriveAdapter
import com.fox.app.data.drive.GoogleSignInDriveAuthTokenProvider
import com.fox.app.data.drive.SafDriveAdapter
import com.fox.app.data.profile.KnowledgeProfileStore
import com.fox.app.data.sync.SyncRepository
import com.fox.app.data.sync.SyncWorker
import java.util.concurrent.ConcurrentHashMap

class FoxDependencies private constructor(context: Context) {
    private val appContext = context.applicationContext

    val profileStore: KnowledgeProfileStore by lazy { KnowledgeProfileStore(appContext) }

    private val sourceStores = ConcurrentHashMap<String, FoxDriveSourceStore>()
    private val repositories = ConcurrentHashMap<String, SyncRepository>()

    init {
        SyncWorker.schedulePeriodic(appContext)
    }

    fun databaseFor(profileId: String): FoxDatabase =
        FoxDatabase.getInstance(appContext, profileId)

    fun driveSourceStoreFor(profileId: String): FoxDriveSourceStore =
        sourceStores[profileId] ?: synchronized(sourceStores) {
            sourceStores[profileId] ?: FoxDriveSourceStore(appContext, profileId)
                .also { sourceStores[profileId] = it }
        }

    fun syncRepositoryFor(profileId: String): SyncRepository =
        repositories[profileId] ?: synchronized(repositories) {
            repositories[profileId] ?: run {
                val database = databaseFor(profileId)
                val sourceStore = driveSourceStoreFor(profileId)
                val driveAdapter = AdaptiveDriveAdapter(
                    sourceStore = sourceStore,
                    safAdapter = SafDriveAdapter(appContext, sourceStore),
                    googleAdapter = GoogleDriveAdapter(GoogleSignInDriveAuthTokenProvider(appContext)),
                )
                SyncRepository(
                    driveAdapter = driveAdapter,
                    nodeDao = database.nodeDao(),
                    edgeDao = database.edgeDao(),
                    contentDao = database.contentDao(),
                    ftsDao = database.nodeSearchFtsDao(),
                    syncStateDao = database.syncStateDao(),
                )
            }.also { repositories[profileId] = it }
        }

    val database: FoxDatabase
        get() = databaseFor(profileStore.activeProfileId())

    val driveSourceStore: FoxDriveSourceStore
        get() = driveSourceStoreFor(profileStore.activeProfileId())

    val syncRepository: SyncRepository
        get() = syncRepositoryFor(profileStore.activeProfileId())

    companion object {
        @Volatile private var instance: FoxDependencies? = null

        fun get(context: Context): FoxDependencies =
            instance ?: synchronized(this) {
                instance ?: FoxDependencies(context).also { instance = it }
            }
    }
}
