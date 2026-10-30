package com.yongge.tvlock;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import java.util.List;

/**
 * 看门狗服务，运行在独立 :watchdog 进程。
 * 与主进程 TimerService 相互保活：2 秒检查一次，对方不在就拉起。
 */
public class WatchdogService extends Service {
    private Handler handler;
    private static final long INTERVAL = 2000;

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
    }

    private final Runnable watch = new Runnable() {
        @Override
        public void run() {
            try {
                if (!isServiceRunning("com.yongge.tvlock.TimerService")) {
                    Intent i = new Intent(WatchdogService.this, TimerService.class);
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
                    else startService(i);
                }
            } catch (Exception ignored) {
            }
            handler.postDelayed(this, INTERVAL);
        }
    };

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
