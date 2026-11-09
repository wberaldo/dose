package br.com.well.dose;

import android.content.*;

public final class AlarmReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context c, Intent i) {
    if (AlarmAudio.RESTORE.equals(i.getAction())) {
      AlarmAudio.recoverExpired(c);
      return;
    }
    AlarmEngine.tick(c);
  }
}
