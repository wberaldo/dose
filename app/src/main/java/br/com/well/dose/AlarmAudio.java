package br.com.well.dose;

import android.app.*;
import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.Build;

/** Alarm-stream playback settings; no changes to the phone's ringer mode or global DND. */
public final class AlarmAudio {
  public static final String RESTORE = "br.com.well.dose.RESTORE_ALARM_VOLUME";
  private static final String ORIGINAL = "alarm_volume_original",
      APPLIED = "alarm_volume_applied",
      UNTIL = "alarm_volume_until";

  private AlarmAudio() {}

  public static AudioAttributes attributes() {
    return new AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build();
  }

  public static Uri builtIn(Context c) {
    return Uri.parse("android.resource://" + c.getPackageName() + "/raw/dose_alarm");
  }

  public static boolean dndAllowsAlarms(Context c) {
    NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
    int filter = nm.getCurrentInterruptionFilter();
    if (filter == NotificationManager.INTERRUPTION_FILTER_NONE) return false;
    if (filter != NotificationManager.INTERRUPTION_FILTER_PRIORITY) return true;
    NotificationManager.Policy policy =
        Build.VERSION.SDK_INT >= 30
            ? nm.getConsolidatedNotificationPolicy()
            : nm.getNotificationPolicy();
    return (policy.priorityCategories & NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS) != 0;
  }

  public static int minimumPercent(Context c) {
    return Math.max(1, Math.min(100, Prefs.get(c).getInt("alarm_min_volume", 70)));
  }

  private static PendingIntent recovery(Context c) {
    return PendingIntent.getBroadcast(
        c,
        704,
        new Intent(c, AlarmReceiver.class).setAction(RESTORE),
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }

  /** Persist before raising volume, so process death can still be recovered. */
  public static synchronized void acquireVolume(Context c, long until) {
    if (!Prefs.get(c).getBoolean("force_sound", true)) return;
    AudioManager audio = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
    int current = audio.getStreamVolume(AudioManager.STREAM_ALARM);
    int minimum =
        Math.max(
            1,
            Math.round(
                audio.getStreamMaxVolume(AudioManager.STREAM_ALARM) * minimumPercent(c) / 100f));
    android.content.SharedPreferences p = Prefs.get(c);
    boolean leased = p.contains(ORIGINAL);
    int previous =
        leased && current == p.getInt(APPLIED, -1) ? p.getInt(ORIGINAL, current) : current;
    int applied = Math.max(current, minimum);
    if (applied == current && !leased) return;
    p.edit().putInt(ORIGINAL, previous).putInt(APPLIED, applied).putLong(UNTIL, until).commit();
    try {
      audio.setStreamVolume(AudioManager.STREAM_ALARM, applied, 0);
      AlarmManager manager = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
      if (AlarmEngine.exact(c))
        manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, until, recovery(c));
      else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, until, recovery(c));
    } catch (RuntimeException e) {
      Prefs.error(c, "O Android bloqueou o volume do alarme. Permita alarmes no Não perturbe.");
    }
  }

  /** Preserve an explicit volume adjustment made by the user while the alarm was playing. */
  public static synchronized void releaseVolume(Context c) {
    android.content.SharedPreferences p = Prefs.get(c);
    if (!p.contains(ORIGINAL)) return;
    AudioManager audio = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
    try {
      if (audio.getStreamVolume(AudioManager.STREAM_ALARM) == p.getInt(APPLIED, -1))
        audio.setStreamVolume(AudioManager.STREAM_ALARM, p.getInt(ORIGINAL, 0), 0);
      p.edit().remove(ORIGINAL).remove(APPLIED).remove(UNTIL).commit();
      ((AlarmManager) c.getSystemService(Context.ALARM_SERVICE)).cancel(recovery(c));
    } catch (RuntimeException e) {
      Prefs.error(c, "O Android impediu restaurar o volume anterior do alarme.");
    }
  }

  public static void recoverExpired(Context c) {
    if (Prefs.get(c).getLong(UNTIL, Long.MAX_VALUE) <= System.currentTimeMillis()) releaseVolume(c);
  }
}
