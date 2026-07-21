package com.amin.pocketgba;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
        AtomicReference<ExecutionResult> resultRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        mainHandler.post(() -> {
            try { resultRef.set(executeOnMainThread(action, startedAt)); }
            finally { latch.countDown(); }
        });
        try {
            if (!latch.await(Math.max(250L, timeoutMs), TimeUnit.MILLISECONDS)) {
                ExecutionResult timeout = ExecutionResult.timeout(action, startedAt);
                eventStore.recordExecution(timeout);
                return timeout;
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            ExecutionResult interrupted = ExecutionResult.failure(
                    action, startedAt, "EXECUTION_INTERRUPTED", "Action execution was interrupted"
            );
            eventStore.recordExecution(interrupted);
            return interrupted;
        }
        ExecutionResult result = resultRef.get();
        return result == null
                ? ExecutionResult.failure(action, startedAt, "EXECUTION_EMPTY", "No execution result")
                : result;
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
