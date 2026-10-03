package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Intent;
import android.net.Uri;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
@LargeTest
public final class Gen2VoiceActivityTest {
    private static Intent link(String uri) {
        return new Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                .setClass(ApplicationProvider.getApplicationContext(), Gen2VoiceActivity.class);
    }

    @Test
    public void deepLinkSelectsTheRun() {
        assertEquals(7, Gen2VoiceActivity.issueFrom(link("amin-gen2://run/7")));
        assertEquals(0, Gen2VoiceActivity.issueFrom(link("amin-gen2://run/x")));
        try (ActivityScenario<Gen2VoiceActivity> scenario = ActivityScenario.launch(link("amin-gen2://run/7"))) {
            scenario.onActivity(activity -> {
                View root = activity.findViewById(android.R.id.content);
                assertNotNull(Gen2RunsActivityTest.find(root, "gen2-voice-orb"));
                assertNotNull(Gen2RunsActivityTest.find(root, "gen2-voice-open-page"));
            });
        }
    }

    @Test
    public void aSpokenDecisionOnlyProposesUntilConfirmed() throws Exception {
        List<Gen2RunView> runs = Gen2RunView.fromIssues(new JSONArray()
                .put(Gen2RunsActivityTest.issue(7, Gen2RunsActivityTest.gateView())));
        try (ActivityScenario<Gen2VoiceActivity> scenario = ActivityScenario.launch(link("amin-gen2://run/7"))) {
            scenario.onActivity(activity -> {
                activity.useRuns(runs);
                View confirm = Gen2RunsActivityTest.find(activity.findViewById(android.R.id.content), "gen2-voice-confirm");
                assertNotNull(confirm);
                activity.hear("核准");
                assertEquals("/gen2 decide G1 approve", activity.proposedCommand());
                assertEquals(View.VISIBLE, ((View) confirm.getParent().getParent()).getVisibility());
                activity.hear("等一下，不要");
                assertNull(activity.proposedCommand());
                assertEquals(View.GONE, ((View) confirm.getParent().getParent()).getVisibility());
            });
        }
    }
}
