package br.com.well.dose;

import android.content.*;

public final class AlarmReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context c, Intent i) {
    AlarmEngine.tick(c);
  }
}
