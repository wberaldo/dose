package br.com.well.dose;

import android.content.*;

public final class Prefs {
  public static android.content.SharedPreferences get(Context c) {
    return c.getSharedPreferences("dose_settings", Context.MODE_PRIVATE);
  }

  public static int interval(Context c) {
    return get(c).getInt("repeat_minutes", 5);
  }

  public static int repeats(Context c) {
    return get(c).getInt("repeat_count", 3);
  }

  public static int snooze(Context c) {
    return get(c).getInt("snooze_minutes", 10);
  }

  public static boolean ring(Context c) {
    return get(c).getBoolean("ring", true);
  }

  public static boolean vibrate(Context c) {
    return get(c).getBoolean("vibrate", true);
  }

  public static boolean fullscreen(Context c) {
    return get(c).getBoolean("fullscreen", true);
  }

  public static void error(Context c, String text) {
    get(c)
        .edit()
        .putString("last_error", text)
        .putLong("last_error_at", System.currentTimeMillis())
        .apply();
  }
}
