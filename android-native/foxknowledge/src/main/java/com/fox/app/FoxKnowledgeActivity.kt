package com.fox.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.fox.app.data.sync.SyncWorker
import com.fox.app.ui.FoxNavHost
import com.fox.app.ui.theme.FoxTheme

/**
 * Entry point for the FOX knowledge feature, launched by an explicit Intent from
 * ControlCenterActivity — not the process's LAUNCHER activity (that stays
 * ControlCenterActivity in :app).
 */
class FoxKnowledgeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only start the periodic Drive-sync job once the user has opened this feature at
        // least once, so Amin Pocket GBA's own startup path stays unaffected when FOX is unused.
        SyncWorker.schedulePeriodic(applicationContext)
        enableEdgeToEdge()
        setContent {
            FoxTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FoxNavHost()
                }
            }
        }
    }
}
