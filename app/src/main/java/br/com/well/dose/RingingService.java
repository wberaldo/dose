package br.com.well.dose;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.net.Uri;
import android.os.*;
import java.util.List;

/** Bounded alarm playback attached to the actual dose notification, without an auxiliary notice. */
public final class RingingService extends Service {
  private static volatile RingingService running;
  private MediaPlayer player;
  private AudioFocusRequest focus;
  private Vibrator vibrator;
  private PowerManager.WakeLock wake;
  private long notificationId;
  private boolean test;
  private boolean detached;
  private int lastStartId, audioGeneration, focusAttempts;
  private final Handler handler = new Handler(Looper.getMainLooper());

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public void onCreate() {
    super.onCreate();
    running = this;
  }

  public static void stopTest(Context c) {
    RingingService current = running;
    if (current == null || current.test) stopSound(c);
  }

  public static void stopSound(Context c) {
    RingingService current = running;
    if (current != null) current.handler.post(current::stopAlarm);
    else c.stopService(new Intent(c, RingingService.class));
  }

  public static void stopCancelledReminders(Context c) {
    RingingService current = running;
    if (current == null || current.test) return;
    current.handler.post(
        () -> {
          if (running != current || current.activeDose(current.notificationId) != null) return;
          List<Dose> others =
              Store.get(c)
                  .doses(
                      "status='pending' AND last_alert>0 AND snoozed=0 AND reminder_cancelled=0"
                          + " AND expires>?",
                      new String[] {"" + System.currentTimeMillis()},
                      "last_alert DESC,id");
          if (others.isEmpty()) current.stopAlarm();
          else
            try {
              c.startService(
                  new Intent(c, RingingService.class)
                      .setAction("RING")
                      .putExtra("dose_id", others.get(0).id)
                      .putExtra("keep_screen", true));
            } catch (RuntimeException e) {
              current.stopAlarm();
            }
        });
  }

  private boolean keepReminder() {
    return test
        ? Prefs.get(this).getBoolean("test_visible", false)
        : activeDose(notificationId) != null;
  }

  /** Detach while the service is still running: Android cancels its notice before onDestroy. */
  private void stopAlarm() {
    handler.removeCallbacksAndMessages(null);
    stopPlayback();
    AlarmAudio.releaseVolume(this);
    boolean keep = keepReminder();
    stopForeground(keep ? STOP_FOREGROUND_DETACH : STOP_FOREGROUND_REMOVE);
    detached = true;
    if (!keep && notificationId != 0) Alerts.cancel(this, notificationId);
    stopSelf(lastStartId);
  }

  private Dose activeDose(long id) {
    Dose d = Store.get(this).dose(id);
    return d != null
            && d.actionable()
            && !d.reminderCancelled
            && !d.snoozed
            && d.lastAlert > 0
            && d.expires > System.currentTimeMillis()
        ? d
        : null;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    lastStartId = startId;
    if (intent != null && "SILENCE".equals(intent.getAction())) {
      stopAlarm();
      return START_NOT_STICKY;
    }
    Alerts.channels(this);
    Dose dose =
        notificationId > 0 && notificationId < Alerts.SERVICE_ID
            ? activeDose(notificationId)
            : null;
    if (dose == null && intent != null) dose = activeDose(intent.getLongExtra("dose_id", 0));
    if (dose == null && (intent == null || !"TEST".equals(intent.getAction()))) {
      List<Dose> candidates =
          Store.get(this)
              .doses(
                  "status='pending' AND last_alert>0 AND snoozed=0 AND reminder_cancelled=0 AND"
                      + " expires>?",
                  new String[] {"" + System.currentTimeMillis()},
                  "last_alert DESC,id");
      if (!candidates.isEmpty()) dose = candidates.get(0);
    }
    boolean newTest = dose == null && Prefs.get(this).getBoolean("test_visible", false);
    if (dose == null && !newTest) {
      stopAlarm();
      return START_NOT_STICKY;
    }
    long id = newTest ? Alerts.TEST_ID : dose.id;
    if (notificationId != 0 && notificationId != id) {
      boolean keepPrevious = keepReminder();
      stopForeground(keepPrevious ? STOP_FOREGROUND_DETACH : STOP_FOREGROUND_REMOVE);
      if (!keepPrevious) Alerts.cancel(this, notificationId);
    }
    notificationId = id;
    test = newTest;
    Notification notification = Alerts.notification(this, dose, false);
    try {
      if (Build.VERSION.SDK_INT >= 34)
        startForeground((int) id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
      else startForeground((int) id, notification);
      detached = false;
    } catch (RuntimeException e) {
      Prefs.error(this, "O Android impediu o som contínuo. Revise as permissões e a bateria.");
      stopAlarm();
      return START_NOT_STICKY;
    }
    handler.removeCallbacksAndMessages(null);
    stopPlayback();
    wake =
        ((PowerManager) getSystemService(POWER_SERVICE))
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Dose:ringing");
    wake.acquire(65000L);
    Prefs.get(this)
        .edit()
        .remove("last_audio")
        .putLong("ringing_until", System.currentTimeMillis() + 60000L)
        .commit();
    NotificationChannel channel =
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
            .getNotificationChannel(Alerts.CHANNEL);
    boolean dnd = AlarmAudio.dndAllowsAlarms(this);
    if (Prefs.ring(this) && channel.getImportance() >= NotificationManager.IMPORTANCE_DEFAULT) {
      if (dnd) {
        AlarmAudio.acquireVolume(this, System.currentTimeMillis() + 70000L);
        Uri tone = channel.getSound();
        if (tone == null && Prefs.get(this).getBoolean("force_sound", true))
          tone = AlarmAudio.builtIn(this);
        if (tone != null) {
          Uri selected = tone;
          handler.postDelayed(() -> play(selected, false), 200);
        }
      } else
        Prefs.error(
            this,
            "O Não perturbe está bloqueando alarmes. Permita alarmes nas configurações desse"
                + " modo.");
    }
    if (Prefs.vibrate(this) && channel.shouldVibrate() && dnd) {
      vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
      vibrator.vibrate(
          VibrationEffect.createWaveform(new long[] {0, 350, 250, 350, 250, 600, 1100}, 0),
          AlarmAudio.attributes());
    }
    if (intent == null || !intent.getBooleanExtra("keep_screen", false))
      Alerts.openFullScreen(this, test ? -1L : id);
    handler.postDelayed(this::stopAlarm, 60000L);
    return START_NOT_STICKY;
  }

  private void play(Uri uri, boolean fallback) {
    try {
      AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
      if (focus == null) {
        int generation = audioGeneration;
        focus =
            new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(AlarmAudio.attributes())
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(
                    change -> {
                      if (generation == audioGeneration && change == AudioManager.AUDIOFOCUS_LOSS)
                        stopAlarm();
                    },
                    handler)
                .build();
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
          audio.abandonAudioFocusRequest(focus);
          focus = null;
          // On a cold start the system can still be publishing the foreground-service state.
          // Retry briefly while the alarm screen opens, without bypassing a denied focus request.
          if (++focusAttempts < 10) {
            int pendingGeneration = audioGeneration;
            handler.postDelayed(
                () -> {
                  if (pendingGeneration == audioGeneration) play(uri, fallback);
                },
                500);
          } else
            Prefs.error(
                this,
                "O Android não liberou o áudio do alarme. Confira as permissões e teste"
                    + " novamente.");
          return;
        }
        focusAttempts = 0;
      }
      MediaPlayer expected = new MediaPlayer();
      player = expected;
      expected.setAudioAttributes(AlarmAudio.attributes());
      expected.setDataSource(this, uri);
      expected.setLooping(true);
      expected.setVolume(1f, 1f);
      expected.setOnPreparedListener(
          mp -> {
            if (player != mp) return;
            try {
              mp.start();
              if (mp.isPlaying())
                Prefs.get(this).edit().putLong("last_audio", System.currentTimeMillis()).apply();
            } catch (RuntimeException e) {
              failed(mp, fallback);
            }
          });
      expected.setOnErrorListener(
          (mp, what, extra) -> {
            failed(mp, fallback);
            return true;
          });
      expected.prepareAsync();
    } catch (Exception e) {
      failed(player, fallback);
    }
  }

  private void failed(MediaPlayer expected, boolean fallback) {
    if (player != expected) return;
    if (player != null) {
      try {
        player.release();
      } catch (RuntimeException ignored) {
      }
      player = null;
    }
    if (!fallback) play(AlarmAudio.builtIn(this), true);
    else
      Prefs.error(
          this, "Não foi possível tocar o som do alarme. Confira o volume e o Não perturbe.");
  }

  private void stopPlayback() {
    audioGeneration++;
    focusAttempts = 0;
    if (player != null) {
      try {
        player.setOnPreparedListener(null);
        player.setOnErrorListener(null);
        player.release();
      } catch (RuntimeException ignored) {
      }
      player = null;
    }
    if (focus != null) {
      ((AudioManager) getSystemService(AUDIO_SERVICE)).abandonAudioFocusRequest(focus);
      focus = null;
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
    stopPlayback();
    AlarmAudio.releaseVolume(this);
    Prefs.get(this).edit().remove("ringing_until").commit();
    boolean keep = keepReminder();
    if (!detached) {
      stopForeground(keep ? STOP_FOREGROUND_DETACH : STOP_FOREGROUND_REMOVE);
      // If the system stopped us first, recover the pending reminder as a normal notification.
      if (keep && Alerts.allowed(this)) {
        Notification notice =
            Alerts.notification(this, test ? null : activeDose(notificationId), false);
        notice.fullScreenIntent = null;
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
            .notify((int) notificationId, notice);
      }
    }
    if (!keep && notificationId != 0) Alerts.cancel(this, notificationId);
    running = null;
    super.onDestroy();
  }
}
