package com.fox.app

import android.content.Context
import com.fox.app.data.db.FoxDatabase
import com.fox.app.data.drive.AdaptiveDriveAdapter
import com.fox.app.data.drive.FoxDriveSourceStore
import com.fox.app.data.drive.GoogleDriveAdapter
import com.fox.app.data.drive.GoogleSignInDriveAuthTokenProvider
import com.fox.app.data.drive.SafDriveAdapter
import com.fox.app.data.sync.SyncRepository

/**
 * Manual dependency wiring for P0 — no DI framework yet, kept intentionally simple.
 *
 * A plain per-process singleton rather than an Application subclass: :foxknowledge is a
 * library module inside the merged Amin Pocket GBA app, which already owns the process's
 * one Application class (AminPocketApplication). A process can only have one Application.
 */
class FoxDependencies private constructor(context: Context) {
    private val appContext = context.applicationContext

    val database: FoxDatabase by lazy { FoxDatabase.getInstance(appContext) }

    val driveSourceStore: FoxDriveSourceStore by lazy { FoxDriveSourceStore(appContext) }

    private val driveAdapter by lazy {
        AdaptiveDriveAdapter(
            sourceStore = driveSourceStore,
            safAdapter = SafDriveAdapter(appContext, driveSourceStore),
            googleAdapter = GoogleDriveAdapter(GoogleSignInDriveAuthTokenProvider(appContext)),
        )
    }

    val syncRepository: SyncRepository by lazy {
        SyncRepository(
            driveAdapter = driveAdapter,
            nodeDao = database.nodeDao(),
            edgeDao = database.edgeDao(),
            contentDao = database.contentDao(),
            ftsDao = database.nodeSearchFtsDao(),
            syncStateDao = database.syncStateDao(),
        )
    }

    companion object {
        @Volatile private var instance: FoxDependencies? = null

        fun get(context: Context): FoxDependencies =
            instance ?: synchronized(this) {
                instance ?: FoxDependencies(context).also { instance = it }
            }
    }
}
