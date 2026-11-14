package br.com.well.dose;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.database.sqlite.SQLiteException;
import android.graphics.drawable.RippleDrawable;
import android.os.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Actual alarm screen, with persistent actions outside the scrolling medicine details. */
public final class AlarmActivity extends Activity {
  private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");
  private long requested;
  private boolean registered;
  private AlertDialog snoozePicker;
  private ScrollView doseScroll;
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

  @Override
  protected void onDestroy() {
    if (snoozePicker != null) snoozePicker.dismiss();
    super.onDestroy();
  }

  private List<Dose> pending() {
    return Store.get(this).alertingDoses(System.currentTimeMillis());
  }

  private LinearLayout footer(LinearLayout root) {
    LinearLayout b = Ui.column(this);
    b.setPadding(dp(22), dp(10), dp(22), dp(12));
    b.setBackgroundColor(Ui.BG);
    root.addView(b);
    return b;
  }

  private void render() {
    int scrollY = doseScroll == null ? 0 : doseScroll.getScrollY();
    doseScroll = null;
    LinearLayout root = Ui.root(this);
    LinearLayout header = Ui.row(this);
    header.setPadding(dp(22), dp(14), dp(22), dp(6));
    header.addView(
        Ui.text(this, "dose", 24, Ui.TEAL, true), new LinearLayout.LayoutParams(0, -2, 1));
    header.addView(Ui.text(this, LocalTime.now().format(ScheduleRules.CLOCK), 22, Ui.INK, true));
    root.addView(header);
    LinearLayout body = Ui.scroll(root);
    KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
    if (requested != -1 && keyguard.isKeyguardLocked() && Prefs.privateAlarm(this)) {
      Ui.title(body, "Hora do medicamento", "");
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
      Ui.title(body, "Alarme de teste", "");
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
    boolean multiple = list.size() > 1;
    Ui.title(
        body,
        multiple ? "Hora dos medicamentos" : "Hora do medicamento",
        multiple ? "Toque em uma dose para selecioná-la." : "");
    if (list.isEmpty()) {
      getIntent().setAction(null);
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
    for (Dose d : list) Ui.add(body, doseCard(d, multiple, d.id == selected.id), 10);
    doseScroll = (ScrollView) body.getParent();
    if (scrollY > 0) restoreScroll(doseScroll, scrollY);
    TextView skip = Ui.text(this, "Pular esta dose", 14, Ui.MUTED, false);
    skip.setGravity(Gravity.CENTER);
    skip.setMinHeight(dp(48));
    skip.setOnClickListener(
        v ->
            new AlertDialog.Builder(this)
                .setTitle("Pular esta dose?")
                .setMessage(
                    selected.name + " · " + scheduledLabel(selected)
                        + "\n\nO histórico mostrará que você pulou esta dose.")
                .setNegativeButton("Voltar", null)
                .setPositiveButton("Pular", (a, b) -> act(selected.id, "SKIP"))
                .show());
    Ui.add(body, skip, 8);
    LinearLayout controls = footer(root);
    if (multiple) {
      Set<Long> displayedIds = new HashSet<>();
      for (Dose d : list) displayedIds.add(d.id);
      controls.addView(
          Ui.button(
              this,
              "✓  Tomei todos (" + displayedIds.size() + ")",
              true,
              () -> takeAll(displayedIds)));
      TextView target =
          Ui.text(this, scheduledLabel(selected) + " · " + selected.name, 13, Ui.MUTED, true);
      target.setMaxLines(1);
      target.setEllipsize(TextUtils.TruncateAt.END);
      Ui.add(controls, target, 12);
      LinearLayout individual = Ui.row(this);
      individual.addView(
          Ui.button(this, "Tomei esta dose", false, () -> act(selected.id, "TAKE")),
          new LinearLayout.LayoutParams(0, -2, 1));
      LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, -2, 1);
      right.leftMargin = dp(8);
      individual.addView(
          Ui.button(
              this, "Adiar " + Prefs.snooze(this) + " min", false, () -> act(selected.id, "SNOOZE")),
          right);
      Ui.add(controls, individual, 6);
    } else {
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
    }
    TextView silence = Ui.text(this, "Silenciar apenas o som", 13, Ui.MUTED, true);
    silence.setGravity(Gravity.CENTER);
    silence.setMinHeight(dp(48));
    silence.setOnClickListener(v -> RingingService.stopSound(this));
    TextView chooseTime = Ui.text(this, "Adiar até um horário", 14, Ui.TEAL, true);
    chooseTime.setGravity(Gravity.CENTER);
    chooseTime.setMinHeight(dp(48));
    chooseTime.setOnClickListener(v -> showSnoozeTime(selected));
    LinearLayout secondary = Ui.row(this);
    secondary.addView(chooseTime, new LinearLayout.LayoutParams(0, -2, 1));
    secondary.addView(silence, new LinearLayout.LayoutParams(0, -2, 1));
    Ui.add(controls, secondary, 2);
    if (Alerts.CHOOSE_SNOOZE_TIME.equals(getIntent().getAction())) {
      getIntent().setAction(null);
      if (selected.id == requested) showSnoozeTime(selected);
      else Ui.toast(this, "Este lembrete não está mais disponível.");
    }
  }

  private LinearLayout doseCard(Dose dose, boolean selectable, boolean selected) {
    LinearLayout card = Ui.card(this);
    card.setPadding(dp(14), dp(12), dp(14), dp(12));
    LinearLayout line = Ui.row(this);
    line.addView(Ui.medicine(this, dose.icon, dose.color, dose.secondaryColor, 46));
    LinearLayout details = Ui.column(this);
    LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, -2, 1);
    left.leftMargin = dp(12);
    line.addView(details, left);
    details.addView(Ui.text(this, dose.name, 20, Ui.INK, true));
    Ui.add(details, Ui.text(this, dose.detail + " · " + scheduledLabel(dose), 14, Ui.TEAL, false), 5);
    if (!dose.notes.isEmpty())
      Ui.add(details, Ui.text(this, dose.notes, 14, Ui.MUTED, false), 6);
    Medication med = Store.get(this).medication(dose.medId);
    if (med != null && med.stock >= 0 && med.stock <= 5)
      Ui.add(
          details,
          Ui.text(
              this,
              "Estoque: " + ScheduleRules.number(med.stock) + " " + med.unit,
              14,
              Ui.AMBER,
              true),
          6);
    card.addView(line);
    if (selectable) {
      card.setBackground(
          new RippleDrawable(
              ColorStateList.valueOf(0x22137C70),
              Ui.outline(selected ? Ui.LIGHT : Ui.SURFACE, selected ? Ui.TEAL : Ui.LINE, 18, this),
              null));
      FrameLayout marker = new FrameLayout(this);
      marker.setBackground(Ui.outline(Ui.SURFACE, selected ? Ui.TEAL : Ui.MUTED, 12, this));
      marker.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
      if (selected) {
        View dot = new View(this);
        dot.setBackground(Ui.bg(Ui.TEAL, 8, this));
        marker.addView(dot, new FrameLayout.LayoutParams(dp(10), dp(10), Gravity.CENTER));
      }
      LinearLayout.LayoutParams markerLayout = new LinearLayout.LayoutParams(dp(20), dp(20));
      markerLayout.leftMargin = dp(8);
      line.addView(marker, markerLayout);
      card.setSelected(selected);
      card.setFocusable(true);
      card.setOnClickListener(
          v -> {
            requested = dose.id;
            render();
          });
    }
    return card;
  }

  private String scheduledLabel(Dose dose) {
    ZonedDateTime planned = Instant.ofEpochMilli(dose.scheduled).atZone(ZoneId.systemDefault());
    String time = planned.format(ScheduleRules.CLOCK);
    return planned.toLocalDate().equals(LocalDate.now()) ? time : planned.format(DAY) + " às " + time;
  }

  private void restoreScroll(ScrollView scroll, int y) {
    scroll.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
      @Override
      public boolean onPreDraw() {
        scroll.getViewTreeObserver().removeOnPreDrawListener(this);
        scroll.scrollTo(0, y);
        return true;
      }
    });
  }

  private void showSnoozeTime(Dose dose) {
    if (snoozePicker != null) snoozePicker.dismiss();
    AlertDialog dialog = SnoozeTimePicker.show(this, dose, this::afterAction);
    snoozePicker = dialog;
    dialog.setOnDismissListener(ignored -> {
      if (snoozePicker == dialog) snoozePicker = null;
    });
  }

  private void act(long id, String action) {
    AlarmEngine.action(this, id, action);
    afterAction();
  }

  private void takeAll(Set<Long> displayedIds) {
    KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
    if (keyguard.isKeyguardLocked() && Prefs.privateAlarm(this)) {
      render();
      return;
    }
    int taken;
    try {
      taken = AlarmEngine.takeAll(this, displayedIds);
    } catch (SQLiteException e) {
      Ui.toast(this, "Não foi possível concluir o registro. Confira a lista e tente novamente.");
      render();
      return;
    }
    String message;
    if (taken == 0) message = "As doses exibidas já não estão aguardando confirmação.";
    else if (taken == 1) message = "1 dose registrada como tomada.";
    else message = taken + " doses registradas como tomadas.";
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    afterAction();
  }

  private void afterAction() {
    requested = 0;
    if (pending().isEmpty()) finish();
    else render();
  }

  private int dp(int value) {
    return Ui.dp(this, value);
  }
}
