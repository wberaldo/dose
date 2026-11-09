package br.com.well.dose;

import android.app.job.*;
import android.os.*;

public final class GoogleDriveJob extends JobService {
  private CancellationSignal current;

  @Override
  public boolean onStartJob(JobParameters parameters) {
    if (!GoogleDriveBackup.automatic(this) || !GoogleDriveConfig.enabled(this)) return false;
    CancellationSignal signal = new CancellationSignal();
    current = signal;
    boolean started =
        GoogleDriveBackup.submit(
            this,
            signal,
            token -> GoogleDriveBackup.save(this, token),
            (ok, message) -> {
              if (current != signal || signal.isCanceled()) return;
              current = null;
              if (ok)
                GoogleDriveBackup.prefs(this)
                    .edit()
                    .putLong("job_saved_at", System.currentTimeMillis())
                    .apply();
              jobFinished(parameters, !ok && GoogleDriveBackup.automatic(this));
            });
    if (!started)
      new Handler(getMainLooper())
          .post(
              () -> {
                if (current == signal && !signal.isCanceled()) {
                  current = null;
                  jobFinished(parameters, true);
                }
              });
    return true;
  }

  @Override
  public boolean onStopJob(JobParameters parameters) {
    if (current != null) current.cancel();
    current = null;
    return GoogleDriveBackup.automatic(this);
  }
}
