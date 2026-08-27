package com.amin.pocketgba;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lightweight, read-only execution trace bus for the Neural Flow POC.
 *
 * Producers emit what actually happened. Consumers (the canvas) only observe.
 * This class never changes routing decisions and never invokes an LLM/tool itself.
 */
public final class NeuralFlowTrace {
    public enum Stage {
        INPUT,
        ROUTER,
        NODE_REGISTRY,
        COMMAND,
        LLM_REQUEST,
        LLM_RESPONSE,
        LLM_ERROR,
        MEMORY_JUDGE,
        MEMORY_COMPRESS,
        MEMORY_APPROVAL,
        MEMORY_STORE,
        MEMORY_ERROR
    }

    public static final class Event {
        public final long sequence;
        public final long timestampMs;
        public final String turnId;
        public final Stage stage;
        public final String status;
        public final String detail;

        Event(long sequence, long timestampMs, String turnId, Stage stage, String status, String detail) {
            this.sequence = sequence;
            this.timestampMs = timestampMs;
            this.turnId = turnId;
            this.stage = stage;
            this.status = status;
            this.detail = detail == null ? "" : detail;
        }
    }

    public interface Listener {
        void onTraceEvent(Event event);
    }

    private static final int MAX_EVENTS = 120;
    private static final Object LOCK = new Object();
    private static final ArrayDeque<Event> HISTORY = new ArrayDeque<>();
    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static long sequence;
    private static long turnSequence;

    private NeuralFlowTrace() { }

    public static String beginTurn(String detail) {
        final String turnId;
        synchronized (LOCK) {
            turnSequence++;
            turnId = "TURN-" + turnSequence;
        }
        emit(turnId, Stage.INPUT, "received", detail);
        return turnId;
    }

    public static void emit(String turnId, Stage stage, String status, String detail) {
        if (turnId == null || turnId.trim().isEmpty() || stage == null) return;
        final Event event;
        synchronized (LOCK) {
            sequence++;
            event = new Event(sequence, System.currentTimeMillis(), turnId, stage,
                    status == null ? "" : status, detail);
            HISTORY.addLast(event);
            while (HISTORY.size() > MAX_EVENTS) HISTORY.removeFirst();
        }
        for (Listener listener : LISTENERS) {
            try { listener.onTraceEvent(event); } catch (RuntimeException ignored) { }
        }
    }

    public static List<Event> snapshot() {
        synchronized (LOCK) {
            return Collections.unmodifiableList(new ArrayList<>(HISTORY));
        }
    }

    public static String latestTurnId() {
        synchronized (LOCK) {
            Event last = HISTORY.peekLast();
            return last == null ? "" : last.turnId;
        }
    }

    public static List<Event> latestTurnEvents() {
        String id = latestTurnId();
        if (id.isEmpty()) return Collections.emptyList();
        ArrayList<Event> result = new ArrayList<>();
        synchronized (LOCK) {
            for (Event event : HISTORY) if (id.equals(event.turnId)) result.add(event);
        }
        return Collections.unmodifiableList(result);
    }

    public static void addListener(Listener listener) {
        if (listener != null) LISTENERS.addIfAbsent(listener);
    }

    public static void removeListener(Listener listener) {
        if (listener != null) LISTENERS.remove(listener);
    }

    static void clearForPoc() {
        synchronized (LOCK) { HISTORY.clear(); }
    }
}
