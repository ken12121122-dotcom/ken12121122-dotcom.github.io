package com.amin.pocketgba;

import static org.junit.Assert.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

public final class AminPendingExecutionTest {
    @Test public void cancelledQueuedActionNeverStartsWhenQueueDrains() {
        AminPendingExecution<String> pending = new AminPendingExecution<>();
        assertTrue(pending.cancelBeforeStart());
        assertFalse(pending.tryStart());
        assertNull(pending.completedResult());
    }

    @Test public void runningActionCannotBeReportedAsCancelled() {
        AminPendingExecution<String> pending = new AminPendingExecution<>();
        assertTrue(pending.tryStart());
        assertFalse(pending.cancelBeforeStart());
        assertNull(pending.completedResult());
        pending.complete("actual-result");
        assertEquals("actual-result", pending.completedResult());
    }

    @Test public void completionUnblocksWaitingCaller() throws Exception {
        AminPendingExecution<String> pending = new AminPendingExecution<>();
        assertTrue(pending.tryStart());
        pending.complete("done");
        assertTrue(pending.await(250));
        assertEquals("done", pending.completedResult());
        assertFalse(pending.tryStart());
        assertFalse(pending.cancelBeforeStart());
    }

    @Test public void expiredWaitCanCancelQueuedAction() throws Exception {
        AminPendingExecution<String> pending = new AminPendingExecution<>();
        assertFalse(pending.await(250));
        assertTrue(pending.cancelBeforeStart());
        assertFalse(pending.tryStart());
    }

    @Test public void expiredWaitCannotCancelStartedAction() throws Exception {
        AminPendingExecution<String> pending = new AminPendingExecution<>();
        assertTrue(pending.tryStart());
        assertFalse(pending.await(250));
        assertFalse(pending.cancelBeforeStart());
        pending.complete("late-result");
        assertEquals("late-result", pending.completedResult());
    }

    @Test public void interruptionLeavesQueuedWorkCancellable() throws Exception {
        AminPendingExecution<String> pending = new AminPendingExecution<>();
        Thread.currentThread().interrupt();
        try {
            pending.await(250);
            fail("Expected InterruptedException");
        } catch (InterruptedException expected) {
            assertTrue(pending.cancelBeforeStart());
            assertFalse(pending.tryStart());
        } finally {
            Thread.interrupted();
        }
    }

    @Test public void startAndCancellationHaveExactlyOneWinner() throws Exception {
        for (int i = 0; i < 200; i++) {
            AminPendingExecution<String> pending = new AminPendingExecution<>();
            CountDownLatch release = new CountDownLatch(1);
            AtomicBoolean started = new AtomicBoolean(), cancelled = new AtomicBoolean();
            Thread executor = new Thread(() -> {
                await(release);
                started.set(pending.tryStart());
                if (started.get()) pending.complete("done");
            });
            Thread caller = new Thread(() -> {
                await(release);
                cancelled.set(pending.cancelBeforeStart());
            });
            executor.start(); caller.start(); release.countDown();
            executor.join(2000); caller.join(2000);
            assertFalse(executor.isAlive()); assertFalse(caller.isAlive());
            assertTrue(started.get() ^ cancelled.get());
            assertFalse(pending.tryStart());
            if (started.get()) assertEquals("done", pending.completedResult());
        }
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); }
        catch (InterruptedException error) { throw new AssertionError(error); }
    }
}
