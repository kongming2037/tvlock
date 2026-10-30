package com.yongge.tvlock.watchdog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/** 安装结果回调 */
public class InstallReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
        if (status == PackageInstaller.STATUS_SUCCESS) {
            // 安装成功，拉起主服务
            try {
                Intent s = new Intent();
                s.setComponent(new android.content.ComponentName(
                        "com.yongge.tvlock",
                        "com.yongge.tvlock.TimerService"));
                if (android.os.Build.VERSION.SDK_INT >= 26) c.startForegroundService(s);
                else c.startService(s);
            } catch (Exception ignored) {
            }
        }
    }
}
