package br.com.well.dose;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.*;
import android.os.*;
import android.provider.Settings;
import java.time.*;

public final class Alerts {
  public static final String CHANNEL = "dose_alarms_v1", INFO = "dose_status_v1";
  public static final int SERVICE_ID = 900000001, TEST_ID = 900000002;

  public static void channels(Context c) {
    NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
    NotificationChannel alarm =
        new NotificationChannel(
            CHANNEL, "Alarmes de medicamentos", NotificationManager.IMPORTANCE_HIGH);
    alarm.setDescription(
        "Horários das doses, adiamentos e repetições. Use som de alarme, vibração e exibição na"
            + " tela bloqueada.");
    alarm.setSound(
        android.net.Uri.parse("android.resource://" + c.getPackageName() + "/raw/dose_alarm"),
        new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build());
    alarm.enableVibration(true);
    alarm.setVibrationPattern(new long[] {0, 400, 220, 400, 220, 650});
    alarm.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
    alarm.setShowBadge(true);
    if (nm.isNotificationPolicyAccessGranted() && Prefs.get(c).getBoolean("force_sound", true))
      alarm.setBypassDnd(true);
    nm.createNotificationChannel(alarm);
    NotificationChannel info =
        new NotificationChannel(INFO, "Estado do alarme", NotificationManager.IMPORTANCE_LOW);
    info.setSound(null, null);
    info.enableVibration(false);
    nm.createNotificationChannel(info);
  }

  public static boolean allowed(Context c) {
    channels(c);
    NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
    return (Build.VERSION.SDK_INT < 33
            || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED)
        && nm.areNotificationsEnabled()
        && nm.getNotificationChannel(CHANNEL).getImportance()
            != NotificationManager.IMPORTANCE_NONE;
  }

  public static boolean fullScreenAllowed(Context c) {
    return Build.VERSION.SDK_INT < 34
        || ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE))
            .canUseFullScreenIntent();
  }

  public static boolean overOtherApps(Context c) {
    return Settings.canDrawOverlays(c);
  }

  public static Intent openIntent(Context c, long id) {
    return new Intent(c, AlarmActivity.class)
        .putExtra("dose_id", id)
        .setData(android.net.Uri.parse("dose://open/" + id))
        .addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
  }

  public static void openFullScreen(Context c, long id) {
    if (!Prefs.fullscreen(c) || !overOtherApps(c)) return;
    try {
      c.startActivity(openIntent(c, id));
    } catch (RuntimeException e) {
      Prefs.error(
          c,
          "O Android impediu abrir a tela de alarme. Confira a permissão de aparecer sobre outros"
              + " apps.");
    }
  }

  private static PendingIntent action(Context c, long id, String action) {
    Intent i =
        new Intent(c, ActionReceiver.class)
            .setAction(action)
            .setData(android.net.Uri.parse("dose://dose/" + id + "/" + action))
            .putExtra("dose_id", id);
    return PendingIntent.getBroadcast(
        c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }

  public static Notification notification(Context c, Dose d, boolean ringing) {
    boolean test = d == null;
    long id = test ? -1 : d.id;
    String name = test ? "Seu lembrete está funcionando" : d.name;
    String time =
        test
            ? "Alarme de teste"
            : Instant.ofEpochMilli(d.scheduled)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
                .format(ScheduleRules.CLOCK);
    String text =
        test
            ? "Toque para adiar ou encerrar. Nenhuma dose será registrada."
            : d.detail + " • previsto para " + time;
    String more = test ? text : text + (d.notes.isEmpty() ? "" : "\n" + d.notes);
    if (!test) {
      Medication med = Store.get(c).medication(d.medId);
      if (med != null && med.stock >= 0 && med.stock <= 5)
        more += "\nEstoque baixo: " + ScheduleRules.number(med.stock) + " " + med.unit + ".";
    }
    Intent open = openIntent(c, id);
    Bundle options = null;
    if (Build.VERSION.SDK_INT >= 35) {
      ActivityOptions activityOptions = ActivityOptions.makeBasic();
      activityOptions.setPendingIntentCreatorBackgroundActivityStartMode(
          Build.VERSION.SDK_INT >= 36
              ? ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
              : ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
      options = activityOptions.toBundle();
    }
    PendingIntent content =
        PendingIntent.getActivity(
            c, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE, options);
    Notification publicVersion =
        new Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Dose • hora do seu cuidado")
            .setContentText("Desbloqueie para ver o medicamento.")
            .build();
    Notification.Builder b =
        new Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(name)
            .setContentText(text)
            .setStyle(new Notification.BigTextStyle().bigText(more))
            .setColor(test ? 0xFF137C70 : d.color)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(
                !test && Prefs.privateAlarm(c)
                    ? Notification.VISIBILITY_PRIVATE
                    : Notification.VISIBILITY_PUBLIC)
            .setContentIntent(content)
            .setOngoing(true)
            .setAutoCancel(false)
            .setShowWhen(true)
            .setWhen(test ? System.currentTimeMillis() : d.scheduled)
            .setOnlyAlertOnce(!ringing);
    if (!test) {
      b.setPublicVersion(publicVersion);
      b.setLargeIcon(MedicineIcon.bitmap(d.icon, d.color, d.secondaryColor, Ui.dp(c, 64)));
    }
    b.setSubText(test ? "Alarme de teste" : "Lembrete de medicamento");
    Notification.Action.Builder take =
        new Notification.Action.Builder(
            null, test ? "Encerrar teste" : "Tomei", action(c, id, test ? "END_TEST" : "TAKE"));
    if (!test && Build.VERSION.SDK_INT >= 31 && Prefs.privateAlarm(c))
      take.setAuthenticationRequired(true);
    b.addAction(take.build());
    b.addAction(
        new Notification.Action.Builder(
                null,
                "Adiar " + Prefs.snooze(c) + " min",
                action(c, id, test ? "SNOOZE_TEST" : "SNOOZE"))
            .build());
    if (Prefs.fullscreen(c) && fullScreenAllowed(c)) b.setFullScreenIntent(content, true);
    if (Build.VERSION.SDK_INT >= 31)
      b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
    return b.build();
  }

  public static void show(Context c, Dose d, boolean ringing) {
    channels(c);
    try {
      ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE))
          .notify((int) d.id, notification(c, d, ringing));
    } catch (SecurityException e) {
      Prefs.error(c, "As notificações estão bloqueadas.");
    }
  }

  public static void test(Context c) {
    channels(c);
    Prefs.get(c).edit().putBoolean("test_visible", true).commit();
    ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE))
        .notify(TEST_ID, notification(c, null, true));
  }

  public static void cancel(Context c, long id) {
    ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel((int) id);
  }
}
