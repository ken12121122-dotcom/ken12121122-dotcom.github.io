package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
@LargeTest
public final class Gen2ChatActivityTest {
    private static final String WF = "---\ndocument_type: workflow\nworkflow_id: WF-DEMO-001\n---\n\n# WF-DEMO-001｜示範\n\n"
            + "```gen2-run\n{\"version\":1,\"steps\":[{\"id\":\"S1\",\"type\":\"input\",\"title\":\"收資料\",\"prompt\":\"貼上工作紀錄\"}]}\n```\n";

    private static Intent chat(int issue) {
        return Gen2ChatActivity.intent(ApplicationProvider.getApplicationContext(), issue);
    }

    private static void waitLoaded(ActivityScenario<Gen2ChatActivity> scenario) throws Exception {
        for (int i = 0; i < 100; i++) {
            AtomicBoolean done = new AtomicBoolean();
            scenario.onActivity(a -> done.set(a.isLoaded()));
            if (done.get()) return;
            Thread.sleep(100);
        }
        throw new AssertionError("chat did not finish loading");
    }

    private static View card(Gen2ChatActivity activity) {
        return Gen2RunsActivityTest.find(activity.findViewById(android.R.id.content), "gen2-chat-card");
    }

    @Test
    public void opensWithTypingVoiceAndNoCard() throws Exception {
        try (ActivityScenario<Gen2ChatActivity> scenario = ActivityScenario.launch(chat(0))) {
            waitLoaded(scenario);
            scenario.onActivity(activity -> {
                View root = activity.findViewById(android.R.id.content);
                assertNotNull(Gen2RunsActivityTest.find(root, "gen2-chat-input"));
                assertNotNull(Gen2RunsActivityTest.find(root, "gen2-chat-send"));
                assertNotNull(Gen2RunsActivityTest.find(root, "gen2-chat-mic"));
                assertEquals(View.GONE, card(activity).getVisibility());
            });
        }
    }

    @Test
    public void aProposedTaskWaitsForALaterAgreement() throws Exception {
        List<Gen2WorkflowCatalog.Workflow> catalog = Collections.singletonList(Gen2WorkflowCatalog.parse(WF));
        try (ActivityScenario<Gen2ChatActivity> scenario = ActivityScenario.launch(chat(0))) {
            waitLoaded(scenario);
            scenario.onActivity(activity -> {
                activity.useState(Collections.emptyList(), catalog);
                try {
                    JSONObject reply = new JSONObject().put("say", "我理解是把今天的巡檢寫進行事曆，要開始嗎？")
                            .put("confirm", "yes") // agreement cannot come in the same turn as the proposal
                            .put("action", new JSONObject().put("type", "start").put("workflow", "WF-DEMO-001")
                                    .put("scope", "今天的巡檢寫進行事曆").put("material", "9:00-10:00 巡檢"));
                    activity.modelReply(reply.toString());
                } catch (Exception error) { throw new AssertionError(error); }
                assertEquals("start:WF-DEMO-001", activity.waitingSummary());
                assertEquals(View.VISIBLE, card(activity).getVisibility());
                activity.modelReply("{\"say\":\"好，那你想怎麼改？\",\"confirm\":\"no\",\"action\":null}");
                assertNull(activity.waitingSummary());
                assertEquals(View.GONE, card(activity).getVisibility());
            });
        }
    }

    @Test
    public void unsureRepliesKeepTheCardAndInvalidActionsAreExplained() throws Exception {
        List<Gen2RunView> runs = Gen2RunView.fromIssues(new JSONArray()
                .put(Gen2RunsActivityTest.issue(7, Gen2RunsActivityTest.gateView())));
        try (ActivityScenario<Gen2ChatActivity> scenario = ActivityScenario.launch(chat(7))) {
            waitLoaded(scenario);
            scenario.onActivity(activity -> {
                activity.useState(runs, Collections.emptyList());
                activity.modelReply("{\"say\":\"你要核准 #7 嗎？\",\"confirm\":null,\"action\":{\"type\":\"decide\",\"issue\":7,\"option\":\"approve\"}}");
                assertEquals("decide:/gen2 decide G1 approve", activity.waitingSummary());
                activity.modelReply("{\"say\":\"你是說要先看內容嗎？\",\"confirm\":null,\"action\":null}");
                assertEquals("decide:/gen2 decide G1 approve", activity.waitingSummary());
                activity.modelReply("{\"say\":\"退回\",\"confirm\":\"no\",\"action\":{\"type\":\"decide\",\"issue\":7,\"option\":\"revise\"}}");
                assertNull(activity.waitingSummary()); // revise needs the OWNER's reason, so nothing is proposed
                assertEquals(View.GONE, card(activity).getVisibility());
            });
        }
    }
}
