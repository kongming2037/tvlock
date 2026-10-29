package com.yongge.tvlock;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * 60 秒闹钟自救：MIUI 会强杀计时服务，本接收器每 60 秒被系统唤醒一次，
 * 若服务不在运行则拉起。闹钟由系统管理，不依赖 App 进程存活。
 */
public class KeepAliveReceiver extends BroadcastReceiver {

    private static final int INTERVAL_MS = 60 * 1000;
    private static final int REQ = 0x6b65;

    @Override
    public void onReceive(Context c, Intent intent) {
        try {
            if (!isServiceRunning(c)) {
                Intent s = new Intent(c, TimerService.class);
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s);
                else c.startService(s);
            }
        } catch (Exception ignored) {
        }
        schedule(c);
    }

    public static void schedule(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent i = new Intent(c, KeepAliveReceiver.class);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getBroadcast(c, REQ, i, flags);
            long next = System.currentTimeMillis() + INTERVAL_MS;
            if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, next, pi);
            }
        } catch (Exception ignored) {
        }
    }

    public static void cancel(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent i = new Intent(c, KeepAliveReceiver.class);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getBroadcast(c, REQ, i, flags);
            am.cancel(pi);
        } catch (Exception ignored) {
        }
    }

    private static boolean isServiceRunning(Context c) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        for (ActivityManager.RunningServiceInfo s : am.getRunningServices(100)) {
            if (TimerService.class.getName().equals(s.service.getClassName())) return true;
        }
        return false;
    }
}
