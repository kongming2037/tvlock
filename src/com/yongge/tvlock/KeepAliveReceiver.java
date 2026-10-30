package com.yongge.tvlock;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

public class KeepAliveReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        try {
            Intent s = new Intent(c, TimerService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s);
            else c.startService(s);
        } catch (Exception ignored) {
        }
        schedule(c);
    }

    public static void schedule(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(c, KeepAliveReceiver.class);
            PendingIntent pi = PendingIntent.getBroadcast(c, 0, i,
                    Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                            : PendingIntent.FLAG_UPDATE_CURRENT);
            long next = SystemClock.elapsedRealtime() + 60000;
            if (Build.VERSION.SDK_INT >= 23) am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, next, pi);
            else am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, next, pi);
        } catch (Exception ignored) {
        }
    }
}
