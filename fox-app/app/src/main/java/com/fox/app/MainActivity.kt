package com.fox.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.fox.app.ui.FoxNavHost
import com.fox.app.ui.theme.FoxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
