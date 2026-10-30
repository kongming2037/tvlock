package com.yongge.tvlock;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

public class TimerService extends Service {
    public static final String ACTION_UPDATE = "com.yongge.tvlock.UPDATE";

    private Handler handler;
    private NotificationManager nm;
    private String chId = "tvlock";
    private int remainingSec;
    private long deadline;
    private boolean locked;
    private String day;
    private int tickCount = 0;

    // 左上角常驻倒计时悬浮窗
    private WindowManager wm;
    private TextView floatView;
    // 全屏锁定覆盖层
    private View lockView;

    // 三键监听：返回/菜单直接拦截，桌面键 1 秒拉回
    private final BroadcastReceiver homeWatcher = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(i.getAction())) {
                String reason = i.getStringExtra("reason");
                if ("homekey".equals(reason)) {
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (locked) {
                                if (lockView == null) showLockOverlay();
                            } else {
                                if (floatView == null || floatView.getParent() == null) {
                                    floatView = null;
                                    showFloat();
                                }
                            }
                        }
                    }, 1000);
                }
            }
        }
    };

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(chId, "计时服务", NotificationManager.IMPORTANCE_MIN);
            nm.createNotificationChannel(ch);
        }
        startForeground(1, buildNotification("计时服务运行中"));

        loadState();
        KeepAliveReceiver.schedule(this);
        handler = new Handler(Looper.getMainLooper());
        try {
            registerReceiver(homeWatcher, new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
        } catch (Exception ignored) {
        }
        ensureWatchdog();
        showFloat();
        handler.post(tick);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null) handler.removeCallbacksAndMessages(null);
        try {
            unregisterReceiver(homeWatcher);
        } catch (Exception ignored) {
        }
        hideFloat();
        hideLockOverlay();
    }

    private void ensureWatchdog() {
        try {
            // 拉起独立看门狗 App 的服务（跨应用保活）
            Intent i = new Intent();
            i.setComponent(new android.content.ComponentName(
                    "com.yongge.tvlock.watchdog",
                    "com.yongge.tvlock.watchdog.WatchdogService"));
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
        } catch (Exception ignored) {
        }
    }

    private Notification buildNotification(String text) {
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) b = new Notification.Builder(this, chId);
        else b = new Notification.Builder(this);
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        b.setContentTitle("屏幕计时锁").setContentText(text)
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentIntent(pi);
        return b.build();
    }

    private void updateNotification() {
        String t = locked ? "已锁定" : "剩余 " + Prefs.fmt(remainingSec);
        nm.notify(1, buildNotification(t));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String a = intent.getAction();
            if ("LOCK_NOW".equals(a) || "RELOCK".equals(a)) {
                enterLocked();
            } else if ("RESET".equals(a)) {
                // 立即重置：重开满额周期
                // 优先用 Intent 传来的值（:ui 进程直接传，避免多进程 prefs 不同步）
                int min = intent.getIntExtra("period_min", -1);
                if (min < 1) min = Prefs.getPeriodMin(this);
                // 同步回 prefs，保证一致
                Prefs.setPeriodMin(this, min);
                locked = false;
                day = Prefs.today();
                setTimer(min);
                saveState();
                updateNotification();
                hideLockOverlay();
                showFloat();
                broadcastUpdate();
            } else if ("UNLOCK".equals(a)) {
                // 密码解锁后开新周期（兼容旧调用）
                locked = false;
                setTimer(Prefs.getPeriodMin(this));
                saveState();
                updateNotification();
                hideLockOverlay();
                showFloat();
            } else if ("SET_PASSWORD".equals(a)) {
                // :ui 进程传来的新密码，同步到主进程 prefs
                String np = intent.getStringExtra("password");
                if (np != null && !np.isEmpty()) {
                    Prefs.setPassword(this, np);
                }
            } else if ("HIDE_ICON".equals(a)) {
                setLauncherIcon(false);
            } else if ("SHOW_ICON".equals(a)) {
                setLauncherIcon(true);
            }
        }
        ensureWatchdog();
        return START_STICKY;
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            try {
                String t = Prefs.today();
                if (!t.equals(day)) {
                    day = t;
                    locked = false;
                    setTimer(Prefs.getPeriodMin(TimerService.this));
                    saveState();
                }
                if (!locked) {
                    remainingSec = (int) Math.max(0,
                            (deadline - System.currentTimeMillis()) / 1000);
                    if (remainingSec <= 0) {
                        remainingSec = 0;
                        enterLocked();
                    } else {
                        saveState();
                    }
                } else {
                    enforceLock();
                }
                updateNotification();
                updateFloat();
                tickCount++;
                if (tickCount % 2 == 0) {
                    broadcastUpdate();
                    ensureWatchdog();
                }
            } catch (Exception ignored) {
            }
            handler.postDelayed(this, 1000);
        }
    };

    /** 左上角常驻倒计时悬浮窗，每秒更新 */
    private void showFloat() {
        try {
            if (floatView != null) return;
            if (wm == null) wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            floatView = new TextView(this);
            floatView.setTextSize(22);
            floatView.setTextColor(0xFFFFFFFF);
            floatView.setBackgroundColor(0xCC000000);
            float d = getResources().getDisplayMetrics().density;
            int pad = (int) (12 * d + 0.5f);
            floatView.setPadding(pad, (int) (8 * d + 0.5f), pad, (int) (8 * d + 0.5f));
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT);
            // 左上角
            p.gravity = Gravity.TOP | Gravity.START;
            p.x = (int) (24 * d + 0.5f);
            p.y = (int) (24 * d + 0.5f);
            wm.addView(floatView, p);
            updateFloat();
        } catch (Exception ignored) {
        }
    }

    private void updateFloat() {
        try {
            if (floatView == null || floatView.getParent() == null) {
                floatView = null;
                if (!locked) showFloat();
                return;
            }
            if (locked) {
                hideFloat();
                return;
            }
            floatView.setText(Prefs.fmt(remainingSec) + "后锁定");
        } catch (Exception ignored) {
        }
    }

    private void hideFloat() {
        try {
            if (wm != null && floatView != null) {
                try {
                    wm.removeView(floatView);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        floatView = null;
    }

    private void enterLocked() {
        locked = true;
        saveState();
        updateNotification();
        hideFloat();
        showLockOverlay();
    }

    /** 全屏锁定覆盖层：拦截返回/菜单，桌面键 1 秒拉回 */
    private void showLockOverlay() {
        try {
            if (lockView != null) return;
            if (wm == null) wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            float d = getResources().getDisplayMetrics().density;
            int pad = (int) (24 * d + 0.5f);

            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setGravity(Gravity.CENTER);
            layout.setBackgroundColor(0xFF000000);
            layout.setPadding(pad, pad, pad, pad);

            TextView title = new TextView(this);
            title.setText("已锁定");
            title.setTextSize(32);
            title.setTextColor(0xFFFFFFFF);
            title.setGravity(Gravity.CENTER);
            layout.addView(title);

            TextView hint = new TextView(this);
            hint.setText("时间已到，请输入管理员密码解锁");
            hint.setTextSize(16);
            hint.setTextColor(0xFFAAAAAA);
            hint.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            hp.topMargin = (int) (16 * d + 0.5f);
            layout.addView(hint, hp);

            final EditText pw = new EditText(this);
            pw.setHint("管理员密码");
            pw.setTextSize(20);
            pw.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                    (int) (300 * d + 0.5f),
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            pp.topMargin = (int) (24 * d + 0.5f);
            layout.addView(pw, pp);

            final TextView err = new TextView(this);
            err.setTextSize(14);
            err.setTextColor(0xFFFF4444);
            err.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            ep.topMargin = (int) (8 * d + 0.5f);
            layout.addView(err, ep);

            Button ok = new Button(this);
            ok.setText("解锁");
            ok.setTextSize(20);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                    (int) (200 * d + 0.5f),
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            bp.topMargin = (int) (16 * d + 0.5f);
            layout.addView(ok, bp);

            ok.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (Prefs.checkPassword(TimerService.this, pw.getText().toString())) {
                        // 密码正确：开始新倒计时周期
                        locked = false;
                        setTimer(Prefs.getPeriodMin(TimerService.this));
                        saveState();
                        updateNotification();
                        hideLockOverlay();
                        showFloat();
                    } else {
                        err.setText("密码错误");
                        pw.setText("");
                    }
                }
            });

            WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    PixelFormat.TRANSLUCENT);
            p.gravity = Gravity.CENTER;
            // 拦截返回键和菜单键
            layout.setFocusableInTouchMode(true);
            layout.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, android.view.KeyEvent event) {
                    if (keyCode == android.view.KeyEvent.KEYCODE_BACK
                            || keyCode == android.view.KeyEvent.KEYCODE_MENU) return true;
                    return false;
                }
            });
            wm.addView(layout, p);
            lockView = layout;
            pw.requestFocus();
        } catch (Exception ignored) {
        }
    }

    private void hideLockOverlay() {
        try {
            if (wm != null && lockView != null) {
                try {
                    wm.removeView(lockView);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        lockView = null;
    }

    private void enforceLock() {
        if (lockView == null) showLockOverlay();
    }

    private void loadState() {
        day = Prefs.sp(this).getString("day", "");
        remainingSec = Prefs.sp(this).getInt("remaining", -1);
        deadline = Prefs.sp(this).getLong("deadline", 0);
        locked = Prefs.sp(this).getBoolean("locked", false);
        String t = Prefs.today();
        if (!t.equals(day) || remainingSec < 0) {
            day = t;
            locked = false;
            setTimer(Prefs.getPeriodMin(this));
        } else if (!locked && deadline > 0) {
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
                .apply();
    }

    private void setTimer(int minutes) {
        remainingSec = minutes * 60;
        deadline = System.currentTimeMillis() + remainingSec * 1000L;
    }

    private void setLauncherIcon(boolean show) {
        try {
            android.content.pm.PackageManager pm = getPackageManager();
            android.content.ComponentName cn = new android.content.ComponentName(
                    this, MainActivity.class);
            pm.setComponentEnabledSetting(cn,
                    show ? android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                         : android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP);
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
