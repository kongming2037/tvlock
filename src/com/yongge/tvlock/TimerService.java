package com.yongge.tvlock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;
import java.util.List;

public class TimerService extends Service {
    public static final String ACTION_UPDATE = "com.yongge.tvlock.UPDATE";

    private Handler handler;
    private TextView overlayView;
    private WindowManager wm;
    private int remainingSec;
    private long deadline;
    private boolean locked;
    private boolean override;
    private String day;
    private int tickCount = 0;

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        String chId = "tvlock";
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(chId, "计时服务", NotificationManager.IMPORTANCE_MIN);
            nm.createNotificationChannel(ch);
        }
        Intent pi = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent p = PendingIntent.getActivity(this, 0, pi, flags);
        Notification n = new Notification.Builder(this, chId)
                .setContentTitle("屏幕计时")
                .setContentText("计时服务运行中")
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentIntent(p)
                .build();
        startForeground(1, n);

        loadState();
        addOverlay();
        KeepAliveReceiver.schedule(this);
        handler = new Handler(Looper.getMainLooper());
        handler.post(tick);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String a = intent.getAction();
            if ("UNLOCK".equals(a)) {
                locked = false;
                override = true;
                saveState();
            } else if ("ADD_TIME".equals(a)) {
                int m = intent.getIntExtra("min", 30);
                locked = false;
                override = false;
                setTimer(m);
                saveState();
            } else if ("RELOCK".equals(a)) {
                override = false;
                enterLocked();
            } else if ("RESET_DAY".equals(a)) {
                day = Prefs.today();
                locked = false;
                override = false;
                setTimer(Prefs.getDailyLimitMin(this));
                saveState();
            }
            updateOverlay();
            broadcastUpdate();
        }
        // 注意：不用 START_STICKY。MIUI 会把频繁自重启视为异常而加剧猎杀；
        // 改由看门狗定时拉起，计时本身按 deadline 走，杀掉重来也不丢时间。
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null) handler.removeCallbacks(tick);
        if (wm != null && overlayView != null) {
            try {
                wm.removeView(overlayView);
            } catch (Exception ignored) {
            }
        }
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            try {
                String t = Prefs.today();
                if (!t.equals(day)) {
                    day = t;
                    locked = false;
                    override = false;
                    setTimer(Prefs.getDailyLimitMin(TimerService.this));
                    saveState();
                }
                if (!locked && !override) {
                    // 按 deadline 换算剩余秒数：即使进程被杀过一段时间，
                    // 重启后也能得出正确剩余时间，不会把被杀的时间“吐出来”
                    remainingSec = (int) Math.max(0,
                            (deadline - System.currentTimeMillis()) / 1000);
                    if (remainingSec <= 0) {
                        remainingSec = 0;
                        enterLocked();
                    } else {
                        saveState();
                    }
                } else if (locked) {
                    enforceLock();
                }
                updateOverlay();
                tickCount++;
                if (tickCount % 5 == 0) broadcastUpdate();
            } catch (Exception ignored) {
            }
            handler.postDelayed(this, 1000);
        }
    };

    private void enterLocked() {
        locked = true;
        saveState();
        updateOverlay();
        Intent i = new Intent(this, LockActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    private void enforceLock() {
        String fg = foregroundPackage();
        if (fg != null && !fg.equals(getPackageName())) {
            Intent i = new Intent(this, LockActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            try {
                startActivity(i);
            } catch (Exception ignored) {
            }
        }
    }

    private String foregroundPackage() {
        try {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return null;
            long now = System.currentTimeMillis();
            List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 8000, now);
            if (stats == null || stats.isEmpty()) return null;
            UsageStats best = null;
            for (UsageStats s : stats) {
                if (best == null || s.getLastTimeUsed() > best.getLastTimeUsed()) best = s;
            }
            return best != null ? best.getPackageName() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void loadState() {
        day = Prefs.sp(this).getString("day", "");
        remainingSec = Prefs.sp(this).getInt("remaining", -1);
        deadline = Prefs.sp(this).getLong("deadline", 0);
        locked = Prefs.sp(this).getBoolean("locked", false);
        override = Prefs.sp(this).getBoolean("override", false);
        String t = Prefs.today();
        if (!t.equals(day) || remainingSec < 0) {
            day = t;
            locked = false;
            override = false;
            setTimer(Prefs.getDailyLimitMin(this));
        } else if (!locked && !override && deadline > 0) {
            // 按 deadline 校准：补上进程被杀期间流逝的时间
            remainingSec = (int) Math.max(0,
                    (deadline - System.currentTimeMillis()) / 1000);
        }
        saveState();
    }

    private void saveState() {
        Prefs.sp(this).edit()
                .putString("day", day)
                .putInt("remaining", remainingSec)
                .putLong("deadline", deadline)
                .putBoolean("locked", locked)
                .putBoolean("override", override)
                .apply();
    }

    /** 设定计时器：n 分钟后到期 */
    private void setTimer(int minutes) {
        remainingSec = minutes * 60;
        deadline = System.currentTimeMillis() + remainingSec * 1000L;
    }

    private void addOverlay() {
        try {
            wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            overlayView = new TextView(this);
            overlayView.setTextSize(20);
            overlayView.setTextColor(0xFFFFFFFF);
            overlayView.setBackgroundColor(0x99000000);
            float d = getResources().getDisplayMetrics().density;
            int pad = (int) (10 * d + 0.5f);
            overlayView.setPadding(pad, (int) (6 * d + 0.5f), pad, (int) (6 * d + 0.5f));
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT);
            p.gravity = Gravity.TOP | Gravity.END;
            p.x = (int) (24 * d + 0.5f);
            p.y = (int) (24 * d + 0.5f);
            wm.addView(overlayView, p);
            updateOverlay();
        } catch (Exception ignored) {
        }
    }

    private void updateOverlay() {
        if (overlayView == null) return;
        try {
            String t;
            if (locked) t = "已锁定";
            else if (override) t = "今日已解锁";
            else t = "剩余 " + Prefs.fmt(remainingSec);
            overlayView.setText(t);
        } catch (Exception ignored) {
        }
    }

    private void broadcastUpdate() {
        Intent i = new Intent(ACTION_UPDATE);
        i.putExtra("remaining", remainingSec);
        i.putExtra("locked", locked);
        sendBroadcast(i);
    }
}
