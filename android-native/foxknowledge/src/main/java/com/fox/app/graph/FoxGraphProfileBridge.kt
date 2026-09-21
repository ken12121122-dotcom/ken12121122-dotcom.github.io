package com.fox.app.graph

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.fox.app.FoxDependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Java-friendly bridge used by AMIN WIKI when a FOX graph profile becomes active.
 * Sync remains read-only and profile-scoped.
 */
object FoxGraphProfileBridge {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @JvmStatic
    fun syncAsync(context: Context, profileId: String, finished: Runnable?) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                val deps = FoxDependencies.get(appContext)
                val source = deps.driveSourceStoreFor(profileId)
                if (source.hasTreeUri()) {
                    deps.syncRepositoryFor(profileId).syncOnce()
                }
            } catch (_: Exception) {
                // The graph can still show the last valid local projection.
            } finally {
                if (finished != null) {
                    Handler(Looper.getMainLooper()).post(finished)
                }
            }
        }
    }
}
