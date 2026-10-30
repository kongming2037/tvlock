package com.yongge.tvlock;

import android.content.Context;
import android.content.SharedPreferences;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Prefs {
    private static final String NAME = "tvlock";
    private static final String DEFAULT_PW = "112233";

    public static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    /** 倒计时周期（分钟），默认 1 分钟 */
    public static int getPeriodMin(Context c) {
        return sp(c).getInt("period_min", 1);
    }

    public static void setPeriodMin(Context c, int min) {
        sp(c).edit().putInt("period_min", min).apply();
    }

    /** 格式化秒数为 H:MM:SS */
    public static String fmt(int s) {
        if (s < 0) s = 0;
        int h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h + ":" + (m < 10 ? "0" : "") + m + ":" + (sec < 10 ? "0" : "") + sec;
    }

    /** 密码 SHA-256 校验 */
    public static boolean checkPassword(Context c, String input) {
        if (input == null) return false;
        String saved = sp(c).getString("pw_hash", sha256(DEFAULT_PW));
        return saved.equals(sha256(input));
    }

    public static void setPassword(Context c, String pw) {
        sp(c).edit().putString("pw_hash", sha256(pw)).apply();
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
}
