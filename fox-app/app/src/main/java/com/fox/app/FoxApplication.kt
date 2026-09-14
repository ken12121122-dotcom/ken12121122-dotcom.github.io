package com.fox.app

import android.app.Application
import com.fox.app.data.db.FoxDatabase
import com.fox.app.data.drive.GoogleDriveAdapter
import com.fox.app.data.drive.GoogleSignInDriveAuthTokenProvider
import com.fox.app.data.sync.SyncRepository
import com.fox.app.data.sync.SyncWorker

/** Manual dependency wiring for P0 — no DI framework yet, kept intentionally simple. */
class FoxApplication : Application() {

    val database: FoxDatabase by lazy { FoxDatabase.getInstance(this) }

    private val driveAdapter by lazy {
        GoogleDriveAdapter(GoogleSignInDriveAuthTokenProvider(this))
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

    override fun onCreate() {
        super.onCreate()
        SyncWorker.schedulePeriodic(this)
    }
}
