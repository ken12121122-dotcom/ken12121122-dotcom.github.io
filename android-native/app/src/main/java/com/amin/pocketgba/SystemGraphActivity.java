package com.amin.pocketgba;

import android.os.Bundle;

import androidx.activity.ComponentActivity;

/**
 * 「思流」POC entrance.
 *
 * The previous feature-map UI is intentionally not shown in this branch. This activity now hosts
 * Graphyn as an isolated workflow-canvas experiment so we can validate mobile pan/zoom, nodes,
 * execution traces, and later animated signal flow without disturbing production chat behavior.
 */
public final class SystemGraphActivity extends ComponentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xfff7f8fa);
        getWindow().setNavigationBarColor(0xfff7f8fa);
        setContentView(ThoughtFlowGraphynHost.createView(this));
    }
}
