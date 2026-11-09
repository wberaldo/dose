package br.com.well.dose;

import android.app.backup.*;
import android.content.*;
import android.util.AtomicFile;
import java.io.*;

/** Opt-in Android account backup. No Drive API tokens or account credentials enter the app. */
public final class CloudBackup {
  private CloudBackup() {}

  public static SharedPreferences prefs(Context c) {
    return c.getSharedPreferences("dose_cloud_backup", Context.MODE_PRIVATE);
  }

  public static boolean enabled(Context c) {
    return prefs(c).getBoolean("enabled", false);
  }

  public static boolean eligible(boolean enabled, int sdk, int transportFlags) {
    return enabled
        && sdk >= 28
        && (transportFlags
                & (BackupAgent.FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED
                    | BackupAgent.FLAG_DEVICE_TO_DEVICE_TRANSFER))
            != 0;
  }

  public static File payload(Context c) {
    return new File(c.getFilesDir(), "cloud_backup/backup.json");
  }

  public static File prepare(Context c) throws Exception {
    byte[] bytes = BackupFiles.snapshot(c);
    File file = payload(c);
    if (!file.getParentFile().isDirectory() && !file.getParentFile().mkdirs())
      throw new IOException("Não foi possível preparar o backup.");
    AtomicFile atomic = new AtomicFile(file);
    FileOutputStream out = atomic.startWrite();
    try {
      out.write(bytes);
      atomic.finishWrite(out);
    } catch (IOException error) {
      atomic.failWrite(out);
      throw error;
    }
    return file;
  }

  public static void setEnabled(Context c, boolean value) {
    prefs(c).edit().putBoolean("enabled", value).remove("error").commit();
    if (!value) new AtomicFile(payload(c)).delete();
    changed(c);
  }

  public static void changed(Context c) {
    if (!enabled(c)) return;
    if (System.currentTimeMillis() - prefs(c).getLong("requested_at", 0) < 60000) return;
    try {
      new BackupManager(c).dataChanged();
      prefs(c).edit().putLong("requested_at", System.currentTimeMillis()).apply();
    } catch (RuntimeException error) {
      prefs(c)
          .edit()
          .putString("error", "Confira se o backup está ativado nas configurações do Android.")
          .apply();
    }
  }

  public static void restore(Context c) throws Exception {
    File file = payload(c);
    if (!file.isFile()) return;
    try (InputStream in = new FileInputStream(file)) {
      BackupFiles.restore(c, BackupFiles.read(in));
    }
    prefs(c)
        .edit()
        .putBoolean("enabled", true)
        .putLong("restored_at", System.currentTimeMillis())
        .remove("error")
        .commit();
  }
}
