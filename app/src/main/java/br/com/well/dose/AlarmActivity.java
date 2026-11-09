package br.com.well.dose;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.util.*;

/** Actual alarm screen, with persistent actions outside the scrolling medicine details. */
public final class AlarmActivity extends Activity {
  private long requested;
  private boolean registered;
  private final BroadcastReceiver changed =
      new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
          render();
        }
      };

  @Override
  public void onCreate(Bundle state) {
    Ui.prepare(this);
    super.onCreate(state);
    if (Build.VERSION.SDK_INT >= 27) {
      setShowWhenLocked(true);
      setTurnScreenOn(true);
    } else
      getWindow()
          .addFlags(
              WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                  | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    requested = getIntent().getLongExtra("dose_id", 0);
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    requested = intent.getLongExtra("dose_id", 0);
    render();
  }

  @Override
  protected void onResume() {
    super.onResume();
    AlarmEngine.refresh(this);
    if (Build.VERSION.SDK_INT >= 33)
      registerReceiver(
          changed, new IntentFilter("br.com.well.dose.CHANGED"), Context.RECEIVER_NOT_EXPORTED);
    else registerReceiver(changed, new IntentFilter("br.com.well.dose.CHANGED"));
    registered = true;
    render();
    Prefs.get(this).edit().putLong("last_fullscreen", System.currentTimeMillis()).apply();
  }

  @Override
  protected void onPause() {
    if (registered) {
      unregisterReceiver(changed);
      registered = false;
    }
    super.onPause();
  }

  private List<Dose> pending() {
    return Store.get(this)
        .doses(
            "status='pending' AND last_alert>0 AND snoozed=0 AND reminder_cancelled=0 AND"
                + " expires>?",
            new String[] {"" + System.currentTimeMillis()},
            "scheduled,id");
  }

  private LinearLayout footer(LinearLayout root) {
    LinearLayout b = Ui.column(this);
    b.setPadding(dp(22), dp(10), dp(22), dp(12));
    b.setBackgroundColor(Ui.BG);
    root.addView(b);
    return b;
  }

  private void render() {
    LinearLayout root = Ui.root(this);
    LinearLayout header = Ui.row(this);
    header.setPadding(dp(22), dp(18), dp(22), dp(10));
    header.addView(
        Ui.text(this, "dose", 24, Ui.TEAL, true), new LinearLayout.LayoutParams(0, -2, 1));
    header.addView(Ui.text(this, LocalTime.now().format(ScheduleRules.CLOCK), 22, Ui.INK, true));
    root.addView(header);
    LinearLayout body = Ui.scroll(root);
    Ui.title(body, requested == -1 ? "Alarme de teste" : "Hora do seu medicamento", "");
    KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
    if (requested != -1 && keyguard.isKeyguardLocked() && Prefs.privateAlarm(this)) {
      Ui.add(
          body,
          Ui.text(
              this, "Desbloqueie para ver os detalhes e confirmar a dose.", 17, Ui.MUTED, false),
          24);
      LinearLayout controls = footer(root);
      controls.addView(
          Ui.button(
              this,
              "Desbloquear e ver lembrete",
              true,
              () ->
                  keyguard.requestDismissKeyguard(
                      this,
                      new KeyguardManager.KeyguardDismissCallback() {
                        @Override
                        public void onDismissSucceeded() {
                          render();
                        }
                      })));
      return;
    }
    if (requested == -1) {
      LinearLayout.LayoutParams bellLayout = new LinearLayout.LayoutParams(dp(64), dp(64));
      bellLayout.topMargin = dp(24);
      body.addView(Ui.icon(this, "bell", Ui.TEAL, 64), bellLayout);
      Ui.add(
          body,
          Ui.text(this, "Este é um teste. Nenhuma dose será registrada.", 17, Ui.MUTED, false),
          22);
      Ui.add(
          body,
          Ui.text(
              this, "Use os botões abaixo para encerrar ou adiar o alarme.", 15, Ui.MUTED, false),
          16);
      LinearLayout controls = footer(root);
      controls.addView(
          Ui.button(
              this,
              "Funcionou • encerrar teste",
              true,
              () -> {
                AlarmEngine.testAction(this, "END_TEST");
                finish();
              }));
      Ui.add(
          controls,
          Ui.button(
              this,
              "Adiar teste por " + Prefs.snooze(this) + " minutos",
              false,
              () -> {
                AlarmEngine.testAction(this, "SNOOZE_TEST");
                finish();
              }),
          9);
      return;
    }
    List<Dose> list = pending();
    if (list.isEmpty()) {
      Ui.add(
          body,
          Ui.text(this, "Tudo certo. Não há doses aguardando confirmação.", 17, Ui.MUTED, false),
          24);
      footer(root)
          .addView(
              Ui.button(
                  this,
                  "Voltar à minha rotina",
                  true,
                  () -> {
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                  }));
      return;
    }
    Dose selected = list.stream().filter(d -> d.id == requested).findFirst().orElse(list.get(0));
    if (list.size() > 1)
      Ui.add(
          body,
          Ui.text(
              this,
              list.size() + " medicamentos aguardam confirmação. Toque em um para selecionar.",
              14,
              Ui.MUTED,
              false),
          16);
    // Keep the selected medicine first, so the fixed actions always match the visible details.
    list.remove(selected);
    list.add(0, selected);
    for (Dose d : list) {
      LinearLayout card = Ui.card(this);
      LinearLayout title = Ui.row(this);
      title.addView(Ui.medicine(this, d.icon, d.color, d.secondaryColor, 70));
      LinearLayout words = Ui.column(this);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
      lp.leftMargin = dp(14);
      title.addView(words, lp);
      words.addView(Ui.text(this, d.name, 25, Ui.INK, true));
      Ui.add(words, Ui.text(this, d.detail, 18, Ui.MUTED, false), 10);
      card.addView(title);
      String scheduled =
          Instant.ofEpochMilli(d.scheduled)
              .atZone(ZoneId.systemDefault())
              .toLocalTime()
              .format(ScheduleRules.CLOCK);
      Ui.add(card, Ui.text(this, "Horário previsto: " + scheduled, 17, Ui.TEAL, true), 20);
      if (!d.notes.isEmpty()) Ui.add(card, Ui.text(this, d.notes, 16, Ui.INK, false), 16);
      Medication med = Store.get(this).medication(d.medId);
      if (med != null && med.stock >= 0 && med.stock <= 5)
        Ui.add(
            card,
            Ui.text(
                this,
                "Estoque: " + ScheduleRules.number(med.stock) + " " + med.unit,
                14,
                Ui.AMBER,
                true),
            14);
      if (list.size() > 1) {
        Ui.add(
            card,
            Ui.chip(
                this,
                d.id == selected.id ? "Selecionado" : "Toque para selecionar",
                Ui.LIGHT,
                Ui.TEAL),
            14);
        card.setOnClickListener(
            v -> {
              requested = d.id;
              render();
            });
      }
      Ui.add(body, card, 20);
    }
    TextView skip = Ui.text(this, "Pular esta dose", 14, Ui.MUTED, false);
    skip.setGravity(Gravity.CENTER);
    skip.setMinHeight(dp(48));
    skip.setOnClickListener(
        v ->
            new AlertDialog.Builder(this)
                .setTitle("Pular esta dose?")
                .setMessage("O histórico mostrará que você pulou esta dose.")
                .setNegativeButton("Voltar", null)
                .setPositiveButton("Pular", (a, b) -> act(selected.id, "SKIP"))
                .show());
    Ui.add(body, skip, 8);
    LinearLayout controls = footer(root);
    controls.addView(
        Ui.button(this, "✓  Tomei este medicamento", true, () -> act(selected.id, "TAKE")));
    Ui.add(
        controls,
        Ui.button(
            this,
            "Adiar por " + Prefs.snooze(this) + " minutos",
            false,
            () -> act(selected.id, "SNOOZE")),
        9);
    TextView silence = Ui.text(this, "Silenciar apenas o som", 13, Ui.MUTED, true);
    silence.setGravity(Gravity.CENTER);
    silence.setMinHeight(dp(44));
    silence.setOnClickListener(v -> RingingService.stopSound(this));
    Ui.add(controls, silence, 2);
  }

  private void act(long id, String action) {
    AlarmEngine.action(this, id, action);
    requested = 0;
    if (pending().isEmpty()) finish();
    else render();
  }

  private int dp(int value) {
    return Ui.dp(this, value);
  }
}
