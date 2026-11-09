package br.com.well.dose;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.util.*;

public final class AlarmActivity extends Activity {
  private long requested = 0;
  private boolean registered;
  private final BroadcastReceiver changed =
      new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
          render();
        }
      };

  @Override
  public void onCreate(Bundle b) {
    Ui.prepare(this);
    super.onCreate(b);
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
  protected void onNewIntent(Intent i) {
    super.onNewIntent(i);
    setIntent(i);
    requested = i.getLongExtra("dose_id", 0);
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
  }

  @Override
  protected void onPause() {
    if (registered) {
      unregisterReceiver(changed);
      registered = false;
    }
    super.onPause();
  }

  private void render() {
    LinearLayout root = Ui.root(this);
    LinearLayout body = Ui.scroll(root);
    body.setGravity(Gravity.CENTER_HORIZONTAL);
    Ui.add(body, Ui.text(this, "dose", 28, Ui.TEAL, true), 28);
    Ui.add(body, Ui.gap(this, 24), 0);
    body.addView(Ui.icon(this, "bell", Ui.TEAL, 64));
    TextView title =
        Ui.text(
            this, requested == -1 ? "Alarme de teste" : "Hora do seu\ncuidado.", 34, Ui.INK, true);
    title.setGravity(Gravity.CENTER);
    Ui.add(body, title, 26);
    TextView clock =
        Ui.text(this, LocalTime.now().format(ScheduleRules.CLOCK), 22, Ui.MUTED, false);
    clock.setGravity(Gravity.CENTER);
    Ui.add(body, clock, 16);
    KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
    if (requested != -1
        && keyguard.isKeyguardLocked()
        && Prefs.get(this).getBoolean("private", true)) {
      TextView text =
          Ui.text(
              this,
              "Seus detalhes estão protegidos.\nDesbloqueie para ver e confirmar a dose.",
              15,
              Ui.MUTED,
              false);
      text.setGravity(Gravity.CENTER);
      Ui.add(body, text, 24);
      Ui.add(
          body,
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
                      })),
          24);
    } else if (requested == -1) {
      Ui.add(
          body,
          Ui.text(this, "Este é um teste. Nenhuma dose será registrada.", 15, Ui.MUTED, false),
          24);
      Ui.add(
          body,
          Ui.button(
              this,
              "Funcionou • encerrar teste",
              true,
              () -> {
                AlarmEngine.testAction(this, "END_TEST");
                finish();
              }),
          26);
      Ui.add(
          body,
          Ui.button(
              this,
              "Adiar teste por " + Prefs.snooze(this) + " minutos",
              false,
              () -> {
                AlarmEngine.testAction(this, "SNOOZE_TEST");
                Ui.toast(this, "Teste adiado por " + Prefs.snooze(this) + " minutos.");
                finish();
              }),
          12);
    } else {
      List<Dose> pending =
          Store.get(this)
              .doses(
                  "status='pending' AND last_alert>0 AND snoozed=0 AND expires>?",
                  new String[] {"" + System.currentTimeMillis()},
                  "scheduled,id");
      if (requested > 0) {
        Dose d = Store.get(this).dose(requested);
        if (d != null && d.actionable() && pending.stream().noneMatch(p -> p.id == d.id))
          pending.add(0, d);
      }
      if (pending.isEmpty()) {
        Ui.add(
            body,
            Ui.text(this, "Tudo certo. Não há doses aguardando confirmação.", 15, Ui.MUTED, false),
            25);
        Ui.add(
            body,
            Ui.button(
                this,
                "Voltar à minha rotina",
                true,
                () -> {
                  startActivity(new Intent(this, MainActivity.class));
                  finish();
                }),
            24);
      } else
        for (Dose d : pending) {
          LinearLayout card = Ui.card(this);
          LinearLayout heading = Ui.row(this);
          heading.addView(Ui.medicine(this, d.icon, d.color, d.secondaryColor, 60));
          TextView medicineName = Ui.text(this, d.name, 24, Ui.INK, true);
          LinearLayout.LayoutParams nameLayout = new LinearLayout.LayoutParams(0, -2, 1);
          nameLayout.leftMargin = Ui.dp(this, 14);
          heading.addView(medicineName, nameLayout);
          card.addView(heading);
          Ui.add(card, Ui.text(this, d.detail, 16, Ui.MUTED, false), 10);
          Ui.add(
              card,
              Ui.text(
                  this,
                  "Horário previsto: "
                      + Instant.ofEpochMilli(d.scheduled)
                          .atZone(ZoneId.systemDefault())
                          .toLocalTime()
                          .format(ScheduleRules.CLOCK),
                  13,
                  Ui.TEAL,
                  true),
              12);
          if (!d.notes.isEmpty()) Ui.add(card, Ui.text(this, d.notes, 15, Ui.MUTED, false), 12);
          Ui.add(
              card,
              Ui.button(
                  this,
                  "✓  Tomei este medicamento",
                  true,
                  () -> {
                    AlarmEngine.action(this, d.id, "TAKE");
                    requested = 0;
                    render();
                  }),
              21);
          Ui.add(
              card,
              Ui.button(
                  this,
                  "Lembrar em " + Prefs.snooze(this) + " minutos",
                  false,
                  () -> {
                    AlarmEngine.action(this, d.id, "SNOOZE");
                    requested = 0;
                    render();
                  }),
              10);
          TextView skip = Ui.text(this, "Pular esta dose", 13, Ui.MUTED, false);
          skip.setMinHeight(Ui.dp(this, 48));
          skip.setGravity(Gravity.CENTER);
          skip.setOnClickListener(
              v ->
                  new AlertDialog.Builder(this)
                      .setTitle("Pular esta dose?")
                      .setMessage("O histórico mostrará que você pulou esta dose.")
                      .setNegativeButton("Voltar", null)
                      .setPositiveButton(
                          "Pular",
                          (a, b) -> {
                            AlarmEngine.action(this, d.id, "SKIP");
                            requested = 0;
                            render();
                          })
                      .show());
          Ui.add(card, skip, 6);
          Ui.add(body, card, 27);
        }
    }
    TextView silence = Ui.text(this, "Silenciar apenas o som", 14, Ui.MUTED, true);
    silence.setGravity(Gravity.CENTER);
    silence.setMinHeight(Ui.dp(this, 52));
    silence.setOnClickListener(
        v -> {
          stopService(new Intent(this, RingingService.class));
          Ui.toast(this, "Som silenciado. O lembrete continua pendente.");
        });
    Ui.add(body, silence, 19);
  }
}
