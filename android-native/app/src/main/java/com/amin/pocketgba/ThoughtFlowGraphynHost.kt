package com.amin.pocketgba

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import com.ronjunevaldoz.graphyn.editor.shell.GraphynEditorShell
import com.ronjunevaldoz.graphyn.editor.shell.GraphynEditorShellDependencies
import com.ronjunevaldoz.graphyn.editor.state.rememberGraphynEditorState
import com.ronjunevaldoz.graphyn.editor.theme.GraphynBranding
import com.ronjunevaldoz.graphyn.pluginapi.DefaultGraphynPluginRegistry

/**
 * Isolated Graphyn proof-of-concept host for Amin Pocket.
 *
 * This deliberately does not touch Voice Orb, Node Registry, LLM routing, or memory.
 * It only proves that Graphyn can be embedded as the future observable Skill/Agent canvas.
 */
object ThoughtFlowGraphynHost {
    @JvmStatic
    fun createView(activity: ComponentActivity): View = ComposeView(activity).apply {
        setContent {
            ThoughtFlowCanvas()
        }
    }
}

@Composable
private fun ThoughtFlowCanvas() {
    val plugins = remember { DefaultGraphynPluginRegistry() }
    val state = rememberGraphynEditorState(nodeSpecs = plugins.nodeSpecs)

    GraphynEditorShell(
        dependencies = GraphynEditorShellDependencies(
            nodeSpecs = plugins.nodeSpecs,
            onHome = null,
        ),
        branding = GraphynBranding(appName = "思流"),
        state = state,
    )
}
