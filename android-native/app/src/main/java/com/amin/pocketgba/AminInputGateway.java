package com.amin.pocketgba;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public final class AminInputGateway {
    public interface Callback { void onComplete(ExecutionResult result); }
    private static volatile AminInputGateway instance;

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AminActionValidator validator = new AminActionValidator();
    private final AminEventStore eventStore;

    private AminInputGateway(Context context) {
        appContext = context.getApplicationContext();
        eventStore = new AminEventStore(appContext);
    }

    public static AminInputGateway get(Context context) {
        AminInputGateway local = instance;
        if (local == null) {
            synchronized (AminInputGateway.class) {
                local = instance;
                if (local == null) {
                    local = new AminInputGateway(context);
                    instance = local;
                }
            }
        }
        return local;
    }

    public AminEventStore getEventStore() { return eventStore; }

    public void execute(AminAction action, Callback callback) {
        Runnable task = () -> {
            ExecutionResult result = executeOnMainThread(action, Instant.now().toString());
            if (callback != null) callback.onComplete(result);
        };
        if (Looper.myLooper() == Looper.getMainLooper()) task.run();
        else mainHandler.post(task);
    }

    public ExecutionResult executeBlocking(AminAction action, long timeoutMs) {
        String startedAt = Instant.now().toString();
        if (Looper.myLooper() == Looper.getMainLooper()) return executeOnMainThread(action, startedAt);
        AminPendingExecution<ExecutionResult> pending = new AminPendingExecution<>();
        Runnable task = () -> {
            if (!pending.tryStart()) return;
            ExecutionResult result = null;
            try { result = executeOnMainThread(action, startedAt); }
            finally { pending.complete(result); }
        };
        if (!mainHandler.post(task)) {
            pending.cancelBeforeStart();
            ExecutionResult rejected = ExecutionResult.failure(
                    action, startedAt, "EXECUTION_QUEUE_UNAVAILABLE", "Main action queue is unavailable"
            );
            eventStore.recordExecution(rejected);
            return rejected;
        }
        try {
            if (!pending.await(timeoutMs)) {
                return stopWaiting(pending, task, action, startedAt, false);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return stopWaiting(pending, task, action, startedAt, true);
        }
        ExecutionResult result = pending.completedResult();
        return result == null
                ? ExecutionResult.failure(action, startedAt, "EXECUTION_EMPTY", "No execution result")
                : result;
    }

    private ExecutionResult stopWaiting(
            AminPendingExecution<ExecutionResult> pending, Runnable task,
            AminAction action, String startedAt, boolean interrupted
    ) {
        if (pending.cancelBeforeStart()) {
            // CAS prevents execution even if Handler has already dequeued the callback.
            mainHandler.removeCallbacks(task);
            ExecutionResult cancelled = interrupted
                    ? ExecutionResult.failure(action, startedAt, "EXECUTION_INTERRUPTED",
                            "Action was cancelled before execution because the caller was interrupted")
                    : ExecutionResult.timeout(action, startedAt);
            eventStore.recordExecution(cancelled);
            return cancelled;
        }
        ExecutionResult completed = pending.completedResult();
        if (completed != null) return completed;
        // Dispatch has started and cannot be rolled back here. Only its actual result is audited.
        return ExecutionResult.failure(action, startedAt, "EXECUTION_OUTCOME_PENDING",
                "Action already started; outcome is pending. Check events by requestId before retrying");
    }

    public AminAction createAction(String requestedName, JSONObject parameters, String source, double confidence) {
        return createAction(requestedName, parameters, source, confidence, "");
    }

    public AminAction createAction(
            String requestedName, JSONObject parameters, String source, double confidence, String requestId
    ) {
        return new AminAction(
                resolveActionName(requestedName),
                parameters == null ? new JSONObject() : parameters,
                source == null || source.isBlank() ? "unknown" : source,
                confidence,
                requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId,
                Instant.now().toString()
        );
    }

    public String resolveActionName(String requestedName) {
        if (requestedName == null) return "";
        String trimmed = requestedName.trim();
        for (VoiceCommandCatalog.Command command : VoiceCommandCatalog.getCommands()) {
            if (command.getId().equalsIgnoreCase(trimmed)
                    || command.getAction().equalsIgnoreCase(trimmed)) return command.getAction();
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private ExecutionResult executeOnMainThread(AminAction action, String startedAt) {
        AminActionValidator.ValidationResult validation = validator.validate(action);
        if (!validation.isValid()) {
            ExecutionResult rejected = ExecutionResult.failure(
                    action, startedAt, validation.getCode(), validation.getMessage()
            );
            eventStore.recordExecution(rejected);
            return rejected;
        }
        AminActionDispatcher.DispatchResult dispatch = AminActionDispatcher.dispatch(appContext, action);
        ExecutionResult result = dispatch.isSuccess()
                ? ExecutionResult.success(action, startedAt, dispatch.getMessage())
                : ExecutionResult.failure(action, startedAt, "EXECUTION_FAILED", dispatch.getMessage());
        eventStore.recordExecution(result);
        return result;
    }
}
