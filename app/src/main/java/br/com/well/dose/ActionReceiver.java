package br.com.well.dose;

import android.content.*;

public final class ActionReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context c, Intent i) {
    long id = i.getLongExtra("dose_id", -1);
    if (id == -1) {
      AlarmEngine.testAction(c, i.getAction());
    } else if (id > 0) AlarmEngine.action(c, id, i.getAction());
  }
}
