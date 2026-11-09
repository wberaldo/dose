package br.com.well.dose;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.net.Uri;
import android.os.*;

/** A bounded foreground alarm using Android's native ringtone player. */
public final class RingingService extends Service {
  private Ringtone ringtone;
  private MediaPlayer legacyPlayer;
  private Vibrator vibrator;
  private PowerManager.WakeLock wake;
  private final Handler handler = new Handler(Looper.getMainLooper());

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent != null && "SILENCE".equals(intent.getAction())) {
      stopSelf();
      return START_NOT_STICKY;
    }
    Alerts.channels(this);
    boolean test = intent != null && "TEST".equals(intent.getAction());
    try {
      if (Build.VERSION.SDK_INT >= 34)
        startForeground(
            Alerts.SERVICE_ID,
            Alerts.serviceNotification(this, test),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
      else startForeground(Alerts.SERVICE_ID, Alerts.serviceNotification(this, test));
    } catch (RuntimeException e) {
      Prefs.error(this, "O Android impediu o som contínuo. Revise as permissões e a bateria.");
      stopSelf();
      return START_NOT_STICKY;
    }
    handler.removeCallbacksAndMessages(null);
    stopAudio();
    wake =
        ((PowerManager) getSystemService(POWER_SERVICE))
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Dose:ringing");
    wake.acquire(65000L);
    NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    NotificationChannel channel = nm.getNotificationChannel(Alerts.CHANNEL);
    boolean dndAllows =
        nm.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_NONE
            || channel.canBypassDnd();
    AudioAttributes attrs =
        new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();
    AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
    if (Prefs.ring(this)
        && channel.getSound() != null
        && channel.getImportance() >= NotificationManager.IMPORTANCE_DEFAULT
        && dndAllows
        && audio.getStreamVolume(AudioManager.STREAM_ALARM) > 0) {
      handler.postDelayed(() -> playTone(channel.getSound(), attrs, false), 200);
    }
    if (Prefs.vibrate(this) && channel.shouldVibrate() && dndAllows) {
      vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
      vibrator.vibrate(
          VibrationEffect.createWaveform(new long[] {0, 350, 250, 350, 250, 600, 1100}, 0), attrs);
    }
    handler.postDelayed(this::stopSelf, 60000L);
    return START_NOT_STICKY;
  }

  private Uri builtIn() {
    return Uri.parse("android.resource://" + getPackageName() + "/raw/dose_alarm");
  }

  private void playTone(Uri uri, AudioAttributes attrs, boolean fallback) {
    try {
      if (Build.VERSION.SDK_INT >= 28) {
        ringtone = RingtoneManager.getRingtone(this, uri);
        if (ringtone == null) throw new IllegalStateException("Ringtone unavailable");
        ringtone.setAudioAttributes(attrs);
        ringtone.setLooping(true);
        ringtone.play();
        Ringtone expected = ringtone;
        handler.postDelayed(
            () -> {
              if (ringtone != expected) return;
              if (expected.isPlaying())
                Prefs.get(this).edit().putLong("last_audio", System.currentTimeMillis()).apply();
              else if (!fallback) {
                expected.stop();
                ringtone = null;
                playTone(builtIn(), attrs, true);
              } else
                Prefs.error(
                    this, "Não foi possível tocar o som. Confira o canal e o volume de alarmes.");
            },
            600);
      } else {
        legacyPlayer = new MediaPlayer();
        legacyPlayer.setAudioAttributes(attrs);
        legacyPlayer.setDataSource(this, uri);
        legacyPlayer.setLooping(true);
        legacyPlayer.prepare();
        legacyPlayer.start();
        if (legacyPlayer.isPlaying())
          Prefs.get(this).edit().putLong("last_audio", System.currentTimeMillis()).apply();
      }
    } catch (Exception e) {
      if (ringtone != null) {
        ringtone.stop();
        ringtone = null;
      }
      if (legacyPlayer != null) {
        legacyPlayer.release();
        legacyPlayer = null;
      }
      if (!fallback) playTone(builtIn(), attrs, true);
      else {
        android.util.Log.w("DoseAlarm", "Unable to play alarm ringtone", e);
        Prefs.error(this, "Não foi possível tocar o som. Confira o canal e o volume de alarmes.");
      }
    }
  }

  private void stopAudio() {
    if (ringtone != null) {
      ringtone.stop();
      ringtone = null;
    }
    if (legacyPlayer != null) {
      try {
        legacyPlayer.stop();
      } catch (Exception ignored) {
      }
      legacyPlayer.release();
      legacyPlayer = null;
    }
    if (vibrator != null) {
      vibrator.cancel();
      vibrator = null;
    }
    if (wake != null && wake.isHeld()) wake.release();
    wake = null;
  }

  @Override
  public void onDestroy() {
    handler.removeCallbacksAndMessages(null);
    stopAudio();
    stopForeground(STOP_FOREGROUND_REMOVE);
    super.onDestroy();
  }
}
