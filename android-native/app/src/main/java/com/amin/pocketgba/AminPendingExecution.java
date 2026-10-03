package com.amin.pocketgba;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Per-call arbitration; cancels queued work, never claims to undo running work. */
final class AminPendingExecution<T> {
    private enum State { QUEUED, RUNNING, COMPLETED, CANCELLED }
    private final AtomicReference<State> state = new AtomicReference<>(State.QUEUED);
    private final CountDownLatch done = new CountDownLatch(1);
    private volatile T result;

    boolean tryStart() {
        return state.compareAndSet(State.QUEUED, State.RUNNING);
    }

    boolean cancelBeforeStart() {
        boolean cancelled = state.compareAndSet(State.QUEUED, State.CANCELLED);
        if (cancelled) done.countDown();
        return cancelled;
    }

    void complete(T value) {
        if (state.get() != State.RUNNING) return;
        result = value;
        state.set(State.COMPLETED);
        done.countDown();
    }

    boolean await(long timeoutMs) throws InterruptedException {
        return done.await(Math.max(250L, timeoutMs), TimeUnit.MILLISECONDS);
    }

    T completedResult() {
        return state.get() == State.COMPLETED ? result : null;
    }
}
