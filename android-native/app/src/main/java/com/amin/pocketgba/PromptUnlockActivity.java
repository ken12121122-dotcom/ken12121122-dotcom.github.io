package com.amin.pocketgba;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Delegates prompt access verification to the Android system lock screen.
 * Amin never receives or stores the user's device PIN, pattern, password, or biometric data.
 */
public final class PromptUnlockActivity extends Activity {
    public static final String ACTION_PROMPT_UNLOCKED = "com.amin.pocketgba.ACTION_PROMPT_UNLOCKED";
    private static final int REQUEST_CONFIRM_DEVICE = 2401;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguardManager == null || !keyguardManager.isDeviceSecure()) {
            Toast.makeText(this, "請先在手機設定螢幕鎖定", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(
                "解鎖 Amin 提示詞",
                "請使用手機的螢幕鎖驗證"
        );
        if (intent == null) {
            Toast.makeText(this, "無法開啟手機驗證", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        startActivityForResult(intent, REQUEST_CONFIRM_DEVICE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CONFIRM_DEVICE && resultCode == RESULT_OK) {
            Intent unlocked = new Intent(ACTION_PROMPT_UNLOCKED);
            unlocked.setPackage(getPackageName());
            sendBroadcast(unlocked);
        }
        finish();
    }
}
