package com.yongge.tvlock;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * 倒计时弹窗服务，运行在独立进程 :popup。
 * MIUI 猎杀悬浮窗时只会杀掉本进程，主进程的 TimerService 不受影响。
 * 每次启动显示 10 秒后自动销毁。
 */
public class PopupService extends Service {
    private WindowManager wm;
    private TextView popupView;
    private Handler handler;

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String text = intent != null ? intent.getStringExtra("text") : "";
        if (text == null || text.isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        showPopup(text);
        // 10 秒后销毁窗口并自杀
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                removePopup();
                stopSelf();
            }
        }, 10000);
        return START_NOT_STICKY;
    }

    private void showPopup(String text) {
        try {
            removePopup();
            wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            popupView = new TextView(this);
            popupView.setTextSize(22);
            popupView.setTextColor(0xFFFFFFFF);
            popupView.setBackgroundColor(0xCC000000);
            float d = getResources().getDisplayMetrics().density;
            int pad = (int) (12 * d + 0.5f);
            popupView.setPadding(pad, (int) (8 * d + 0.5f), pad, (int) (8 * d + 0.5f));
            popupView.setText(text);
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
            wm.addView(popupView, p);
        } catch (Exception ignored) {
        }
    }

    private void removePopup() {
        try {
            if (wm != null && popupView != null) {
                wm.removeView(popupView);
            }
        } catch (Exception ignored) {
        }
        popupView = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null) handler.removeCallbacksAndMessages(null);
        removePopup();
        // 确保进程退出，不留残留
        try {
            android.os.Process.killProcess(android.os.Process.myPid());
        } catch (Exception ignored) {
        }
    }
}
