package com.amin.pocketgba;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.widget.Toast;

public final class PackageInstallResultReceiver extends BroadcastReceiver {
    public static final String ACTION_INSTALL_STATUS =
            "com.amin.pocketgba.action.INSTALL_STATUS";

    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE
        );
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmation;
            if (Build.VERSION.SDK_INT >= 33) {
                confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
            } else {
                confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            }
            if (confirmation != null) {
                confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(confirmation);
            }
            return;
        }

        if (status == PackageInstaller.STATUS_SUCCESS) {
            Toast.makeText(context, "Amin Pocket GBA 更新完成", Toast.LENGTH_LONG).show();
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(launch);
            }
            return;
        }

        String detail = message == null || message.trim().isEmpty()
                ? "Android 回報安裝失敗，請重新檢查更新。"
                : message;
        Toast.makeText(context, "更新失敗：" + detail, Toast.LENGTH_LONG).show();

        Intent reopen = new Intent(context, NativeUpdateActivity.class);
        reopen.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        reopen.putExtra("install_error", detail);
        context.startActivity(reopen);
    }
}
