package com.yongge.tvlock;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

public class AdminReceiver extends DeviceAdminReceiver {
    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        return "停用设备管理器后，屏幕计时锁定的卸载保护将失效";
    }
}
