package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@LargeTest
public final class Gen2RunsActivityTest {
    @Rule
    public final ActivityScenarioRule<Gen2RunsActivity> activityRule =
            new ActivityScenarioRule<>(Gen2RunsActivity.class);

    @Test
    public void withoutGitHubLoginShowsTheLinkPrompt() {
        activityRule.getScenario().onActivity(activity -> {
            View root = activity.findViewById(android.R.id.content);
            assertNotNull(findText(root, "尚未連結 GitHub"));
            TextView status = (TextView) find(root, "gen2-status");
            assertTrue(status.getText().toString().contains("尚未連結"));
        });
    }

    @Test
    public void rendersAGateWithOptionButtonsAndRequiresAReasonWhereNeeded() throws Exception {
        List<Gen2RunView> runs = Gen2RunView.fromIssues(new JSONArray().put(issue(7, gateView())));
        activityRule.getScenario().onActivity(activity -> {
            activity.showRuns(runs);
            View root = activity.findViewById(android.R.id.content);
            assertNotNull(find(root, "gen2-run-7"));
            assertNotNull(findText(root, "| 10/3 | 09:00 | 巡檢 |"));
            assertNotNull(find(root, "gen2-option-7-approve"));
            assertNotNull(find(root, "gen2-option-7-stop"));
            View revise = find(root, "gen2-option-7-revise");
            assertNotNull(revise);
            assertTrue(revise.performClick());
            TextView status = (TextView) find(root, "gen2-status");
            assertTrue(status.getText().toString(), status.getText().toString().contains("必須寫說明"));
            assertTrue(revise.isEnabled());
        });
    }

    @Test
    public void rendersAnInputStepAndRefusesEmptyMaterial() throws Exception {
        JSONObject view = gateView().put("status", "waiting_input").put("current", "S1");
        view.put("pending", new JSONObject().put("step", "S1").put("type", "input")
                .put("title", "接收工作紀錄來源").put("prompt", "貼上工作紀錄"));
        List<Gen2RunView> runs = Gen2RunView.fromIssues(new JSONArray().put(issue(8, view)));
        activityRule.getScenario().onActivity(activity -> {
            activity.showRuns(runs);
            View root = activity.findViewById(android.R.id.content);
            EditText material = (EditText) find(root, "gen2-input-8");
            assertNotNull(material);
            assertEquals("", material.getText().toString());
            assertTrue(find(root, "gen2-send-8").performClick());
            TextView status = (TextView) find(root, "gen2-status");
            assertTrue(status.getText().toString().contains("請先填寫"));
            assertNull(find(root, "gen2-option-8-approve"));
        });
    }

    private static JSONObject gateView() throws Exception {
        return new JSONObject()
                .put("format", "gen2-run-view").put("version", 1)
                .put("runId", "WF-TIME-001-test")
                .put("workflow", new JSONObject().put("id", "WF-TIME-001").put("title", "工作紀錄寫入 Google Calendar"))
                .put("status", "waiting_owner").put("current", "G1").put("seq", 2)
                .put("pending", new JSONObject().put("step", "G1").put("type", "gate")
                        .put("title", "OWNER 審核候選工作紀錄").put("attempt", 1)
                        .put("options", new JSONArray()
                                .put(new JSONObject().put("id", "approve").put("label", "核准").put("comment", JSONObject.NULL))
                                .put(new JSONObject().put("id", "revise").put("label", "退回重新擷取").put("comment", "required"))
                                .put(new JSONObject().put("id", "stop").put("label", "這次不寫入").put("comment", "optional")))
                        .put("review", new JSONObject().put("step", "S2").put("title", "產出候選")
                                .put("output", new JSONObject().put("text", "| 10/3 | 09:00 | 巡檢 |"))))
                .put("steps", new JSONArray());
    }

    private static JSONObject issue(int number, JSONObject view) throws Exception {
        String body = "<!-- gen2-run-view:"
                + Base64.getEncoder().encodeToString(view.toString().getBytes(StandardCharsets.UTF_8)) + " -->";
        return new JSONObject().put("number", number).put("title", "▶ WF-TIME-001")
                .put("html_url", "https://github.com/ken12121122-dotcom/gen2-knowledge/issues/" + number)
                .put("user", new JSONObject().put("login", Gen2RunView.ENGINE_LOGIN))
                .put("labels", new JSONArray().put(new JSONObject().put("name", "gen2-run")))
                .put("body", body);
    }

    private static View find(View view, String description) {
        CharSequence value = view.getContentDescription();
        if (value != null && description.contentEquals(value)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                View match = find(group.getChildAt(index), description);
                if (match != null) return match;
            }
        }
        return null;
    }

    private static View findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                View match = findText(group.getChildAt(index), text);
                if (match != null) return match;
            }
        }
        return null;
    }
}
