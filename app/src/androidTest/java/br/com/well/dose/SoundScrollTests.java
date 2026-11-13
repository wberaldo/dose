package br.com.well.dose;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import android.view.*;
import android.view.inspector.WindowInspector;
import android.widget.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Destructive only to the isolated test AVD. Never run against personal app data. */
public final class SoundScrollTests extends Instrumentation {
  private Context c;
  private int passed;
  private final StringBuilder report = new StringBuilder();

  @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

  @Override public void onStart() {
    c = getTargetContext();
    Bundle result = new Bundle();
    try {
      checks();
      result.putString("result", "PASS");
      result.putString("stream", report + "\nPASS " + passed + " sound and scroll checks\n");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable error) {
      result.putString("result", "FAIL");
      result.putString("stream", report + "\nFAIL: " + android.util.Log.getStackTraceString(error));
      finish(Activity.RESULT_CANCELED, result);
    }
  }

  private void check(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
    passed++;
    report.append("PASS ").append(message).append('\n');
    Bundle b = new Bundle(); b.putString("stream", message + "\n"); sendStatus(0, b);
  }

  private void await(BooleanSupplier condition) {
    long deadline = SystemClock.elapsedRealtime() + 20000;
    while (!condition.getAsBoolean() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100);
    if (!condition.getAsBoolean()) throw new AssertionError("Operação Android não terminou.");
  }

  private void onMain(Runnable action) {
    Throwable[] error = {null};
    super.runOnMainSync(() -> {
      try { action.run(); } catch (Throwable failure) { error[0] = failure; }
    });
    if (error[0] != null) throw new AssertionError("Ação de interface falhou", error[0]);
  }

  private List<View> views(View root) {
    List<View> result = new ArrayList<>(); result.add(root);
    if (root instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) root;
      for (int i = 0; i < group.getChildCount(); i++) result.addAll(views(group.getChildAt(i)));
    }
    return result;
  }

  private ScrollView scroll(Activity a) {
    return (ScrollView) views(a.getWindow().getDecorView()).stream()
        .filter(v -> v instanceof ScrollView).findFirst().get();
  }

  private void press(Activity a, String text) {
    View target = views(a.getWindow().getDecorView()).stream()
        .filter(v -> text.contentEquals(v.getContentDescription() == null ? "" : v.getContentDescription())
            || v instanceof TextView && text.equals(((TextView) v).getText().toString()))
        .findFirst().get();
    while (!target.isClickable() && target.getParent() instanceof View) target = (View) target.getParent();
    if (!target.performClick()) throw new AssertionError("Controle não respondeu: " + text);
  }

  private void dialogChoice(String label) {
    ListView list = (ListView) WindowInspector.getGlobalWindowViews().stream()
        .flatMap(v -> views(v).stream()).filter(v -> v instanceof ListView).findFirst().get();
    int index = Arrays.asList(AlarmTones.labels()).indexOf(label);
    list.performItemClick(list.getChildAt(index - list.getFirstVisiblePosition()), index, list.getItemIdAtPosition(index));
  }

  private void dialogButton(String text) {
    int id = "Usar toque".equals(text) ? android.R.id.button1 : android.R.id.button2;
    View button = WindowInspector.getGlobalWindowViews().stream().flatMap(v -> views(v).stream())
        .filter(v -> v instanceof Button && v.getId() == id && v.isShown()).findFirst()
        .orElseThrow(() -> new AssertionError("Botão ausente: " + text));
    button.performClick();
  }

  private View firstItem(ScrollView scroll) { return ((ViewGroup) scroll.getChildAt(0)).getChildAt(0); }

  private boolean darkBackground(Activity activity) {
    View root = ((ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);
    int color = ((android.graphics.drawable.ColorDrawable) root.getBackground()).getColor();
    return android.graphics.Color.red(color) < 64 && android.graphics.Color.green(color) < 64;
  }

  private Activity openEditor(Activity parent) {
    ActivityMonitor monitor = addMonitor(EditorActivity.class.getName(), null, false);
    onMain(() -> parent.startActivity(new Intent(parent, EditorActivity.class)));
    Activity editor = waitForMonitorWithTimeout(monitor, 30000);
    removeMonitor(monitor);
    if (editor == null) throw new AssertionError("Cadastro não abriu na tarefa da agenda");
    waitForIdleSync();
    return editor;
  }

  private void checks() throws Exception {
    for (String command : new String[] {
        "settings put system screen_off_timeout 2147483647",
        "settings put global stay_on_while_plugged_in 3",
        "input keyevent 224", "wm dismiss-keyguard", "am force-stop com.android.launcher3"}) {
      try (android.os.ParcelFileDescriptor pipe = getUiAutomation().executeShellCommand(command);
          java.io.InputStream input = new android.os.ParcelFileDescriptor.AutoCloseInputStream(pipe)) {
        byte[] buffer = new byte[1024];
        while (input.read(buffer) != -1) { }
      }
    }
    c.stopService(new Intent(c, RingingService.class));
    NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
    nm.cancelAll();
    Store db = Store.get(c);
    for (String table : new String[] {"import_links", "reminder_mutes", "dose_mutes", "import_sources", "doses", "meds"})
      db.getWritableDatabase().delete(table, null, null);
    Prefs.get(c).edit().clear().putBoolean("alarm_profile_130", true).putBoolean("fullscreen", false).commit();
    for (int i = 0; i < 15; i++) {
      Medication med = new Medication(); med.name = "Exemplo de rolagem " + i;
      med.times = "09:00,21:00"; med.stock = 30; db.save(med, System.currentTimeMillis());
    }
    Activity[] main = { startActivitySync(new Intent(c, MainActivity.class)
        .putExtra("tab", 3).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) };
    waitForIdleSync();
    await(() -> scroll(main[0]).getHeight() > 0);
    String[] keys = AlarmTones.keys();
    check(keys.length == 7, "sete opções de toque disponíveis sem baixar arquivos externos");
    for (String key : keys) {
      MediaPlayer player = new MediaPlayer();
      try {
        player.setAudioAttributes(AlarmAudio.attributes());
        player.setDataSource(c, AlarmTones.uri(c, key)); player.prepare(); player.start();
        check(player.getDuration() >= 1000 && player.isPlaying(), "Android abre e inicia o áudio " + key);
      } finally { player.release(); }
      Alerts.selectTone(c, key);
      NotificationChannel channel = nm.getNotificationChannel(Alerts.channelId(c));
      check(channel.getSound().equals(AlarmTones.uri(c, key))
          && Alerts.notification(c, null, false).getChannelId().equals(channel.getId()),
          "aviso e serviço utilizam o canal do toque " + key);
    }
    int count = nm.getNotificationChannels().size();
    Alerts.selectTone(c, "sinos"); Alerts.selectTone(c, "digital"); Alerts.selectTone(c, "sinos");
    check(nm.getNotificationChannels().size() == count, "trocar e voltar a um toque reutiliza seus canais");
    boolean rejected = false;
    try { Alerts.selectTone(c, "inexistente"); } catch (IllegalArgumentException expected) { rejected = true; }
    check(rejected && "sinos".equals(AlarmTones.key(c)), "opção inválida é recusada sem mudar a escolha");

    onMain(() -> scroll(main[0]).scrollTo(0, 1400)); waitForIdleSync();
    ScrollView expected = scroll(main[0]); int y = expected.getScrollY();
    check(y > 500, "tela longa posicionada abaixo do topo para reproduzir o problema");
    c.sendBroadcast(new Intent("br.com.well.dose.CHANGED").setPackage(c.getPackageName()));
    SystemClock.sleep(600); waitForIdleSync();
    check(scroll(main[0]) == expected && Math.abs(scroll(main[0]).getScrollY() - y) <= 1,
        "atualização dos dados mantém a mesma área de rolagem e posição");
    View beforeClock = firstItem(expected);
    SystemClock.sleep(31500); waitForIdleSync();
    check(firstItem(expected) != beforeClock, "atualização periódica real de 30 segundos foi executada");
    check(scroll(main[0]) == expected && Math.abs(expected.getScrollY() - y) <= 1,
        "relógio atualiza o conteúdo sem levar a tela ao topo");

    onMain(() -> press(main[0], "Toque do alarme")); waitForIdleSync();
    onMain(() -> dialogChoice("Digital"));
    check("sinos".equals(AlarmTones.key(c)), "ouvir uma prévia não salva uma escolha ainda não confirmada");
    onMain(() -> dialogButton("Cancelar")); waitForIdleSync();
    check("sinos".equals(AlarmTones.key(c)), "Cancelar preserva o toque anterior");
    onMain(() -> press(main[0], "Toque do alarme")); waitForIdleSync();
    onMain(() -> dialogChoice("Suave"));
    onMain(() -> dialogButton("Usar toque")); waitForIdleSync();
    check("suave".equals(AlarmTones.key(c)), "botão real Usar toque salva a seleção");
    check(Math.abs(scroll(main[0]).getScrollY() - y) <= 1, "salvar o toque mantém a rolagem de Alertas");

    onMain(() -> press(main[0], "Remédios")); waitForIdleSync();
    onMain(() -> scroll(main[0]).scrollTo(0, 500)); waitForIdleSync();
    int medicinesY = scroll(main[0]).getScrollY();
    onMain(() -> press(main[0], "Alertas")); waitForIdleSync();
    await(() -> Math.abs(scroll(main[0]).getScrollY() - y) <= 1);
    check(true, "voltar a Alertas recupera sua posição independente");
    onMain(() -> press(main[0], "Remédios")); waitForIdleSync();
    await(() -> Math.abs(scroll(main[0]).getScrollY() - medicinesY) <= 1);
    check(medicinesY > 0, "Remédios mantém sua própria posição ao trocar de aba");
    onMain(() -> press(main[0], "Alertas")); waitForIdleSync();
    View beforeEditor = firstItem(scroll(main[0]));
    Activity editor = openEditor(main[0]);
    waitForIdleSync(); onMain(editor::finish); waitForIdleSync();
    await(() -> firstItem(scroll(main[0])) != beforeEditor
        && Math.abs(scroll(main[0]).getScrollY() - y) <= 1);
    check(true, "voltar de outro cadastro não redefine a rolagem");
    ActivityMonitor monitor = addMonitor(MainActivity.class.getName(), null, false);
    onMain(() -> main[0].recreate());
    Activity recreated = waitForMonitorWithTimeout(monitor, 30000); removeMonitor(monitor);
    check(recreated != null, "atividade foi recriada como em mudança de orientação ou aparência");
    main[0] = recreated; waitForIdleSync();
    await(() -> Math.abs(scroll(main[0]).getScrollY() - y) <= 1);
    check("suave".equals(AlarmTones.key(c)), "recriação preserva a rolagem e o toque escolhido");

    AudioManager audio = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
    int volume = audio.getStreamVolume(AudioManager.STREAM_ALARM), mode = audio.getRingerMode();
    long beforeAudio = System.currentTimeMillis();
    Alerts.test(c); c.startService(new Intent(c, RingingService.class).setAction("TEST"));
    await(() -> Prefs.get(c).getLong("last_audio", 0) >= beforeAudio);
    check(nm.getActiveNotifications().length == 1
        && nm.getActiveNotifications()[0].getNotification().getChannelId().equals(Alerts.channelId(c)),
        "serviço toca o novo som com uma única notificação de teste");
    Prefs.get(c).edit().putBoolean("test_visible", false).commit();
    RingingService.stopTest(c); Alerts.cancel(c, Alerts.TEST_ID);
    await(() -> Prefs.get(c).getLong("ringing_until", 0) == 0);
    check(audio.getStreamVolume(AudioManager.STREAM_ALARM) == volume && audio.getRingerMode() == mode,
        "encerrar devolve o volume anterior sem alterar o modo do celular");
    byte[] snapshot = BackupFiles.snapshot(c);
    check("suave".equals(new JSONObject(new String(snapshot, "UTF-8")).getJSONObject("settings").getString(AlarmTones.PREF)),
        "backup portátil inclui o identificador do toque");
    Alerts.selectTone(c, "digital"); BackupFiles.restore(c, snapshot);
    check("suave".equals(AlarmTones.key(c)), "restaurar recupera o toque e seus recursos locais");
    JSONObject bad = new JSONObject(new String(snapshot, "UTF-8"));
    bad.getJSONObject("settings").put(AlarmTones.PREF, "arquivo-externo-invalido");
    String meds = db.exportJson().getJSONArray("meds").toString(); rejected = false;
    try { BackupFiles.restore(c, bad.toString().getBytes("UTF-8")); } catch (java.io.IOException expectedError) { rejected = true; }
    check(rejected && meds.equals(db.exportJson().getJSONArray("meds").toString()) && "suave".equals(AlarmTones.key(c)),
        "toque inválido no backup é rejeitado antes de alterar dados");
    bad.getJSONObject("settings").remove(AlarmTones.PREF);
    BackupFiles.restore(c, bad.toString().getBytes("UTF-8"));
    check("original".equals(AlarmTones.key(c)), "backup antigo sem toque permanece compatível");
    Activity backupScreen = openEditor(main[0]);
    waitForIdleSync();
    bad.getJSONObject("settings").put("theme", "dark");
    BackupFiles.restore(c, bad.toString().getBytes("UTF-8"));
    onMain(backupScreen::finish); waitForIdleSync();
    await(() -> darkBackground(main[0])
        && Math.abs(scroll(main[0]).getScrollY() - y) <= 1);
    check(true,
        "tema restaurado atualiza a tela retida sem perder a rolagem");
    onMain(() -> main[0].finish());
  }
}
