package com.amin.pocketgba;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONObject;

public final class AminAutomationActivity extends Activity {
    private TextView resultView;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        executeIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        executeIntent(intent);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(40), dp(24), dp(32));
        root.setBackgroundColor(0xfff4f7f5);

        TextView title = new TextView(this);
        title.setText("Amin 自動化執行");
        title.setTextSize(26f);
        title.setTextColor(0xff16231b);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title);

        progress = new ProgressBar(this);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(52), dp(52));
        progressParams.topMargin = dp(24);
        root.addView(progress, progressParams);

        resultView = new TextView(this);
        resultView.setText("正在執行…");
        resultView.setTextSize(16f);
        resultView.setTextColor(0xff68766e);
        resultView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams resultParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        resultParams.topMargin = dp(18);
        root.addView(resultView, resultParams);

        Button close = new Button(this);
        close.setText("關閉");
        close.setAllCaps(false);
        close.setOnClickListener(view -> finish());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        closeParams.topMargin = dp(24);
        root.addView(close, closeParams);
        setContentView(root);
    }

    private void executeIntent(Intent intent) {
        progress.setVisibility(android.view.View.VISIBLE);
        resultView.setText("正在執行…");
        AminControlApiConfig config = new AminControlApiConfig(this);
        if (!config.isAutomationEnabled()) {
            showFailure("外部自動化尚未開啟。請先到 Amin Control API 設定頁開啟。", null);
            return;
        }
        try {
            Input input = parseInput(intent);
            AminInputGateway gateway = AminInputGateway.get(this);
            AminAction action = gateway.createAction(
                    input.action,
                    input.parameters,
                    input.source,
                    1d,
                    input.requestId
            );
            gateway.execute(action, result -> runOnUiThread(() -> showResult(result)));
        } catch (Exception error) {
            showFailure(error.getMessage(), null);
        }
    }

    private Input parseInput(Intent intent) throws Exception {
        Uri data = intent == null ? null : intent.getData();
        if (data != null && "amin-control".equalsIgnoreCase(data.getScheme())) {
            String action = data.getQueryParameter("action");
            String parametersRaw = data.getQueryParameter("parameters");
            JSONObject parameters = parametersRaw == null || parametersRaw.isBlank()
                    ? new JSONObject()
                    : new JSONObject(parametersRaw);
            return new Input(
                    action,
                    parameters,
                    "deep_link",
                    data.getQueryParameter("requestId")
            );
        }
        String parametersRaw = intent == null
                ? ""
                : intent.getStringExtra(AminAutomationReceiver.EXTRA_PARAMETERS);
        JSONObject parameters = parametersRaw == null || parametersRaw.isBlank()
                ? new JSONObject()
                : new JSONObject(parametersRaw);
        return new Input(
                intent == null ? "" : intent.getStringExtra(AminAutomationReceiver.EXTRA_ACTION),
                parameters,
                "explicit_intent",
                intent == null ? "" : intent.getStringExtra(AminAutomationReceiver.EXTRA_REQUEST_ID)
        );
    }

    private void showResult(ExecutionResult result) {
        progress.setVisibility(android.view.View.GONE);
        resultView.setText(result.getMessage() + "\n\n" + result.toJson());
        resultView.setTextColor(result.isSuccess() ? 0xff19794b : 0xffb3261e);
        Intent output = new Intent();
        output.putExtra(AminAutomationReceiver.EXTRA_RESULT_JSON, result.toJson());
        setResult(result.isSuccess() ? RESULT_OK : RESULT_CANCELED, output);
    }

    private void showFailure(String message, String json) {
        progress.setVisibility(android.view.View.GONE);
        resultView.setText((message == null ? "執行失敗" : message) + (json == null ? "" : "\n" + json));
        resultView.setTextColor(0xffb3261e);
        setResult(RESULT_CANCELED);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class Input {
        final String action;
        final JSONObject parameters;
        final String source;
        final String requestId;

        Input(String action, JSONObject parameters, String source, String requestId) {
            this.action = action;
            this.parameters = parameters;
            this.source = source;
            this.requestId = requestId;
        }
    }
}
