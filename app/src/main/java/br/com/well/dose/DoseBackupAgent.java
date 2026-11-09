package br.com.well.dose;

import android.app.backup.*;
import android.os.*;
import java.io.*;

/** Only a validated logical snapshot is offered to the system's encrypted backup transport. */
public final class DoseBackupAgent extends BackupAgent {
  @Override
  public void onBackup(ParcelFileDescriptor old, BackupDataOutput out, ParcelFileDescriptor next) {}

  @Override
  public void onRestore(BackupDataInput in, int version, ParcelFileDescriptor next) {}

  @Override
  public void onFullBackup(FullBackupDataOutput output) throws IOException {
    if (Build.VERSION.SDK_INT < 28
        || !CloudBackup.eligible(
            CloudBackup.enabled(this), Build.VERSION.SDK_INT, output.getTransportFlags())) return;
    try {
      CloudBackup.prepare(this);
      if (!CloudBackup.enabled(this)) return;
      super.onFullBackup(output);
      CloudBackup.prefs(this)
          .edit()
          .putLong("prepared_at", System.currentTimeMillis())
          .remove("error")
          .commit();
    } catch (Exception error) {
      CloudBackup.prefs(this)
          .edit()
          .putString("error", "Não foi possível preparar os dados para o backup do Android.")
          .commit();
      throw new IOException("Não foi possível preparar o backup.", error);
    }
  }

  @Override
  public long onEstimateFullBackupBytes(long quota, int flags) throws IOException {
    if (!CloudBackup.eligible(CloudBackup.enabled(this), Build.VERSION.SDK_INT, flags)) return 0;
    try {
      return CloudBackup.prepare(this).length();
    } catch (Exception error) {
      throw new IOException("Não foi possível preparar o backup.", error);
    }
  }

  @Override
  public void onRestoreFinished() {
    try {
      CloudBackup.restore(this);
    } catch (Exception error) {
      CloudBackup.prefs(this)
          .edit()
          .putString(
              "error",
              "Não foi possível concluir a restauração recebida. Abra o Dose e confira seus dados.")
          .commit();
    }
  }

  @Override
  public void onQuotaExceeded(long used, long quota) {
    CloudBackup.prefs(this)
        .edit()
        .putString("error", "O backup ultrapassou o espaço permitido pelo Android.")
        .commit();
  }
}
