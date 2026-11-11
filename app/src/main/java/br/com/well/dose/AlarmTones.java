package br.com.well.dose;

import android.content.Context;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

/** Stable, portable tone names. Preview never changes the device's alarm volume. */
public final class AlarmTones {
  public static final String PREF = "alarm_tone";
  private static final String[] KEYS = {"original", "suave", "gotas", "sinos", "pulso", "digital", "classico"};
  private static final String[] LABELS = {"Dose original / som do Android", "Suave", "Gotas", "Sinos", "Pulso", "Digital", "Clássico"};

  private AlarmTones() {}

  public static String[] keys() { return KEYS.clone(); }
  public static String[] labels() { return LABELS.clone(); }

  public static boolean valid(String key) {
    for (String value : KEYS) if (value.equals(key)) return true;
    return false;
  }

  public static String key(Context c) {
    String value = Prefs.get(c).getString(PREF, "original");
    return valid(value) ? value : "original";
  }

  public static String label(Context c) {
    String key = key(c);
    for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return LABELS[i];
    return LABELS[0];
  }

  public static Uri uri(Context c, String key) {
    String name = valid(key) && !"original".equals(key) ? "dose_" + key : "dose_alarm";
    return Uri.parse("android.resource://" + c.getPackageName() + "/raw/" + name);
  }

  public static final class Preview {
    private final Context c;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private AudioFocusRequest focus;
    private int generation;

    public Preview(Context c) { this.c = c; }

    public void play(String key) {
      stop();
      int expectedGeneration = generation;
      if (Prefs.get(c).getLong("ringing_until", 0) > System.currentTimeMillis()) {
        Ui.toast(c, "Encerre ou adie o alarme atual para ouvir a prévia.");
        return;
      }
      if (!AlarmAudio.dndAllowsAlarms(c)) {
        Ui.toast(c, "O Não perturbe está bloqueando a prévia do alarme.");
        return;
      }
      AudioManager audio = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
      if (audio.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
        Ui.toast(c, "Aumente o volume de alarmes do celular para ouvir a prévia.");
        return;
      }
      try {
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AlarmAudio.attributes())
            .setOnAudioFocusChangeListener(change -> {
              if (generation == expectedGeneration && change < 0) stop();
            }, handler)
            .build();
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
          stop();
          Ui.toast(c, "Não foi possível ouvir a prévia agora.");
          return;
        }
        MediaPlayer expected = new MediaPlayer();
        player = expected;
        expected.setAudioAttributes(AlarmAudio.attributes());
        Uri selected = uri(c, key);
        android.app.NotificationChannel channel = ((android.app.NotificationManager)
            c.getSystemService(Context.NOTIFICATION_SERVICE)).getNotificationChannel(Alerts.channelId(key));
        if (channel != null && channel.getSound() != null) selected = channel.getSound();
        expected.setDataSource(c, selected);
        expected.setOnPreparedListener(mp -> {
          if (player != mp) return;
          try { mp.start(); } catch (RuntimeException error) { stop(); }
        });
        expected.setOnCompletionListener(mp -> { if (player == mp) stop(); });
        expected.setOnErrorListener((mp, what, extra) -> {
          if (player == mp) { stop(); Ui.toast(c, "Não foi possível ouvir esse toque."); }
          return true;
        });
        expected.prepareAsync();
        handler.postDelayed(this::stop, 6000);
      } catch (Exception error) {
        stop();
        Ui.toast(c, "Não foi possível ouvir esse toque.");
      }
    }

    public void stop() {
      generation++;
      handler.removeCallbacksAndMessages(null);
      if (player != null) {
        player.setOnPreparedListener(null);
        player.setOnCompletionListener(null);
        player.setOnErrorListener(null);
        player.release();
        player = null;
      }
      if (focus != null) {
        ((AudioManager) c.getSystemService(Context.AUDIO_SERVICE)).abandonAudioFocusRequest(focus);
        focus = null;
      }
    }
  }
}
