package br.com.well.dose;

import android.app.job.*;
import android.content.*;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.util.AtomicFile;
import java.io.*;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** A user-selected document is retained; credentials and broad Drive access are never requested. */
public final class DriveBackup {
  public static final int JOB_ID = 9101;
  public static final long DAY = 24 * 60 * 60 * 1000L;
  private static final int ACCESS =
      Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
  private static final ExecutorService IO = Executors.newSingleThreadExecutor();
  private static final AtomicBoolean BUSY = new AtomicBoolean();

  private DriveBackup() {}

  public interface Operation {
    String run(CancellationSignal signal) throws Exception;
  }

  public interface Finished {
    void accept(boolean success, String message);
  }

  public static SharedPreferences prefs(Context c) {
    return c.getSharedPreferences("dose_drive_backup", Context.MODE_PRIVATE);
  }

  public static Uri destination(Context c) {
    String value = prefs(c).getString("uri", "");
    return value.isEmpty() ? null : Uri.parse(value);
  }

  public static boolean automatic(Context c) {
    return prefs(c).getBoolean("automatic", false);
  }

  public static boolean busy() {
    return BUSY.get();
  }

  public static boolean hasAccess(Context c, Uri uri) {
    if (uri == null || !"content".equals(uri.getScheme())) return false;
    for (UriPermission grant : c.getContentResolver().getPersistedUriPermissions())
      if (uri.equals(grant.getUri()) && grant.isReadPermission() && grant.isWritePermission())
        return true;
    return false;
  }

  public static Intent choose(boolean existing) {
    Intent intent =
        new Intent(existing ? Intent.ACTION_OPEN_DOCUMENT : Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(existing ? "*/*" : "application/json")
            .addFlags(ACCESS | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
    if (!existing) intent.putExtra(Intent.EXTRA_TITLE, "Dose-backup-automatico.json");
    return intent;
  }

  public static boolean submit(
      Context context, CancellationSignal signal, Operation operation, Finished finished) {
    if (!BUSY.compareAndSet(false, true)) return false;
    Context c = context.getApplicationContext();
    IO.execute(
        () -> {
          boolean ok = false;
          String message;
          prefs(c).edit().putLong("attempt_at", System.currentTimeMillis()).apply();
          try {
            signal.throwIfCanceled();
            message = operation.run(signal);
            prefs(c).edit().remove("error").commit();
            ok = true;
          } catch (Exception error) {
            message =
                error instanceof SecurityException
                    ? "O acesso ao arquivo foi perdido. Escolha o destino novamente."
                    : error instanceof OperationCanceledException
                        ? "Backup interrompido. Tente novamente."
                        : "Não foi possível concluir. Confira sua conexão, o arquivo e a conta no"
                            + " Drive e tente novamente.";
            prefs(c).edit().putString("error", message).commit();
          } finally {
            BUSY.set(false);
          }
          boolean success = ok;
          String result = message;
          new Handler(Looper.getMainLooper()).post(() -> finished.accept(success, result));
        });
    return true;
  }

  /** Link only after the candidate document has been checked; keep the old destination on error. */
  public static String configure(
      Context c, Uri uri, int flags, boolean existing, CancellationSignal signal) throws Exception {
    if (uri == null || !"content".equals(uri.getScheme()) || (flags & ACCESS) != ACCESS)
      throw new SecurityException("Sem acesso permanente ao arquivo.");
    Uri old = destination(c);
    boolean previouslyGranted = hasAccess(c, uri);
    c.getContentResolver().takePersistableUriPermission(uri, ACCESS);
    try {
      if (!hasAccess(c, uri)) throw new SecurityException("Sem acesso permanente ao arquivo.");
      String name = displayName(c, uri);
      String provider = providerName(c, uri);
      long exported = 0;
      byte[] bytes;
      if (existing) {
        bytes = read(c, uri, signal);
        JSONObject root = new JSONObject(new String(bytes, "UTF-8"));
        int version = root.optInt("version");
        if (!"dose-backup".equals(root.optString("format")) || version < 1 || version > 4)
          throw new IOException("Arquivo de backup inválido.");
        root.getJSONArray("meds");
        root.getJSONArray("doses");
        exported = root.optLong("exported_at", 0);
      } else {
        bytes = BackupFiles.snapshot(c);
        writeVerified(c, uri, bytes, signal);
      }
      signal.throwIfCanceled();
      SharedPreferences.Editor edit =
          prefs(c)
              .edit()
              .putString("uri", uri.toString())
              .putString("name", name)
              .putString("provider", provider)
              .putBoolean("automatic", false)
              .putLong("saved_at", existing ? 0 : System.currentTimeMillis())
              .putLong("file_exported_at", exported)
              .putBoolean("needs_restore", existing)
              .remove("local_uri")
              .remove("restored_at")
              .remove("error");
      if (!edit.commit()) throw new IOException("Não foi possível guardar o destino.");
      if (existing) new AtomicFile(localCopy(c)).delete();
      else keepLocal(c, bytes);
      ensureScheduled(c);
      if (old != null && !old.equals(uri)) release(c, old);
      return existing
          ? "Arquivo vinculado. Restaure a cópia antes de atualizar o backup."
          : "Backup gravado. Este destino será usado nas próximas vezes.";
    } catch (Exception error) {
      if (!previouslyGranted && !uri.equals(old)) release(c, uri);
      throw error;
    }
  }

  public static String save(Context c, CancellationSignal signal) throws Exception {
    Uri uri = destination(c);
    if (!hasAccess(c, uri)) throw new SecurityException("Destino indisponível.");
    if (prefs(c).getBoolean("needs_restore", false))
      throw new IOException("Confirme como usar o arquivo existente antes de atualizar.");
    byte[] bytes = BackupFiles.snapshot(c);
    writeVerified(c, uri, bytes, signal);
    keepLocal(c, bytes);
    if (uri.equals(destination(c)))
      prefs(c).edit().putLong("saved_at", System.currentTimeMillis()).remove("error").commit();
    return "Backup gravado no destino escolhido.";
  }

  public static String restore(Context c, boolean local, CancellationSignal signal)
      throws Exception {
    byte[] bytes;
    if (local) {
      if (!hasLocalCopy(c)) throw new IOException("Cópia local indisponível.");
      try (InputStream input = new AtomicFile(localCopy(c)).openRead()) {
        bytes = BackupFiles.read(input);
      }
    } else {
      Uri uri = destination(c);
      if (!hasAccess(c, uri)) throw new SecurityException("Destino indisponível.");
      bytes = read(c, uri, signal);
    }
    signal.throwIfCanceled();
    BackupFiles.restore(c, bytes);
    prefs(c)
        .edit()
        .putLong("restored_at", System.currentTimeMillis())
        .putBoolean("needs_restore", false)
        .remove("error")
        .commit();
    return "Backup restaurado. Seus próximos lembretes foram reagendados.";
  }

  public static void useCurrentData(Context c) {
    prefs(c).edit().putBoolean("needs_restore", false).commit();
  }

  public static void setAutomatic(Context c, boolean enabled) {
    if (enabled && (!hasAccess(c, destination(c)) || prefs(c).getBoolean("needs_restore", false)))
      throw new IllegalStateException("Configure o destino e escolha os dados primeiro.");
    prefs(c).edit().putBoolean("automatic", enabled).commit();
    if (!ensureScheduled(c) && enabled) {
      prefs(c).edit().putBoolean("automatic", false).commit();
      throw new IllegalStateException("Não foi possível agendar o backup diário.");
    }
  }

  public static boolean ensureScheduled(Context c) {
    JobScheduler scheduler = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    if (!automatic(c)
        || !hasAccess(c, destination(c))
        || prefs(c).getBoolean("needs_restore", false)) {
      scheduler.cancel(JOB_ID);
      return false;
    }
    if (scheduler.getPendingJob(JOB_ID) != null) return true;
    JobInfo job =
        new JobInfo.Builder(JOB_ID, new ComponentName(c, DriveBackupJob.class))
            .setPersisted(true)
            .setPeriodic(DAY, 60 * 60 * 1000L)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setRequiresBatteryNotLow(true)
            .setBackoffCriteria(15 * 60 * 1000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .build();
    if (scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS) return true;
    prefs(c).edit().putString("error", "Não foi possível agendar o backup diário.").apply();
    return false;
  }

  public static void disconnect(Context c) {
    Uri uri = destination(c);
    prefs(c).edit().clear().commit();
    ((JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE)).cancel(JOB_ID);
    if (uri != null) release(c, uri);
    new AtomicFile(localCopy(c)).delete();
  }

  public static File localCopy(Context c) {
    return new File(c.getNoBackupFilesDir(), "drive-last-good.json");
  }

  public static boolean hasLocalCopy(Context c) {
    Uri uri = destination(c);
    return uri != null
        && uri.toString().equals(prefs(c).getString("local_uri", ""))
        && localCopy(c).isFile();
  }

  private static void keepLocal(Context c, byte[] bytes) {
    AtomicFile file = new AtomicFile(localCopy(c));
    FileOutputStream stream = null;
    try {
      stream = file.startWrite();
      stream.write(bytes);
      file.finishWrite(stream);
      Uri uri = destination(c);
      if (uri != null) prefs(c).edit().putString("local_uri", uri.toString()).commit();
    } catch (IOException error) {
      if (stream != null) file.failWrite(stream);
    }
  }

  private static byte[] read(Context c, Uri uri, CancellationSignal signal) throws IOException {
    signal.throwIfCanceled();
    ParcelFileDescriptor descriptor = c.getContentResolver().openFileDescriptor(uri, "r", signal);
    if (descriptor == null) throw new IOException("Arquivo indisponível.");
    try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
      byte[] bytes = BackupFiles.read(input);
      signal.throwIfCanceled();
      return bytes;
    }
  }

  private static void writeVerified(Context c, Uri uri, byte[] bytes, CancellationSignal signal)
      throws IOException {
    signal.throwIfCanceled();
    ParcelFileDescriptor descriptor = c.getContentResolver().openFileDescriptor(uri, "wt", signal);
    if (descriptor == null) throw new IOException("Arquivo indisponível.");
    try (OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(descriptor)) {
      try {
        for (int offset = 0; offset < bytes.length; offset += 8192) {
          signal.throwIfCanceled();
          out.write(bytes, offset, Math.min(8192, bytes.length - offset));
        }
        out.flush();
        signal.throwIfCanceled();
      } catch (Exception error) {
        try {
          descriptor.closeWithError("Backup incompleto.");
        } catch (IOException ignored) {
        }
        throw error;
      }
    }
    if (!Arrays.equals(bytes, read(c, uri, signal)))
      throw new IOException("Não foi possível conferir o arquivo gravado.");
  }

  private static void release(Context c, Uri uri) {
    try {
      c.getContentResolver().releasePersistableUriPermission(uri, ACCESS);
    } catch (SecurityException ignored) {
    }
  }

  private static String displayName(Context c, Uri uri) {
    try (Cursor cursor =
        c.getContentResolver()
            .query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
      if (cursor != null && cursor.moveToFirst() && cursor.getString(0) != null)
        return cursor.getString(0);
    } catch (RuntimeException ignored) {
    }
    return "Dose-backup-automatico.json";
  }

  private static String providerName(Context c, Uri uri) {
    ProviderInfo provider = c.getPackageManager().resolveContentProvider(uri.getAuthority(), 0);
    return provider == null
        ? "Destino escolhido"
        : c.getPackageManager().getApplicationLabel(provider.applicationInfo).toString();
  }
}
