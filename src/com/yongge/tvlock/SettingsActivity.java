package com.yongge.tvlock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

public class SettingsActivity extends Activity {
    private AlertDialog.Builder darkDialog() {
        // 暗色弹窗
        return new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_settings);

        // 修改倒计时周期
        findViewById(R.id.btn_period).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final EditText et = new EditText(SettingsActivity.this);
                et.setInputType(InputType.TYPE_CLASS_NUMBER);
                et.setHint("分钟数（当前 " + Prefs.getPeriodMin(SettingsActivity.this) + "）");
                et.setTextColor(0xFFFFFFFF);
                et.setHintTextColor(0xFF888888);
                darkDialog()
                        .setTitle("倒计时周期")
                        .setView(et)
                        .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                try {
                                    int min = Integer.parseInt(et.getText().toString().trim());
                                    if (min < 1) min = 1;
                                    if (min > 1440) min = 1440;
                                    Prefs.setPeriodMin(SettingsActivity.this, min);
                                    // 立即按新周期重置（Intent 直接传值，避免多进程 prefs 不同步）
                                    Intent ri = new Intent(SettingsActivity.this, TimerService.class).setAction("RESET");
                                    ri.putExtra("period_min", min);
                                    startService(ri);
                                    Toast.makeText(SettingsActivity.this, "已设为 " + min + " 分钟并重置", Toast.LENGTH_SHORT).show();
                                } catch (Exception e) {
                                    Toast.makeText(SettingsActivity.this, "输入无效", Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        });

        // 修改管理员密码
        findViewById(R.id.btn_password).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LinearLayout ll = new LinearLayout(SettingsActivity.this);
                ll.setOrientation(LinearLayout.VERTICAL);
                int pad = (int) (20 * getResources().getDisplayMetrics().density + 0.5f);
                ll.setPadding(pad, pad, pad, pad);
                final EditText oldPw = new EditText(SettingsActivity.this);
                oldPw.setHint("原密码");
                oldPw.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
                oldPw.setTextColor(0xFFFFFFFF);
                oldPw.setHintTextColor(0xFF888888);
                final EditText newPw = new EditText(SettingsActivity.this);
                newPw.setHint("新密码");
                newPw.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
                newPw.setTextColor(0xFFFFFFFF);
                newPw.setHintTextColor(0xFF888888);
                ll.addView(oldPw);
                ll.addView(newPw);
                darkDialog()
                        .setTitle("修改管理员密码")
                        .setView(ll)
                        .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                if (Prefs.checkPassword(SettingsActivity.this, oldPw.getText().toString())) {
                                    String np = newPw.getText().toString().trim();
                                    if (np.isEmpty()) {
                                        Toast.makeText(SettingsActivity.this, "新密码不能为空", Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                    Prefs.setPassword(SettingsActivity.this, np);
                                    // 通知主进程更新密码（多进程 prefs 不同步）
                                    Intent pi = new Intent(SettingsActivity.this, TimerService.class).setAction("SET_PASSWORD");
                                    pi.putExtra("password", np);
                                    startService(pi);
                                    Toast.makeText(SettingsActivity.this, "密码已修改", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(SettingsActivity.this, "原密码错误", Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        });

        // 重置倒计时状态
        findViewById(R.id.btn_reset).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 从 :ui 进程读最新值，直接传给主进程
                int min = Prefs.getPeriodMin(SettingsActivity.this);
                Intent ri = new Intent(SettingsActivity.this, TimerService.class).setAction("RESET");
                ri.putExtra("period_min", min);
                startService(ri);
                Toast.makeText(SettingsActivity.this, "倒计时已重置", Toast.LENGTH_SHORT).show();
            }
        });

        // 立即锁屏
        findViewById(R.id.btn_locknow).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startService(new Intent(SettingsActivity.this, TimerService.class).setAction("LOCK_NOW"));
                Toast.makeText(SettingsActivity.this, "已锁定", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }
}
