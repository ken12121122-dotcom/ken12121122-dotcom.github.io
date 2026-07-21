package com.amin.pocketgba;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import org.json.JSONObject;

public final class AminAutomationReceiver extends BroadcastReceiver {
    public static final String ACTION_EXECUTE = "com.amin.pocketgba.ACTION_EXECUTE";
    public static final String ACTION_RESULT = "com.amin.pocketgba.ACTION_RESULT";
    public static final String EXTRA_ACTION = "amin_action";
    public static final String EXTRA_PARAMETERS = "amin_parameters";
    public static final String EXTRA_REQUEST_ID = "amin_request_id";
    public static final String EXTRA_REPLY_PACKAGE = "amin_reply_package";
    public static final String EXTRA_RESULT_JSON = "amin_result_json";

    @Override
    public void onReceive(Context context, Intent intent) {
        PendingResult pending = goAsync();
        Context appContext = context.getApplicationContext();
        new Thread(() -> {
            ExecutionResult result;
            try {
                AminControlApiConfig config = new AminControlApiConfig(appContext);
                if (!config.isAutomationEnabled()) {
                    AminAction rejectedAction = AminInputGateway.get(appContext).createAction(
                            intent == null ? "" : intent.getStringExtra(EXTRA_ACTION),
                            new JSONObject(),
                            "broadcast",
                            1d,
                            intent == null ? "" : intent.getStringExtra(EXTRA_REQUEST_ID)
                    );
                    result = ExecutionResult.failure(
                            rejectedAction,
                            java.time.Instant.now().toString(),
                            "AUTOMATION_DISABLED",
                            "External automation is disabled in Amin Control API settings"
                    );
                    AminInputGateway.get(appContext).getEventStore().recordExecution(result);
                } else {
                    result = execute(appContext, intent);
                }
                Bundle extras = new Bundle();
                extras.putString(EXTRA_RESULT_JSON, result.toJson());
                pending.setResultCode(result.isSuccess() ? 0 : 1);
                pending.setResultData(result.getMessage());
                pending.setResultExtras(extras);
                sendResultBroadcast(appContext, intent, result);
            } catch (Exception error) {
                pending.setResultCode(2);
                pending.setResultData(error.getMessage());
            } finally {
                pending.finish();
            }
        }, "amin-automation-broadcast").start();
    }

    private ExecutionResult execute(Context context, Intent intent) throws Exception {
        String actionName = intent == null ? "" : intent.getStringExtra(EXTRA_ACTION);
        String parametersRaw = intent == null ? "" : intent.getStringExtra(EXTRA_PARAMETERS);
        JSONObject parameters = parametersRaw == null || parametersRaw.isBlank()
                ? new JSONObject()
                : new JSONObject(parametersRaw);
        AminInputGateway gateway = AminInputGateway.get(context);
        AminAction action = gateway.createAction(
                actionName,
                parameters,
                "broadcast",
                1d,
                intent == null ? "" : intent.getStringExtra(EXTRA_REQUEST_ID)
        );
        return gateway.executeBlocking(action, 6000L);
    }

    private void sendResultBroadcast(Context context, Intent source, ExecutionResult result) {
        Intent reply = new Intent(ACTION_RESULT);
        reply.putExtra(EXTRA_RESULT_JSON, result.toJson());
        reply.putExtra(EXTRA_REQUEST_ID, result.getRequestId());
        String replyPackage = source == null ? null : source.getStringExtra(EXTRA_REPLY_PACKAGE);
        if (replyPackage != null && !replyPackage.isBlank()) reply.setPackage(replyPackage);
        context.sendBroadcast(reply);
    }
}
