package com.yongge.tvlock;

import android.content.Context;
import android.content.SharedPreferences;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Prefs {
    public static final String NAME = "tvlock";
    public static final String DEFAULT_PW = "112233";

    public static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format(Locale.US, "%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "x" + s;
        }
    }

    public static boolean checkPassword(Context c, String pw) {
        String h = sp(c).getString("admin_hash", null);
        if (h == null) h = sha256(DEFAULT_PW);
        return pw != null && h.equals(sha256(pw));
    }

    public static void setPassword(Context c, String pw) {
        sp(c).edit().putString("admin_hash", sha256(pw)).apply();
    }

    public static int getDailyLimitMin(Context c) {
        return sp(c).getInt("daily_limit_min", 120);
    }

    public static void setDailyLimitMin(Context c, int m) {
        sp(c).edit().putInt("daily_limit_min", m).apply();
    }

    public static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    public static String fmt(int s) {
        if (s < 0) s = 0;
        int h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h + ":" + (m < 10 ? "0" : "") + m + ":" + (sec < 10 ? "0" : "") + sec;
    }
}
