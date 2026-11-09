package br.com.well.dose;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Real Android integration checks; install on an isolated emulator only. */
public final class DeviceTests extends Instrumentation {
  private Bundle args;
  private Context c;
  private Store db;
  private int passed;
  private final StringBuilder report = new StringBuilder();

  @Override
  public void onCreate(Bundle arguments) {
    super.onCreate(arguments);
    args = arguments;
    start();
  }

  @Override
  public void onStart() {
    c = getTargetContext();
    db = Store.get(c);
    Bundle result = new Bundle();
    try {
      String mode = args.getString("mode", "all");
      if ("seed".equals(mode)) seed();
      else if ("verify".equals(mode)) verify();
      else if ("showcase".equals(mode)) showcase();
      else if ("regression".equals(mode)) regression();
      else if ("seed_test".equals(mode)) {
        clear();
        Prefs.get(c)
            .edit()
            .putString("theme", "dark")
            .putBoolean("private", true)
            .putBoolean("fullscreen", false)
            .commit();
        AlarmEngine.testIn(c, 15);
        check(
            Prefs.get(c).getLong("test_at", 0) > System.currentTimeMillis(),
            "teste real programado para 15 segundos, com privacidade ativada");
      } else if ("verify_test".equals(mode)) {
        check(
            Prefs.get(c).getLong("last_delivery", 0) > 0,
            "teste de 15 segundos entregue após encerrar o processo e bloquear a tela");
        check(
            Prefs.get(c).getLong("last_audio", 0) > 0,
            "som do teste executado pelo serviço em segundo plano");
        check(
            db.medications().isEmpty() && db.doses(null, null, "id").isEmpty(),
            "teste real não criou medicamentos nem histórico");
      } else if ("theme".equals(mode)) {
        Prefs.get(c).edit().putString("theme", args.getString("theme", "system")).commit();
        check(true, "preferência de aparência aplicada para inspeção visual");
      } else tests();
      result.putString("stream", report + "\nPASS " + passed + " device checks\n");
      result.putString("result", "PASS");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable e) {
      result.putString("stream", report + "\nFAIL: " + android.util.Log.getStackTraceString(e));
      result.putString("result", "FAIL");
      finish(Activity.RESULT_CANCELED, result);
    }
  }

  private void check(boolean value, String text) {
    if (!value) throw new AssertionError(text);
    passed++;
    report.append("PASS ").append(text).append('\n');
    Bundle status = new Bundle();
    status.putString("stream", text + "\n");
    sendStatus(0, status);
  }

  private void clear() {
    c.stopService(new Intent(c, RingingService.class));
    ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).cancelAll();
    db.getWritableDatabase().delete("doses", null, null);
    db.getWritableDatabase().delete("meds", null, null);
    Prefs.get(c).edit().clear().commit();
  }

  private Medication medication(String name) {
    Medication m = new Medication();
    m.name = name;
    m.stock = 30;
    m.notes = "Exemplo fictício para teste";
    m.start = LocalDate.now().toString();
    m.times = "08:00,20:00";
    db.save(m, System.currentTimeMillis());
    return m;
  }

  private Dose future(long medId) {
    return db.doses("med_id=? AND status='pending'", new String[] {"" + medId}, "scheduled LIMIT 1")
        .get(0);
  }

  private void tests() throws Exception {
    clear();
    Medication m = medication("Medicamento de teste");
    db.maintain(System.currentTimeMillis());
    List<Dose> initial = db.doses(null, null, "id");
    db.maintain(System.currentTimeMillis());
    check(
        initial.size() == db.doses(null, null, "id").size(),
        "SQLite não duplica ocorrências ao reagendar");
    Dose first = future(m.id);
    check(db.resolve(first.id, "taken", System.currentTimeMillis()), "confirmação inicial de dose");
    check(
        !db.resolve(first.id, "taken", System.currentTimeMillis()),
        "duplo clique não confirma a mesma dose duas vezes");
    check(db.medication(m.id).stock == 29, "estoque descontado exatamente uma vez");
    check(db.dose(first.id).nextAlert == 0, "confirmação encerra repetições");
    Dose second = future(m.id);
    long before = System.currentTimeMillis();
    check(db.snooze(second.id, 10, before), "adiamento persistido");
    db.close();
    Dose read = db.dose(second.id);
    check(
        read.snoozed && read.nextAlert == before + 600000,
        "adiamento sobrevive à reabertura do banco");
    ContentValues dueValues = new ContentValues();
    long now = System.currentTimeMillis();
    dueValues.put("scheduled", now - 1000);
    dueValues.put("expires", now + 3600000);
    dueValues.put("next_alert", now - 500);
    db.getWritableDatabase().update("doses", dueValues, "id=?", new String[] {"" + second.id});
    List<Dose> due = db.claimDue(c, now);
    check(due.size() == 1, "despacho entrega somente a ocorrência vencida");
    check(db.claimDue(c, now).isEmpty(), "reentrega do broadcast não duplica o aviso");
    check(db.dose(second.id).nextAlert == now + 300000, "próxima repetição persistida");
    Dose active = db.dose(second.id);
    Alerts.show(c, active, false);
    await(
        () ->
            Arrays.stream(
                    ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE))
                        .getActiveNotifications())
                .anyMatch(n -> n.getId() == (int) second.id));
    NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
    check(
        Arrays.stream(nm.getActiveNotifications()).anyMatch(n -> n.getId() == (int) second.id),
        "notificação real publicada no Android");
    Notification notification =
        Arrays.stream(nm.getActiveNotifications())
            .filter(n -> n.getId() == (int) second.id)
            .findFirst()
            .get()
            .getNotification();
    check(notification.actions.length == 2, "notificação tem os botões Tomei e Adiar");
    android.media.MediaPlayer sound = android.media.MediaPlayer.create(c, R.raw.dose_alarm);
    check(sound != null, "som próprio do aplicativo carregado e preparado pelo Android");
    if (sound != null) sound.release();
    notification.actions[1].actionIntent.send();
    await(
        () ->
            db.dose(second.id).snoozed
                && Arrays.stream(nm.getActiveNotifications())
                    .noneMatch(n -> n.getId() == (int) second.id));
    check(db.dose(second.id).snoozed, "botão Adiar aciona o receptor real");
    check(
        Arrays.stream(nm.getActiveNotifications()).noneMatch(n -> n.getId() == (int) second.id),
        "adiar cancela apenas o aviso correspondente");
    Dose third = future(m.id);
    if (third.id == second.id) {
      db.resolve(second.id, "skipped", now);
      third = future(m.id);
    }
    Alerts.show(c, third, false);
    final long thirdId = third.id;
    await(
        () -> Arrays.stream(nm.getActiveNotifications()).anyMatch(n -> n.getId() == (int) thirdId));
    Notification thirdNotice =
        Arrays.stream(nm.getActiveNotifications())
            .filter(n -> n.getId() == (int) thirdId)
            .findFirst()
            .get()
            .getNotification();
    thirdNotice.actions[0].actionIntent.send();
    await(() -> "taken".equals(db.dose(thirdId).status));
    check("taken".equals(db.dose(third.id).status), "botão Tomei aciona o receptor real");
    db.setEnabled(m.id, false, false);
    check(
        db.doses("med_id=? AND status='pending'", new String[] {"" + m.id}, "id").isEmpty(),
        "pausar cancela todos os lembretes pendentes");
    db.maintain(now);
    check(
        db.doses("med_id=? AND status='pending'", new String[] {"" + m.id}, "id").isEmpty(),
        "manutenção não reativa medicamento pausado");
    db.setEnabled(m.id, true, false);
    db.maintain(now);
    check(
        !db.doses("med_id=? AND status='pending'", new String[] {"" + m.id}, "id").isEmpty(),
        "retomar regenera os próximos lembretes");
    JSONObject backup = db.exportJson();
    int all = db.doses(null, null, "id").size();
    db.importJson(new JSONObject(backup.toString()));
    check(
        db.doses(null, null, "id").size() == all && db.medication(m.id).stock == 28,
        "backup mantém histórico, IDs e estoque");
    JSONObject broken = new JSONObject(backup.toString());
    broken.getJSONArray("meds").getJSONObject(0).put("amount", -3);
    boolean rejected = false;
    try {
      db.importJson(broken);
    } catch (Exception e) {
      rejected = true;
    }
    check(
        rejected && db.doses(null, null, "id").size() == all,
        "backup inválido é rejeitado sem apagar dados");
    JSONObject incomplete = new JSONObject(backup.toString());
    incomplete.getJSONArray("doses").getJSONObject(0).remove("detail");
    boolean rollback = false;
    try {
      db.importJson(incomplete);
    } catch (Exception e) {
      rollback = true;
    }
    check(
        rollback && db.doses(null, null, "id").size() == all && db.medication(m.id).stock == 28,
        "erro durante restauração reverte a transação inteira");
    m = db.medication(m.id);
    m.times = "00:01,23:59";
    long edited = System.currentTimeMillis();
    db.save(m, edited);
    db.maintain(edited);
    check(
        db.doses(
                "med_id=? AND status='pending' AND scheduled<? AND last_alert=0",
                new String[] {"" + m.id, "" + edited},
                "id")
            .isEmpty(),
        "editar horários não gera alarmes retroativos");
    AlarmEngine.refresh(c);
    check(AlarmEngine.exact(c), "permissão de alarme exato concedida no emulador");
    AlarmManager manager = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
    check(
        manager.getNextAlarmClock() != null
            && manager.getNextAlarmClock().getTriggerTime()
                == Prefs.get(c).getLong("next_alarm", 0),
        "próximo alarme realmente registrado no sistema");
    clear();
    AlarmEngine.refresh(c);
  }

  private void seed() {
    clear();
    Medication m = medication("Lembrete de teste");
    db.maintain(System.currentTimeMillis());
    Dose d = future(m.id);
    long at = System.currentTimeMillis() + 45000;
    ContentValues v = new ContentValues();
    v.put("scheduled", at);
    v.put("next_alert", at);
    v.put("expires", at + 3600000);
    db.getWritableDatabase().update("doses", v, "id=?", new String[] {"" + d.id});
    Prefs.get(c)
        .edit()
        .putLong("verify_dose", d.id)
        .putBoolean("private", false)
        .putBoolean("fullscreen", false)
        .commit();
    AlarmEngine.refresh(c);
    report.append("TARGET_AT=").append(at).append('\n');
    check(true, "dose real agendada para 45 segundos após finalizar o processo de teste");
  }

  private void await(BooleanSupplier condition) {
    long deadline = SystemClock.elapsedRealtime() + 15000;
    while (!condition.getAsBoolean() && SystemClock.elapsedRealtime() < deadline)
      SystemClock.sleep(150);
    if (!condition.getAsBoolean())
      throw new AssertionError("O Android não concluiu a operação assíncrona em 15 segundos.");
  }

  private Notification posted(int id) {
    return Arrays.stream(
            ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE))
                .getActiveNotifications())
        .filter(n -> n.getId() == id)
        .findFirst()
        .get()
        .getNotification();
  }

  private List<View> views(View root) {
    List<View> result = new ArrayList<>();
    result.add(root);
    if (root instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) root;
      for (int i = 0; i < group.getChildCount(); i++) result.addAll(views(group.getChildAt(i)));
    }
    return result;
  }

  private boolean hasText(Activity a, String value) {
    return views(a.getWindow().getDecorView()).stream()
        .anyMatch(v -> v instanceof TextView && ((TextView) v).getText().toString().equals(value));
  }

  private double luminance(int color) {
    double result = 0;
    int[] shift = {16, 8, 0};
    double[] weight = {.2126, .7152, .0722};
    for (int i = 0; i < 3; i++) {
      double n = ((color >> shift[i]) & 255) / 255.0;
      result += weight[i] * (n <= .04045 ? n / 12.92 : Math.pow((n + .055) / 1.055, 2.4));
    }
    return result;
  }

  private void inspectCalendar(Activity a, String mode) {
    View selected =
        views(a.getWindow().getDecorView()).stream()
            .filter(v -> v.isSelected() && v instanceof LinearLayout)
            .findFirst()
            .get();
    LinearLayout day = (LinearLayout) selected;
    check(day.getWidth() > 0 && day.getChildCount() == 2, "dia selecionado medido no tema " + mode);
    for (int i = 0; i < 2; i++) {
      TextView label = (TextView) day.getChildAt(i);
      android.text.Layout layout = label.getLayout();
      double center =
          label.getLeft()
              + label.getPaddingLeft()
              + (layout.getLineLeft(0) + layout.getLineRight(0)) / 2.0;
      check(
          Math.abs(center - day.getWidth() / 2.0) <= 1.5,
          (i == 0 ? "nome" : "número") + " do dia centralizado dentro do destaque no tema " + mode);
      double aLum = luminance(label.getCurrentTextColor()), bLum = luminance(Ui.SELECTED);
      check(
          (Math.max(aLum, bLum) + .05) / (Math.min(aLum, bLum) + .05) >= 4.5,
          "contraste legível do " + (i == 0 ? "nome" : "número") + " do dia no tema " + mode);
    }
  }

  private void regression() throws Exception {
    clear();
    Medication m = medication("Exemplo para regressão");
    db.maintain(System.currentTimeMillis());
    String meds = db.exportJson().getJSONArray("meds").toString();
    String doses = db.exportJson().getJSONArray("doses").toString();
    Prefs.get(c).edit().putBoolean("private", true).putBoolean("fullscreen", false).commit();
    Alerts.test(c);
    await(
        () ->
            Arrays.stream(
                    ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE))
                        .getActiveNotifications())
                .anyMatch(n -> n.getId() == Alerts.TEST_ID));
    Notification test = posted(Alerts.TEST_ID);
    check(
        test.actions != null && test.actions.length == 2,
        "notificação de teste publicada com Encerrar e Adiar");
    check(
        test.visibility == Notification.VISIBILITY_PUBLIC && test.publicVersion == null,
        "controles do teste disponíveis sem expor medicamentos na tela bloqueada");
    if (Build.VERSION.SDK_INT >= 31)
      check(!test.actions[0].isAuthenticationRequired(), "encerrar teste dispensa desbloqueio");
    long before = System.currentTimeMillis();
    test.actions[1].actionIntent.send();
    await(() -> Prefs.get(c).getLong("test_at", 0) > before);
    long next = Prefs.get(c).getLong("test_at", 0);
    check(
        next >= before + Prefs.snooze(c) * 60000L
            && next < System.currentTimeMillis() + Prefs.snooze(c) * 60000L + 1000,
        "Adiar da notificação reagenda somente o teste pelo intervalo escolhido");
    await(
        () ->
            Arrays.stream(
                    ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE))
                        .getActiveNotifications())
                .noneMatch(n -> n.getId() == Alerts.TEST_ID));
    check(true, "Adiar remove o aviso de teste anterior");
    Alerts.test(c);
    await(
        () ->
            Arrays.stream(
                    ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE))
                        .getActiveNotifications())
                .anyMatch(n -> n.getId() == Alerts.TEST_ID));
    posted(Alerts.TEST_ID).actions[0].actionIntent.send();
    await(() -> Prefs.get(c).getLong("test_at", 0) == 0);
    check(true, "Encerrar cancela também o próximo teste adiado");
    check(
        meds.equals(db.exportJson().getJSONArray("meds").toString())
            && doses.equals(db.exportJson().getJSONArray("doses").toString())
            && db.medication(m.id).stock == 30,
        "ações do teste preservam medicamentos, estoque e todas as ocorrências");
    Notification service = Alerts.serviceNotification(c, true);
    check(
        service.actions.length == 2 && service.visibility == Notification.VISIBILITY_PUBLIC,
        "aviso do som de teste também oferece Encerrar e Adiar");
    ActivityMonitor monitor = addMonitor(AlarmActivity.class.getName(), null, false);
    service.contentIntent.send();
    Activity alarm = waitForMonitorWithTimeout(monitor, 15000);
    check(alarm != null, "toque no aviso do som abre a atividade real de alarme");
    waitForIdleSync();
    runOnMainSync(
        () -> {
          check(
              hasText(alarm, "Alarme de teste")
                  && hasText(alarm, "Adiar teste por " + Prefs.snooze(c) + " minutos")
                  && hasText(alarm, "Funcionou • encerrar teste"),
              "aviso do som abre os controles do teste em vez da lista de doses");
          alarm.finish();
        });
    removeMonitor(monitor);
    clear();
    for (String mode : new String[] {"light", "dark"}) {
      Prefs.get(c).edit().putString("theme", mode).commit();
      Activity main =
          startActivitySync(
              new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      waitForIdleSync();
      runOnMainSync(
          () -> {
            View root = ((ViewGroup) main.findViewById(android.R.id.content)).getChildAt(0);
            int bg = ((android.graphics.drawable.ColorDrawable) root.getBackground()).getColor();
            check(
                "dark".equals(mode) ? luminance(bg) < .03 : luminance(bg) > .9,
                "fundo da agenda respeita o modo " + mode);
            inspectCalendar(main, mode);
          });
      Activity editor =
          startActivitySync(
              new Intent(c, EditorActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      waitForIdleSync();
      runOnMainSync(
          () -> {
            List<View> fields = views(editor.getWindow().getDecorView());
            EditText field =
                (EditText) fields.stream().filter(v -> v instanceof EditText).findFirst().get();
            double fg = luminance(field.getCurrentTextColor()), bg = luminance(Ui.SURFACE);
            check(
                (Math.max(fg, bg) + .05) / (Math.min(fg, bg) + .05) >= 4.5,
                "campo de cadastro mantém contraste no modo " + mode);
            android.util.TypedValue color = new android.util.TypedValue();
            new AlertDialog.Builder(editor)
                .getContext()
                .getTheme()
                .resolveAttribute(android.R.attr.textColorPrimary, color, true);
            int value = color.resourceId == 0 ? color.data : editor.getColor(color.resourceId);
            check(
                "dark".equals(mode) ? luminance(value) > .5 : luminance(value) < .1,
                "diálogos nativos acompanham o modo " + mode);
            editor.finish();
            main.finish();
          });
    }
    Prefs.get(c).edit().putString("theme", "dark").commit();
  }

  private void verify() {
    long id = Prefs.get(c).getLong("verify_dose", 0);
    Dose d = db.dose(id);
    check(
        d != null && d.lastAlert > 0, "alarme executado com app fora da tela e aparelho bloqueado");
    // The CLI verifies live notifications before starting this instrumentation,
    // because starting instrumentation can terminate the target app's services.
    check(d.nextAlert > d.lastAlert, "repetição reagendada após entrega em segundo plano");
    check(
        Prefs.get(c).getLong("last_audio", 0) > 0,
        "áudio do alarme iniciado pelo serviço em segundo plano");
  }

  private void showcase() {
    clear();
    LocalDate date = LocalDate.now();
    LocalTime now = LocalTime.now().withSecond(0).withNano(0);
    Medication a = medication("Medicamento A");
    a.times =
        now.minusMinutes(15).format(ScheduleRules.CLOCK)
            + ","
            + now.plusHours(6).format(ScheduleRules.CLOCK);
    a.strength = "10 mg";
    a.notes = "Exemplo fictício • após uma refeição";
    a.stock = 24;
    db.save(a, System.currentTimeMillis() - 3600000);
    Medication b = medication("Vitamina de exemplo");
    b.times = now.plusHours(2).format(ScheduleRules.CLOCK);
    b.color = 0xFF9B75B4;
    b.unit = "cápsula(s)";
    db.save(b, System.currentTimeMillis() - 3600000);
    db.maintain(System.currentTimeMillis());
    for (Dose d : db.forDate(LocalDate.now()))
      if (d.scheduled < System.currentTimeMillis()) db.resolve(d.id, "taken", d.scheduled + 120000);
    check(true, "dados fictícios criados apenas no emulador para inspeção visual");
  }
}
