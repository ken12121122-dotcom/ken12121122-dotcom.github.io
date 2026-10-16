package com.amin.pocketgba;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.webkit.WebChromeClient;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class LivingLibraryFileChooserTest {
    private static WebChromeClient.FileChooserParams photoParams() {
        return new WebChromeClient.FileChooserParams() {
            @Override public int getMode() { return MODE_OPEN; }
            @Override public String[] getAcceptTypes() { return new String[]{"image/png,image/jpeg,image/webp"}; }
            @Override public boolean isCaptureEnabled() { return false; }
            @Override public CharSequence getTitle() { return "Photo"; }
            @Override public String getFilenameHint() { return ""; }
            @Override public Intent createIntent() { throw new AssertionError("Use the bounded library picker"); }
        };
    }
    private static void waitHome(ActivityScenario<LivingLibraryActivity> scenario) throws Exception {
        for (int i = 0; i < 300; i++) {
            final boolean[] ready = {false};
            scenario.onActivity(a -> ready[0] = LivingLibraryFileChooserPolicy.allowedPage(a.committedUrl, a.workbenchView().getUrl()));
            if (ready[0]) return;
            Thread.sleep(100);
        }
        fail("Library home never committed");
    }
    @Test public void imagePickerReturnsOneUserSelectedUriAndCancellationReturnsNull() throws Exception {
        try (ActivityScenario<LivingLibraryActivity> scenario = ActivityScenario.launch(LivingLibraryActivity.class)) {
            waitHome(scenario);
            Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
            for (boolean cancel : new boolean[]{false, true}) {
                IntentFilter filter = new IntentFilter(Intent.ACTION_OPEN_DOCUMENT);
                filter.addCategory(Intent.CATEGORY_OPENABLE);
                filter.addDataType("*/*");
                Uri uri = Uri.parse("content://synthetic.library.photo/selected.png");
                Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(filter,
                        new Instrumentation.ActivityResult(cancel ? Activity.RESULT_CANCELED : Activity.RESULT_OK,
                                cancel ? null : new Intent().setData(uri)), true);
                CountDownLatch delivered = new CountDownLatch(1);
                AtomicReference<Uri[]> result = new AtomicReference<>();
                try {
                    scenario.onActivity(a -> assertTrue(a.requestLibraryFile(values -> { result.set(values); delivered.countDown(); }, photoParams())));
                    assertTrue("File callback not delivered", delivered.await(10, TimeUnit.SECONDS));
                    assertEquals(1, monitor.getHits());
                    if (cancel) assertNull(result.get());
                    else assertArrayEquals(new Uri[]{uri}, result.get());
                } finally { instrumentation.removeMonitor(monitor); }
            }
        }
    }
    @Test public void uncommittedHomeCannotLaunchFilePicker() throws Exception {
        try (ActivityScenario<LivingLibraryActivity> scenario = ActivityScenario.launch(LivingLibraryActivity.class)) {
            waitHome(scenario);
            AtomicReference<Uri[]> result = new AtomicReference<>(new Uri[]{Uri.EMPTY});
            scenario.onActivity(a -> {
                String old = a.committedUrl; a.committedUrl = "";
                assertTrue(a.requestLibraryFile(result::set, photoParams()));
                assertNull(result.get()); a.committedUrl = old;
            });
        }
    }
}
