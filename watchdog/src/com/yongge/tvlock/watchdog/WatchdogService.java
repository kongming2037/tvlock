package com.yongge.tvlock.watchdog;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import java.util.List;

/**
 * 独立 App 的看门狗服务。
 * 监控主 App (com.yongge.tvlock) 的 TimerService，2 秒检查一次，死了就拉起。
 * 主 App 也会反向监控本服务，实现双向保活。
 */
public class WatchdogService extends Service {
    private Handler handler;
    private static final long INTERVAL = 2000;
    private static final String TARGET_PKG = "com.yongge.tvlock";
    private static final String TARGET_SVC = "com.yongge.tvlock.TimerService";

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String chId = "tvlock_wd";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(chId, "看门狗", NotificationManager.IMPORTANCE_MIN);
            nm.createNotificationChannel(ch);
        }
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) b = new Notification.Builder(this, chId);
        else b = new Notification.Builder(this);
        b.setContentTitle("计时锁看门狗").setContentText("运行中")
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock);
        startForeground(2, b.build());

        handler = new Handler(Looper.getMainLooper());
        handler.post(watch);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null) handler.removeCallbacksAndMessages(null);
        // 被杀时尝试自拉起（尽力）
        try {
            Intent i = new Intent(this, WatchdogService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
        } catch (Exception ignored) {
        }
    }

    private final Runnable watch = new Runnable() {
        @Override
        public void run() {
            try {
                // 检查是否被手动停用
                if (!WatchdogActivity.isEnabled(WatchdogService.this)) {
                    stopSelf();
                    return;
                }
                if (!isServiceRunning(TARGET_SVC)) {
                    // 先检查主 App 是否还存在（被卸载则尝试引导重装）
                    if (!isPackageInstalled(TARGET_PKG)) {
                        onMainAppRemoved();
                    } else {
                        Intent i = new Intent();
                        i.setComponent(new ComponentName(TARGET_PKG, TARGET_SVC));
                        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
                        else startService(i);
                    }
                }
            } catch (Exception ignored) {
            }
            handler.postDelayed(this, INTERVAL);
        }
    };

    private boolean isPackageInstalled(String pkg) {
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 主 App 被卸载：从内置 APK 触发重装 */
    private void onMainAppRemoved() {
        try {
            // 从 assets 释放 APK
            java.io.File apkFile = new java.io.File(getCacheDir(), "main.apk");
            if (!apkFile.exists()) {
                java.io.InputStream in = getAssets().open("main.apk");
                java.io.FileOutputStream out = new java.io.FileOutputStream(apkFile);
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                out.close();
                in.close();
            }
            // 用 PackageInstaller 触发安装（会弹用户确认）
            android.content.pm.PackageInstaller pi = getPackageManager().getPackageInstaller();
            android.content.pm.PackageInstaller.SessionParams params =
                    new android.content.pm.PackageInstaller.SessionParams(
                            android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            int sessionId = pi.createSession(params);
            android.content.pm.PackageInstaller.Session session = pi.openSession(sessionId);
            java.io.InputStream in = new java.io.FileInputStream(apkFile);
            java.io.OutputStream out = session.openWrite("main", 0, apkFile.length());
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            session.fsync(out);
            out.close();
            in.close();
            // 创建安装确认 Intent
            Intent intent = new Intent(this, InstallReceiver.class);
            intent.setAction("com.yongge.tvlock.watchdog.INSTALL_COMPLETE");
            android.app.PendingIntent pi2 = android.app.PendingIntent.getBroadcast(
                    this, sessionId, intent,
                    Build.VERSION.SDK_INT >= 23 ? android.app.PendingIntent.FLAG_IMMUTABLE : 0);
            session.commit(pi2.getIntentSender());
            session.close();
        } catch (Exception e) {
            // 兜底：发通知提醒
            try {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                String chId = "tvlock_wd_alert";
                if (Build.VERSION.SDK_INT >= 26) {
                    NotificationChannel ch = new NotificationChannel(chId, "告警", NotificationManager.IMPORTANCE_HIGH);
                    nm.createNotificationChannel(ch);
                }
                Notification.Builder b;
                if (Build.VERSION.SDK_INT >= 26) b = new Notification.Builder(this, chId);
                else b = new Notification.Builder(this);
                b.setContentTitle("屏幕计时锁被卸载")
                        .setContentText("自动重装失败，请手动重装主程序")
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setAutoCancel(true);
                nm.notify(99, b.build());
            } catch (Exception ignored) {
            }
        }
    }

    private boolean isServiceRunning(String cls) {
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningServiceInfo> list = am.getRunningServices(100);
            for (ActivityManager.RunningServiceInfo s : list) {
                if (cls.equals(s.service.getClassName())) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
