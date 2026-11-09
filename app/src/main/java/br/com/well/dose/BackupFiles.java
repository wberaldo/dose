package br.com.well.dose;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Portable logical backups; provider I/O never runs on the activity's main thread. */
public final class BackupFiles {
  public static final int MAX_BYTES = 20_000_000;
  private static final ExecutorService IO = Executors.newSingleThreadExecutor();
  private static final String[] BOOLEANS = {
    "ring", "vibrate", "fullscreen", "private", "force_sound", "alarm_profile_130"
  };
  private static final String[] INTS = {
    "repeat_minutes", "repeat_count", "snooze_minutes", "alarm_min_volume"
  };

  private BackupFiles() {}

  public static byte[] snapshot(Context c) throws Exception {
    synchronized (AlarmEngine.LOCK) {
      JSONObject root = Store.get(c).exportJson();
      JSONObject settings = new JSONObject();
      Map<String, ?> values = Prefs.get(c).getAll();
      if (values.containsKey("theme")) settings.put("theme", values.get("theme"));
      for (String key : BOOLEANS) if (values.containsKey(key)) settings.put(key, values.get(key));
      for (String key : INTS) if (values.containsKey(key)) settings.put(key, values.get(key));
      root.put("settings", settings);
      byte[] bytes = root.toString().getBytes("UTF-8");
      if (bytes.length > MAX_BYTES) throw new IOException("O backup ultrapassa o limite de 20 MB.");
      return bytes;
    }
  }

  public static byte[] read(InputStream input) throws IOException {
    if (input == null) throw new IOException("Não foi possível abrir o arquivo.");
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    byte[] chunk = new byte[8192];
    int n;
    while ((n = input.read(chunk)) != -1) {
      if (buffer.size() + n > MAX_BYTES) throw new IOException("Backup muito grande.");
      buffer.write(chunk, 0, n);
    }
    return buffer.toByteArray();
  }

  private static JSONObject settings(JSONObject root) throws Exception {
    JSONObject settings = root.optJSONObject("settings");
    if (settings == null) return new JSONObject();
    if (settings.has("theme")
        && !Arrays.asList("system", "light", "dark").contains(settings.getString("theme")))
      throw new IOException("Aparência inválida no backup.");
    for (String key : BOOLEANS)
      if (settings.has(key) && !(settings.get(key) instanceof Boolean))
        throw new IOException("Configuração inválida no backup.");
    for (String key : INTS) {
      if (!settings.has(key)) continue;
      Object value = settings.get(key);
      if (!(value instanceof Integer) && !(value instanceof Long))
        throw new IOException("Configuração inválida no backup.");
      long number = ((Number) value).longValue();
      long min = key.equals("repeat_count") ? 0 : 1;
      long max = key.equals("repeat_count") ? 10 : key.equals("alarm_min_volume") ? 100 : 1440;
      if (number < min || number > max) throw new IOException("Configuração inválida no backup.");
    }
    return settings;
  }

  public static void restore(Context c, byte[] bytes) throws Exception {
    if (bytes.length > MAX_BYTES) throw new IOException("Backup muito grande.");
    JSONObject root = new JSONObject(new String(bytes, "UTF-8"));
    JSONObject settings = settings(root);
    synchronized (AlarmEngine.LOCK) {
      Store.get(c).importJson(root);
      SharedPreferences.Editor edit = Prefs.get(c).edit();
      if (settings.has("theme")) edit.putString("theme", settings.getString("theme"));
      for (String key : BOOLEANS)
        if (settings.has(key)) edit.putBoolean(key, settings.getBoolean(key));
      for (String key : INTS) if (settings.has(key)) edit.putInt(key, settings.getInt(key));
      // An installed backup must not reapply this older release's initial profile next update.
      edit.putBoolean("alarm_profile_130", true).commit();
      Store.get(c).rezone(System.currentTimeMillis());
      Store.get(c)
          .getWritableDatabase()
          .execSQL(
              "UPDATE doses SET next_alert=0,last_alert=0,snoozed=0 WHERE status='pending' AND"
                  + " scheduled<=?",
              new Object[] {System.currentTimeMillis()});
      AlarmAudio.releaseVolume(c);
      Prefs.get(c)
          .edit()
          .remove("test_at")
          .remove("test_visible")
          .remove("ringing_until")
          .remove("next_alarm")
          .remove("next_wake")
          .remove("last_audio")
          .remove("last_delivery")
          .remove("last_fullscreen")
          .remove("last_error")
          .remove("last_error_at")
          .commit();
      ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).cancelAll();
      RingingService.stopSound(c);
      AlarmEngine.refresh(c);
    }
  }

  public static Intent createIntent() {
    return new Intent(Intent.ACTION_CREATE_DOCUMENT)
        .setType("application/json")
        .addCategory(Intent.CATEGORY_OPENABLE)
        .putExtra(Intent.EXTRA_TITLE, "Dose-backup-" + java.time.LocalDate.now() + ".json");
  }

  public static Intent openIntent() {
    return new Intent(Intent.ACTION_OPEN_DOCUMENT)
        .setType("*/*")
        .addCategory(Intent.CATEGORY_OPENABLE);
  }

  private static String name(Context c, Uri uri) {
    try (Cursor cursor =
        c.getContentResolver()
            .query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
      if (cursor != null && cursor.moveToFirst() && cursor.getString(0) != null)
        return cursor.getString(0);
    } catch (RuntimeException ignored) {
    }
    return "Arquivo de backup";
  }

  public static void transfer(Activity activity, Uri uri, boolean restore, Runnable completed) {
    Context c = activity.getApplicationContext();
    Ui.toast(activity, restore ? "Restaurando backup…" : "Salvando backup…");
    IO.execute(
        () -> {
          String message;
          try {
            if (restore) {
              try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                restore(c, read(in));
              }
              CloudBackup.prefs(c)
                  .edit()
                  .putLong("manual_restore_at", System.currentTimeMillis())
                  .apply();
              CloudBackup.changed(c);
              message = "Backup restaurado.";
            } else {
              byte[] bytes = snapshot(c);
              try (OutputStream out = c.getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) throw new IOException("O local escolhido não abriu o arquivo.");
                out.write(bytes);
              }
              CloudBackup.prefs(c)
                  .edit()
                  .putLong("manual_at", System.currentTimeMillis())
                  .putString("manual_name", name(c, uri))
                  .apply();
              message = "Arquivo de backup salvo.";
            }
          } catch (Exception error) {
            message =
                "Não foi possível concluir: "
                    + (error.getMessage() == null
                        ? "confira o arquivo e o local escolhido."
                        : error.getMessage());
          }
          String result = message;
          new Handler(Looper.getMainLooper())
              .post(
                  () -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    if (restore) Ui.prepare(activity);
                    Ui.toast(activity, result);
                    completed.run();
                  });
        });
  }
}
