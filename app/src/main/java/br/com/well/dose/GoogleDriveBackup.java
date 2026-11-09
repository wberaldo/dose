package br.com.well.dose;

import android.app.job.*;
import android.content.*;
import android.os.*;
import android.util.AtomicFile;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GoogleDriveBackup {
  public static final int JOB_ID = 9201;
  private static final ExecutorService IO = Executors.newSingleThreadExecutor();
  private static final AtomicBoolean BUSY = new AtomicBoolean();
  private static final GoogleDriveApi API = new GoogleDriveApi(new GoogleDriveHttp());

  public static final class RemoteChanged extends IOException {
    public RemoteChanged() {
      super("A cópia no Drive precisa ser conferida antes de substituir.");
    }
  }

  public interface Work {
    String run(CancellationSignal signal) throws Exception;
  }

  public interface Done {
    void result(boolean ok, String message);
  }

  private GoogleDriveBackup() {}

  public static SharedPreferences prefs(Context c) {
    return c.getSharedPreferences("dose_google_drive", Context.MODE_PRIVATE);
  }

  public static boolean connected(Context c) {
    return !prefs(c).getString("account_id", "").isEmpty();
  }

  public static boolean automatic(Context c) {
    return prefs(c).getBoolean("automatic", false);
  }

  public static boolean protectedFile(Context c) {
    return prefs(c).getBoolean("restore_required", false);
  }

  public static boolean busy() {
    return BUSY.get();
  }

  public static boolean submit(Context context, CancellationSignal signal, Work work, Done done) {
    if (!BUSY.compareAndSet(false, true)) return false;
    Context c = context.getApplicationContext();
    IO.execute(
        () -> {
          boolean ok = false;
          String message;
          try {
            signal.throwIfCanceled();
            message = work.run(signal);
            prefs(c).edit().remove("error").commit();
            ok = true;
          } catch (Exception error) {
            message = error(c, error);
            prefs(c).edit().putString("error", message).commit();
          } finally {
            BUSY.set(false);
          }
          boolean success = ok;
          String result = message;
          new Handler(Looper.getMainLooper()).post(() -> done.result(success, result));
        });
    return true;
  }

  private static GoogleDriveApi.Check check(CancellationSignal signal) {
    return () -> {
      if (signal.isCanceled() || Thread.currentThread().isInterrupted())
        throw new InterruptedIOException("Operação interrompida.");
    };
  }

  private static void configured(Context c) throws IOException {
    if (!GoogleDriveConfig.enabled(c)) throw new IOException("Configuração Google pendente.");
  }

  public static String connect(Context c, String token, CancellationSignal signal)
      throws Exception {
    configured(c);
    GoogleDriveApi.Profile profile = API.profile(token, check(signal));
    GoogleDriveApi.RemoteFile file = API.latest(token, check(signal));
    boolean known =
        profile.id.equals(prefs(c).getString("account_id", ""))
            && file != null
            && file.id.equals(prefs(c).getString("file_id", ""))
            && file.modified.equals(prefs(c).getString("file_modified", ""))
            && !protectedFile(c);
    signal.throwIfCanceled();
    prefs(c)
        .edit()
        .clear()
        .putString("account_id", profile.id)
        .putString("email", profile.email)
        .putString("name", profile.name)
        .putBoolean("restore_required", file != null && !known)
        .putBoolean("automatic", false)
        .commit();
    storeFile(c, file);
    new AtomicFile(local(c)).delete();
    if (DriveBackup.automatic(c)) DriveBackup.setAutomatic(c, false);
    schedule(c);
    return file != null && !known
        ? "Conta conectada. Há uma cópia no Drive: restaure ou confirme a substituição antes de"
            + " salvar."
        : "Conta Google conectada. Você já pode fazer o backup.";
  }

  private static String access(Context c, CancellationSignal signal) throws Exception {
    configured(c);
    if (!connected(c)) throw new GoogleAuthorization.NeedsConsent();
    signal.throwIfCanceled();
    String token = GoogleAuthorization.silent(c, prefs(c).getString("email", ""));
    try {
      verifyOwner(c, API.profile(token, check(signal)));
    } catch (GoogleDriveApi.ApiFailure error) {
      if (error.status != 401) throw error;
      GoogleAuthorization.clear(c, token);
      token = GoogleAuthorization.silent(c, prefs(c).getString("email", ""));
      verifyOwner(c, API.profile(token, check(signal)));
    }
    return token;
  }

  public static boolean sameVersion(String id, String modified, GoogleDriveApi.RemoteFile remote) {
    return remote == null
        || (remote.id.equals(id) && !modified.isEmpty() && remote.modified.equals(modified));
  }

  public static void requireOwner(String expected, String actual) throws IOException {
    if (expected == null || expected.isEmpty() || !expected.equals(actual))
      throw new IOException("A conta Google retornada não corresponde ao backup configurado.");
  }

  private static void verifyOwner(Context c, GoogleDriveApi.Profile profile) throws IOException {
    requireOwner(prefs(c).getString("account_id", ""), profile.id);
  }

  public static String save(Context c, CancellationSignal signal) throws Exception {
    if (protectedFile(c)) throw new IOException("A cópia existente aguarda sua confirmação.");
    String token = access(c, signal);
    GoogleDriveApi.RemoteFile old = API.latest(token, check(signal));
    if (!sameVersion(
        prefs(c).getString("file_id", ""), prefs(c).getString("file_modified", ""), old)) {
      storeFile(c, old);
      prefs(c).edit().putBoolean("restore_required", true).putBoolean("automatic", false).commit();
      schedule(c);
      throw new RemoteChanged();
    }
    byte[] bytes = BackupFiles.snapshot(c);
    GoogleDriveApi.RemoteFile uploaded = API.upload(token, old, bytes, check(signal));
    storeFile(c, uploaded);
    prefs(c)
        .edit()
        .putLong("saved_at", System.currentTimeMillis())
        .putBoolean("needs_reconnect", false)
        .commit();
    keepLocal(c, bytes);
    return "Backup confirmado no Google Drive.";
  }

  public static String restore(Context c, CancellationSignal signal) throws Exception {
    String token = access(c, signal);
    GoogleDriveApi.RemoteFile file = API.latest(token, check(signal));
    if (file == null) throw new IOException("Não há backup do Dose nesta conta.");
    byte[] bytes = API.download(token, file, check(signal));
    signal.throwIfCanceled();
    BackupFiles.restore(c, bytes);
    storeFile(c, file);
    prefs(c)
        .edit()
        .putBoolean("restore_required", false)
        .putLong("restored_at", System.currentTimeMillis())
        .commit();
    keepLocal(c, bytes);
    return "Backup do Google Drive restaurado.";
  }

  public static void replaceConfirmed(Context c) {
    prefs(c).edit().putBoolean("restore_required", false).commit();
  }

  private static void storeFile(Context c, GoogleDriveApi.RemoteFile file) {
    SharedPreferences.Editor edit = prefs(c).edit();
    if (file == null) edit.remove("file_id").remove("file_name").remove("file_modified");
    else
      edit.putString("file_id", file.id)
          .putString("file_name", file.name)
          .putString("file_modified", file.modified);
    edit.commit();
  }

  public static void setAutomatic(Context c, boolean enabled) {
    if (enabled
        && (!GoogleDriveConfig.enabled(c)
            || !connected(c)
            || protectedFile(c)
            || prefs(c).getBoolean("needs_reconnect", false)))
      throw new IllegalStateException("Conecte a conta e escolha os dados primeiro.");
    prefs(c).edit().putBoolean("automatic", enabled).commit();
    if (!schedule(c) && enabled) {
      prefs(c).edit().putBoolean("automatic", false).commit();
      throw new IllegalStateException("Não foi possível agendar.");
    }
  }

  public static boolean schedule(Context c) {
    JobScheduler jobs = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    if (!GoogleDriveConfig.enabled(c)
        || !automatic(c)
        || !connected(c)
        || protectedFile(c)
        || prefs(c).getBoolean("needs_reconnect", false)) {
      jobs.cancel(JOB_ID);
      return false;
    }
    if (jobs.getPendingJob(JOB_ID) != null) return true;
    JobInfo job =
        new JobInfo.Builder(JOB_ID, new ComponentName(c, GoogleDriveJob.class))
            .setPersisted(true)
            .setPeriodic(DriveBackup.DAY, 3600000)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setRequiresBatteryNotLow(true)
            .setBackoffCriteria(900000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .build();
    return jobs.schedule(job) == JobScheduler.RESULT_SUCCESS;
  }

  public static String disconnect(Context c) throws Exception {
    String email = prefs(c).getString("email", "");
    prefs(c).edit().putBoolean("automatic", false).commit();
    schedule(c);
    boolean revoked = email.isEmpty();
    if (!email.isEmpty())
      try {
        GoogleAuthorization.revoke(c, email);
        revoked = true;
      } catch (Exception ignored) {
      }
    prefs(c).edit().clear().commit();
    new AtomicFile(local(c)).delete();
    return revoked
        ? "Conta desconectada. Seus medicamentos e arquivos no Drive foram mantidos."
        : "Conta removida deste app. Confira também o acesso do Dose nas configurações da conta"
            + " Google.";
  }

  private static File local(Context c) {
    return new File(c.getNoBackupFilesDir(), "google-drive-last-good.json");
  }

  private static void keepLocal(Context c, byte[] bytes) {
    AtomicFile file = new AtomicFile(local(c));
    FileOutputStream out = null;
    try {
      out = file.startWrite();
      out.write(bytes);
      file.finishWrite(out);
    } catch (IOException e) {
      if (out != null) file.failWrite(out);
    }
  }

  private static String error(Context c, Throwable error) {
    while (error instanceof ExecutionException && error.getCause() != null)
      error = error.getCause();
    if (error instanceof RemoteChanged)
      return "Existe uma cópia nova no Drive. Restaure ou confirme a substituição antes de salvar.";
    if (GoogleAuthorization.needsConsent(error)) {
      prefs(c).edit().putBoolean("needs_reconnect", true).putBoolean("automatic", false).commit();
      schedule(c);
      return GoogleAuthorization.errorMessage(error);
    }
    if (error instanceof GoogleDriveApi.ApiFailure) {
      GoogleDriveApi.ApiFailure failure = (GoogleDriveApi.ApiFailure) error;
      if (failure.status == 401) {
        prefs(c).edit().putBoolean("needs_reconnect", true).putBoolean("automatic", false).commit();
        schedule(c);
        return "Reconecte sua conta Google para continuar.";
      }
      if (failure.reason.equals("storageQuotaExceeded"))
        return "O Google Drive está sem espaço para o backup.";
      if (failure.reason.equals("accessNotConfigured"))
        return "A API do Drive precisa ser habilitada no projeto Google do Dose.";
      if (failure.status == 429 || failure.status >= 500)
        return "O Google Drive está temporariamente indisponível. Tente novamente mais tarde.";
    }
    if (error instanceof InterruptedIOException)
      return "Operação interrompida. O último backup confirmado foi mantido.";
    return GoogleAuthorization.errorMessage(error);
  }
}
