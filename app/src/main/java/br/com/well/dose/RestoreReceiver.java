package br.com.well.dose;

import android.content.*;

public final class RestoreReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context c, Intent i) {
    synchronized (AlarmEngine.LOCK) {
      if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction())) Prefs.upgradeAlarms(c);
      if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction())
          || Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) AlarmAudio.releaseVolume(c);
      else AlarmAudio.recoverExpired(c);
      if (Intent.ACTION_TIMEZONE_CHANGED.equals(i.getAction()))
        Store.get(c).rezone(System.currentTimeMillis());
      // Rebuild the next durable alarm. Never start audio directly from boot.
      AlarmEngine.refresh(c);
      DriveBackup.ensureScheduled(c);
    }
  }
}
