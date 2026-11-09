package br.com.well.dose;

import android.app.job.*;
import android.os.CancellationSignal;

public final class DriveBackupJob extends JobService {
  private CancellationSignal current;

  @Override
  public boolean onStartJob(JobParameters parameters) {
    if (!DriveBackup.automatic(this) || !DriveBackup.hasAccess(this, DriveBackup.destination(this)))
      return false;
    CancellationSignal signal = new CancellationSignal();
    current = signal;
    boolean started =
        DriveBackup.submit(
            this,
            signal,
            token -> DriveBackup.save(this, token),
            (ok, message) -> {
              if (current != signal || signal.isCanceled()) return;
              current = null;
              if (ok)
                DriveBackup.prefs(this)
                    .edit()
                    .putLong("job_saved_at", System.currentTimeMillis())
                    .apply();
              jobFinished(parameters, !ok);
            });
    if (!started) {
      new android.os.Handler(getMainLooper())
          .post(
              () -> {
                if (current != signal || signal.isCanceled()) return;
                current = null;
                jobFinished(parameters, true);
              });
    }
    return true;
  }

  @Override
  public boolean onStopJob(JobParameters parameters) {
    if (current != null) current.cancel();
    current = null;
    return DriveBackup.automatic(this);
  }
}
