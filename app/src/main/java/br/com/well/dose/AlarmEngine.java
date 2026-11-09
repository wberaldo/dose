package br.com.well.dose;

import android.app.*;
import android.content.*;
import android.os.*;
import java.util.*;

/** One durable alarm dispatcher groups simultaneous doses and survives process death. */
public final class AlarmEngine {
  public static final Object LOCK = new Object();

  public static boolean exact(Context c) {
    return Build.VERSION.SDK_INT < 31
        || ((AlarmManager) c.getSystemService(Context.ALARM_SERVICE)).canScheduleExactAlarms();
  }

  public static void refresh(Context c) {
    synchronized (LOCK) {
      Store.get(c).maintain(System.currentTimeMillis());
      cancelStale(c);
      schedule(c);
    }
  }

  public static void tick(Context c) {
    synchronized (LOCK) {
      long now = System.currentTimeMillis();
      Store db = Store.get(c);
      db.maintain(now);
      cancelStale(c);
      List<Dose> due = db.claimDue(c, now);
      boolean hasAlert = false;
      boolean hasDoseAlert = false;
      for (Dose d : due) {
        if (Alerts.allowed(c)) {
          Alerts.show(c, d, true);
          hasAlert = true;
          hasDoseAlert = true;
        } else
          Prefs.error(
              c,
              "Um lembrete venceu com as notificações bloqueadas. Ative-as na Central de alertas.");
      }
      long test = Prefs.get(c).getLong("test_at", 0);
      boolean testDue = test > 0 && test <= now;
      if (testDue) {
        Prefs.get(c).edit().remove("test_at").commit();
        if (Alerts.allowed(c)) {
          Alerts.test(c);
          hasAlert = true;
        }
      }
      if (hasAlert) {
        Prefs.get(c).edit().putLong("last_delivery", now).apply();
        Intent service =
            new Intent(c, RingingService.class).setAction(hasDoseAlert ? "RING" : "TEST");
        try {
          c.startForegroundService(service);
        } catch (RuntimeException e) {
          Prefs.error(
              c,
              "O Android restringiu o alarme sonoro. A notificação foi enviada. Revise a bateria e"
                  + " as permissões.");
        }
      }
      schedule(c);
      c.sendBroadcast(new Intent("br.com.well.dose.CHANGED").setPackage(c.getPackageName()));
    }
  }

  public static void schedule(Context c) {
    AlarmManager manager = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
    PendingIntent pending =
        PendingIntent.getBroadcast(
            c,
            700,
            new Intent(c, AlarmReceiver.class).setAction("DOSE_TICK"),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    PendingIntent maintenance =
        PendingIntent.getBroadcast(
            c,
            702,
            new Intent(c, AlarmReceiver.class).setAction("MAINTENANCE"),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    Store db = Store.get(c);
    long now = System.currentTimeMillis();
    long test = Prefs.get(c).getLong("test_at", 0);
    boolean any = !db.doses("status='pending'", null, "id LIMIT 1").isEmpty();
    boolean active = false;
    for (Medication m : db.medications())
      if (m.enabled) {
        active = true;
        break;
      }
    if (!any && !active && test == 0) {
      manager.cancel(pending);
      manager.cancel(maintenance);
      Prefs.get(c).edit().remove("next_wake").remove("next_alarm").apply();
      return;
    }
    long next = db.nextWake(now);
    if (test > 0)
      next = next > 0 ? Math.min(next, Math.max(now + 1000, test)) : Math.max(now + 1000, test);
    try {
      if (next > 0 && exact(c)) {
        PendingIntent show =
            PendingIntent.getActivity(
                c,
                701,
                new Intent(c, MainActivity.class).putExtra("tab", 3),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        manager.setAlarmClock(new AlarmManager.AlarmClockInfo(next, show), pending);
      } else if (next > 0) manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending);
      else manager.cancel(pending);
      long upkeep = db.nextMaintenance(now);
      manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, upkeep, maintenance);
      Prefs.get(c)
          .edit()
          .putLong("next_alarm", next)
          .putLong("next_wake", next > 0 ? Math.min(next, upkeep) : upkeep)
          .apply();
    } catch (SecurityException e) {
      Prefs.error(c, "Autorize Alarmes e lembretes para recuperar a pontualidade dos avisos.");
    }
  }

  public static void testIn(Context c, int seconds) {
    synchronized (LOCK) {
      // The scheduled test must reach disk before the process may be killed.
      Prefs.get(c).edit().putLong("test_at", System.currentTimeMillis() + seconds * 1000L).commit();
      schedule(c);
    }
  }

  public static void testAction(Context c, String action) {
    if (!"END_TEST".equals(action) && !"TAKE".equals(action) && !"SNOOZE_TEST".equals(action))
      return;
    synchronized (LOCK) {
      Alerts.cancel(c, Alerts.TEST_ID);
      c.stopService(new Intent(c, RingingService.class));
      if ("SNOOZE_TEST".equals(action))
        Prefs.get(c)
            .edit()
            .putLong("test_at", System.currentTimeMillis() + Prefs.snooze(c) * 60000L)
            .commit();
      else Prefs.get(c).edit().remove("test_at").commit();
      schedule(c);
      c.sendBroadcast(new Intent("br.com.well.dose.CHANGED").setPackage(c.getPackageName()));
    }
  }

  public static void action(Context c, long id, String action) {
    synchronized (LOCK) {
      Store db = Store.get(c);
      boolean changed = false;
      try {
        if ("SNOOZE".equals(action))
          changed = db.snooze(id, Prefs.snooze(c), System.currentTimeMillis());
        else
          changed =
              db.resolve(
                  id, "SKIP".equals(action) ? "skipped" : "taken", System.currentTimeMillis());
      } catch (IllegalArgumentException e) {
        Prefs.error(c, e.getMessage());
      }
      if (changed) Alerts.cancel(c, id);
      c.stopService(new Intent(c, RingingService.class));
      refresh(c);
      c.sendBroadcast(new Intent("br.com.well.dose.CHANGED").setPackage(c.getPackageName()));
    }
  }

  public static void cancelStale(Context c) {
    NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
    for (android.service.notification.StatusBarNotification n : nm.getActiveNotifications()) {
      if (n.getId() > 0 && n.getId() < Alerts.SERVICE_ID) {
        Dose d = Store.get(c).dose(n.getId());
        if (d == null || !d.actionable() || d.snoozed) nm.cancel(n.getId());
      }
    }
  }
}
