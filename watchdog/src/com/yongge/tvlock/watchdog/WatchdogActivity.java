package com.yongge.tvlock.watchdog;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.security.MessageDigest;

public class WatchdogActivity extends Activity {
    private static final String PREFS = "watchdog";
    private static final String DEFAULT_PW = "112233"; // 与主 App 默认密码一致

    private LinearLayout root;
    private boolean authed = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        int pad = dp(24);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(0xFF222222);
        setContentView(root);
        showPasswordPrompt();
    }

    private void showPasswordPrompt() {
        root.removeAllViews();
        TextView t = new TextView(this);
        t.setText("看门狗管理\n请输入管理员密码");
        t.setTextSize(20);
        t.setTextColor(0xFFFFFFFF);
        t.setGravity(Gravity.CENTER);
        root.addView(t);

        final EditText pw = new EditText(this);
        pw.setHint("管理员密码");
        pw.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pw.setTextColor(0xFFFFFFFF);
        pw.setHintTextColor(0xFF888888);
        pw.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(300),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(24);
        root.addView(pw, lp);

        final TextView err = new TextView(this);
        err.setTextColor(0xFFFF4444);
        err.setGravity(Gravity.CENTER);
        root.addView(err);

        Button ok = new Button(this);
        ok.setBackgroundResource(R.drawable.btn_selector);
        ok.setTextColor(0xFFFFFFFF);
        ok.setText("进入");
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkPassword(pw.getText().toString())) {
                    authed = true;
                    showControls();
                } else {
                    err.setText("密码错误");
                    pw.setText("");
                }
            }
        });
        root.addView(ok);
    }

    private void showControls() {
        root.removeAllViews();
        TextView t = new TextView(this);
        t.setText("看门狗控制");
        t.setTextSize(22);
        t.setTextColor(0xFFFFFFFF);
        t.setGravity(Gravity.CENTER);
        root.addView(t);

        boolean enabled = isEnabled();
        TextView st = new TextView(this);
        st.setText("状态：" + (enabled ? "运行中" : "已停用"));
        st.setTextSize(18);
        st.setTextColor(0xFFFFFFFF);
        st.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(16);
        root.addView(st, lp);

        Button toggle = new Button(this);
        toggle.setBackgroundResource(R.drawable.btn_selector);
        toggle.setTextColor(0xFFFFFFFF);
        toggle.setText(enabled ? "停用看门功能" : "启用看门功能");
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean cur = isEnabled();
                setEnabled(!cur);
                if (!cur) {
                    // 启用：拉起服务
                    startService(new Intent(WatchdogActivity.this, WatchdogService.class));
                    Toast.makeText(WatchdogActivity.this, "看门功能已启用", Toast.LENGTH_SHORT).show();
                } else {
                    // 停用：停掉服务
                    stopService(new Intent(WatchdogActivity.this, WatchdogService.class));
                    Toast.makeText(WatchdogActivity.this, "看门功能已停用", Toast.LENGTH_SHORT).show();
                }
                showControls();
            }
        });
        root.addView(toggle);

        // 隐藏/显示主程序图标
        Button hideIcon = new Button(this);
        hideIcon.setBackgroundResource(R.drawable.btn_selector);
        hideIcon.setTextColor(0xFFFFFFFF);
        hideIcon.setText("隐藏主程序图标");
        hideIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendIconCommand("HIDE_ICON");
                Toast.makeText(WatchdogActivity.this, "已发送隐藏指令", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(hideIcon);

        Button showIcon = new Button(this);
        showIcon.setBackgroundResource(R.drawable.btn_selector);
        showIcon.setTextColor(0xFFFFFFFF);
        showIcon.setText("显示主程序图标");
        showIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendIconCommand("SHOW_ICON");
                Toast.makeText(WatchdogActivity.this, "已发送显示指令", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(showIcon);

        // 修改密码
        Button chpw = new Button(this);
        chpw.setBackgroundResource(R.drawable.btn_selector);
        chpw.setTextColor(0xFFFFFFFF);
        chpw.setText("修改管理员密码");
        chpw.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showChangePassword();
            }
        });
        root.addView(chpw);
    }

    private void showChangePassword() {
        root.removeAllViews();
        TextView t = new TextView(this);
        t.setText("修改管理员密码");
        t.setTextSize(20);
        t.setTextColor(0xFFFFFFFF);
        t.setGravity(Gravity.CENTER);
        root.addView(t);

        final EditText oldPw = new EditText(this);
        oldPw.setHint("原密码");
        oldPw.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        oldPw.setTextColor(0xFFFFFFFF);
        oldPw.setHintTextColor(0xFF888888);
        root.addView(oldPw);

        final EditText newPw = new EditText(this);
        newPw.setHint("新密码");
        newPw.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        newPw.setTextColor(0xFFFFFFFF);
        newPw.setHintTextColor(0xFF888888);
        root.addView(newPw);

        Button ok = new Button(this);
        ok.setBackgroundResource(R.drawable.btn_selector);
        ok.setTextColor(0xFFFFFFFF);
        ok.setText("确定");
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkPassword(oldPw.getText().toString())) {
                    String np = newPw.getText().toString().trim();
                    if (!np.isEmpty()) {
                        setPassword(np);
                        Toast.makeText(WatchdogActivity.this, "密码已修改", Toast.LENGTH_SHORT).show();
                        showControls();
                    } else {
                        Toast.makeText(WatchdogActivity.this, "新密码不能为空", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(WatchdogActivity.this, "原密码错误", Toast.LENGTH_SHORT).show();
                }
            }
        });
        root.addView(ok);

        Button back = new Button(this);
        back.setBackgroundResource(R.drawable.btn_selector);
        back.setTextColor(0xFFFFFFFF);
        back.setText("返回");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showControls();
            }
        });
        root.addView(back);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    // --- 密码与开关存取 ---
    public static boolean checkPassword(Context c, String input) {
        if (input == null) return false;
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String saved = sp.getString("pw_hash", sha256(DEFAULT_PW));
        return saved.equals(sha256(input));
    }

    private boolean checkPassword(String input) {
        return checkPassword(this, input);
    }

    private void setPassword(String pw) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("pw_hash", sha256(pw)).apply();
    }

    public static boolean isEnabled(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean("enabled", true);
    }

    private boolean isEnabled() {
        return isEnabled(this);
    }

    private void setEnabled(boolean e) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean("enabled", e).apply();
    }

    /** 发送图标显示/隐藏指令给主程序 */
    private void sendIconCommand(String action) {
        try {
            Intent i = new Intent();
            i.setComponent(new android.content.ComponentName(
                    "com.yongge.tvlock",
                    "com.yongge.tvlock.TimerService"));
            i.setAction(action);
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
        } catch (Exception e) {
            Toast.makeText(this, "主程序未安装", Toast.LENGTH_SHORT).show();
        }
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            return s;
        }
    }

    @Override
    public void onBackPressed() {
        if (authed) {
            showPasswordPrompt();
            authed = false;
        } else {
            super.onBackPressed();
        }
    }
}
