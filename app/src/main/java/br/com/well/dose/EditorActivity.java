package br.com.well.dose;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class EditorActivity extends Activity {
  private Medication med;
  private EditText name, strength, amount, notes, stock;
  private Spinner unit;
  private CheckBox asNeeded, activate, reviewed;
  private String originalReview = "";
  private LinearLayout timesBox, daysBox;
  private AppearancePicker appearance;
  private double stockWhenOpened;
  private final SortedSet<LocalTime> times = new TreeSet<>();
  private int days = 127;
  private String start, end;
  private TextView startButton, endButton;
  private final String[] units = {
    "comprimido(s)",
    "cápsula(s)",
    "gota(s)",
    "mL",
    "dose(s)",
    "sachê(s)",
    "aplicação(ões)",
    "borrifo(s)",
    "unidade(s)"
  };

  @Override
  public void onCreate(Bundle saved) {
    Ui.prepare(this);
    super.onCreate(saved);
    if (android.os.Build.VERSION.SDK_INT >= 33)
      getOnBackInvokedDispatcher()
          .registerOnBackInvokedCallback(
              android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::confirmLeave);
    long id = getIntent().getLongExtra("med_id", 0);
    med = id == 0 ? new Medication() : Store.get(this).medication(id);
    if (med == null) {
      finish();
      return;
    }
    originalReview = med.review;
    stockWhenOpened = saved == null ? med.stock : saved.getDouble("stock_when_opened", med.stock);
    String restoredTimes = saved == null ? med.times : saved.getString("times", med.times);
    if (!restoredTimes.isEmpty()) times.addAll(ScheduleRules.parseTimes(restoredTimes));
    days = saved == null ? med.days : saved.getInt("days", med.days);
    start = saved == null ? med.start : saved.getString("start", med.start);
    end = saved == null ? med.end : saved.getString("end", med.end);
    LinearLayout root = Ui.root(this);
    LinearLayout top = Ui.row(this);
    top.setPadding(dp(14), dp(9), dp(22), dp(7));
    LinearLayout back = Ui.row(this);
    back.setGravity(Gravity.CENTER);
    back.setMinimumWidth(dp(48));
    back.setMinimumHeight(dp(48));
    back.addView(Ui.icon(this, "back", Ui.INK, 24));
    back.setOnClickListener(v -> confirmLeave());
    top.addView(back);
    TextView heading =
        Ui.text(this, id == 0 ? "Novo medicamento" : "Editar medicamento", 19, Ui.INK, true);
    LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(0, -2, 1);
    hp.leftMargin = dp(8);
    top.addView(heading, hp);
    root.addView(top);
    LinearLayout body = Ui.scroll(root);
    Ui.add(body, Ui.chip(this, "CUIDADO QUE VIRA ROTINA", Ui.LIGHT, Ui.TEAL), 8);
    Ui.title(body, "Vamos organizar?", "Use os dados e os horários da sua prescrição.");
    if (!med.review.isEmpty())
      Ui.add(
          body,
          Ui.text(this, "Dados importados para revisar\n" + med.review, 14, Ui.AMBER, true),
          18);
    section(body, "O medicamento");
    name =
        field(
            body,
            "Nome do medicamento",
            "Ex.: nome da sua receita",
            saved == null ? med.name : saved.getString("name"),
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS,
            101);
    strength =
        field(
            body,
            "Concentração (opcional)",
            "Ex.: 10 mg",
            saved == null ? med.strength : saved.getString("strength"),
            InputType.TYPE_CLASS_TEXT,
            102);
    LinearLayout two = Ui.row(this);
    LinearLayout a = Ui.column(this), b = Ui.column(this);
    two.addView(a, new LinearLayout.LayoutParams(0, -2, 1));
    LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, -2, 1);
    bp.leftMargin = dp(12);
    two.addView(b, bp);
    amount =
        field(
            a,
            "Quantidade por dose",
            "1",
            saved == null ? ScheduleRules.number(med.amount) : saved.getString("amount"),
            InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL,
            103);
    Ui.add(b, Ui.text(this, "Unidade", 13, Ui.INK, true), 17);
    unit = new Spinner(this);
    ArrayAdapter<String> adapter =
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, units);
    unit.setAdapter(adapter);
    unit.setMinimumHeight(dp(54));
    unit.setBackground(Ui.outline(Ui.SURFACE, Ui.LINE, 14, this));
    int selection = 0;
    for (int i = 0; i < units.length; i++) if (units[i].equals(med.unit)) selection = i;
    unit.setSelection(saved == null ? selection : saved.getInt("unit", selection));
    Ui.add(b, unit, 9);
    Ui.add(body, two, 0);
    section(body, "Formato e cores");
    Ui.add(
        body,
        Ui.text(
            this, "Escolha o desenho mais parecido com o seu medicamento.", 13, Ui.MUTED, false),
        8);
    appearance =
        new AppearancePicker(
            this,
            saved == null ? med.icon : saved.getString("icon", med.icon),
            saved == null ? med.color : saved.getInt("color", med.color),
            saved == null
                ? med.secondaryColor
                : saved.getInt("secondary_color", med.secondaryColor));
    Ui.add(body, appearance, 14);
    section(body, "Quando lembrar você");
    asNeeded = new CheckBox(this);
    asNeeded.setText("Usar somente quando necessário, sem horários fixos");
    asNeeded.setTextColor(Ui.INK);
    asNeeded.setChecked(
        saved == null ? med.asNeeded() : saved.getBoolean("as_needed", med.asNeeded()));
    Ui.add(body, asNeeded, 10);
    Ui.add(body, Ui.text(this, "Horários fixos", 15, Ui.INK, true), 2);
    Ui.add(
        body,
        Ui.text(
            this,
            "Adicione cada horário do dia. Os próximos lembretes começam após salvar.",
            13,
            Ui.MUTED,
            false),
        7);
    timesBox = Ui.column(this);
    Ui.add(body, timesBox, 13);
    renderTimes();
    Ui.add(body, Ui.button(this, "+  Adicionar horário", false, () -> timePicker(null)), 12);
    Ui.add(body, Ui.text(this, "Dias da semana", 14, Ui.INK, true), 22);
    daysBox = Ui.row(this);
    Ui.add(body, daysBox, 12);
    renderDays();
    Ui.add(body, Ui.text(this, "Período dos lembretes", 14, Ui.INK, true), 24);
    startButton = Ui.button(this, "", false, () -> date(true));
    Ui.add(body, startButton, 10);
    endButton = Ui.button(this, "", false, () -> date(false));
    Ui.add(body, endButton, 10);
    dates();
    TextView clearEnd = Ui.text(this, "Usar sem data de término", 12, Ui.MUTED, false);
    clearEnd.setMinHeight(dp(44));
    clearEnd.setGravity(Gravity.CENTER);
    clearEnd.setOnClickListener(
        v -> {
          end = "";
          dates();
        });
    Ui.add(body, clearEnd, 0);
    section(body, "Detalhes que ajudam");
    notes =
        field(
            body,
            "Orientações da receita (opcional)",
            "Ex.: tomar após o almoço",
            saved == null ? med.notes : saved.getString("notes"),
            InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
            104);
    notes.setMinLines(2);
    stock =
        field(
            body,
            "Estoque disponível (opcional)",
            "Quantidade na unidade escolhida",
            saved == null
                ? (med.stock < 0 ? "" : ScheduleRules.number(med.stock))
                : saved.getString("stock"),
            InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL,
            105);
    Ui.add(
        body,
        Ui.text(
            this,
            "O estoque diminui quando você confirma uma dose. O aviso de estoque baixo aparece"
                + " quando restarem 5 unidades ou menos.",
            12,
            Ui.MUTED,
            false),
        8);
    if (!med.review.isEmpty()) {
      reviewed = new CheckBox(this);
      reviewed.setText("Conferi os dados importados com minha receita");
      reviewed.setTextColor(Ui.INK);
      reviewed.setChecked(saved != null && saved.getBoolean("reviewed"));
      Ui.add(body, reviewed, 20);
    }
    activate = new CheckBox(this);
    activate.setText("Manter este cadastro ativo após salvar");
    activate.setTextColor(Ui.INK);
    activate.setChecked(saved == null ? med.enabled : saved.getBoolean("activate", med.enabled));
    Ui.add(body, activate, 14);
    Ui.add(body, Ui.gap(this, 15), 0);
    LinearLayout footer = Ui.column(this);
    footer.setPadding(dp(22), dp(12), dp(22), dp(14));
    footer.setBackgroundColor(Ui.BG);
    footer.addView(
        Ui.button(this, id == 0 ? "Criar lembretes" : "Salvar alterações", true, this::save));
    root.addView(footer);
  }

  private void renderTimes() {
    timesBox.removeAllViews();
    for (LocalTime t : times) {
      LinearLayout card = Ui.row(this);
      card.setPadding(dp(16), dp(3), dp(6), dp(3));
      card.setBackground(Ui.outline(Ui.SURFACE, Ui.LINE, 16, this));
      card.addView(Ui.icon(this, "clock", Ui.TEAL, 23));
      TextView time = Ui.text(this, t.format(ScheduleRules.CLOCK), 23, Ui.INK, true);
      time.setMinHeight(dp(54));
      time.setGravity(Gravity.CENTER_VERTICAL);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
      lp.leftMargin = dp(14);
      card.addView(time, lp);
      time.setOnClickListener(v -> timePicker(t));
      TextView delete = Ui.text(this, "×", 26, Ui.MUTED, false);
      delete.setContentDescription("Remover horário " + t);
      delete.setGravity(Gravity.CENTER);
      delete.setMinHeight(dp(48));
      delete.setMinWidth(dp(48));
      delete.setOnClickListener(
          v -> {
            times.remove(t);
            renderTimes();
          });
      card.addView(delete);
      Ui.add(timesBox, card, 8);
    }
  }

  private void timePicker(LocalTime existing) {
    if (existing == null && times.size() >= 12) {
      Ui.toast(this, "Você pode adicionar até 12 horários.");
      return;
    }
    LocalTime initial = existing == null ? LocalTime.of(8, 0) : existing;
    new TimePickerDialog(
            this,
            (v, h, m) -> {
              if (existing != null) times.remove(existing);
              times.add(LocalTime.of(h, m));
              renderTimes();
            },
            initial.getHour(),
            initial.getMinute(),
            true)
        .show();
  }

  private void renderDays() {
    daysBox.removeAllViews();
    String[] labels = {"S", "T", "Q", "Q", "S", "S", "D"},
        full = {"Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo"};
    for (int i = 0; i < 7; i++) {
      final int bit = 1 << i;
      boolean on = (days & bit) != 0;
      TextView day = Ui.text(this, labels[i], 15, on ? Ui.ON_PRIMARY : Ui.MUTED, true);
      day.setGravity(Gravity.CENTER);
      day.setMinHeight(dp(46));
      day.setContentDescription(full[i] + (on ? ", selecionado" : ", não selecionado"));
      Ui.touch(day, on ? Ui.TEAL : Ui.LINE, 14);
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
      p.setMargins(dp(2), 0, dp(2), 0);
      daysBox.addView(day, p);
      day.setOnClickListener(
          v -> {
            days ^= bit;
            renderDays();
          });
    }
  }

  private void date(boolean first) {
    String value = first ? start : (end.isEmpty() ? start : end);
    LocalDate current = value.isEmpty() ? LocalDate.now() : LocalDate.parse(value);
    new DatePickerDialog(
            this,
            (picker, y, m, d) -> {
              String chosen = LocalDate.of(y, m + 1, d).toString();
              if (first) start = chosen;
              else end = chosen;
              dates();
            },
            current.getYear(),
            current.getMonthValue() - 1,
            current.getDayOfMonth())
        .show();
  }

  private void dates() {
    DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    startButton.setText(
        start.isEmpty()
            ? "Primeiro dia não informado"
            : "Início dos lembretes: " + LocalDate.parse(start).format(f));
    endButton.setText(
        end.isEmpty() ? "Sem data de término" : "Término: " + LocalDate.parse(end).format(f));
  }

  private void section(LinearLayout body, String title) {
    Ui.add(body, Ui.text(this, title, 20, Ui.INK, true), 27);
  }

  private EditText field(
      LinearLayout body, String label, String hint, String value, int type, int id) {
    Ui.add(body, Ui.text(this, label, 13, Ui.INK, true), 17);
    EditText e = new EditText(this);
    e.setId(id);
    e.setTextSize(16);
    e.setTextColor(Ui.INK);
    e.setHintTextColor(Ui.MUTED);
    e.setInputType(type);
    e.setText(value);
    e.setHint(hint);
    e.setPadding(dp(15), dp(13), dp(15), dp(13));
    e.setMinHeight(dp(54));
    e.setBackground(Ui.outline(Ui.SURFACE, Ui.LINE, 14, this));
    e.setSelectAllOnFocus(false);
    e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
    Ui.add(body, e, 9);
    return e;
  }

  private void save() {
    try {
      med.name = name.getText().toString().trim();
      med.strength = strength.getText().toString().trim();
      med.amount = Double.parseDouble(amount.getText().toString().replace(",", "."));
      med.unit = units[unit.getSelectedItemPosition()];
      med.notes = notes.getText().toString().trim();
      String s = stock.getText().toString().trim();
      med.stock = s.isEmpty() ? -1 : Double.parseDouble(s.replace(",", "."));
      med.days = days;
      med.color = appearance.primaryColor();
      med.icon = appearance.icon();
      med.secondaryColor = appearance.secondaryColor();
      med.start = start;
      med.end = end;
      med.kind = asNeeded.isChecked() ? "as_needed" : "scheduled";
      med.enabled = activate.isChecked();
      med.review = reviewed != null && !reviewed.isChecked() ? originalReview : "";
      if (med.enabled && med.start.isEmpty() && !med.asNeeded())
        med.start = LocalDate.now().toString();
      List<String> values = new ArrayList<>();
      for (LocalTime t : times) values.add(t.format(ScheduleRules.CLOCK));
      med.times = String.join(",", values);
      if (med.asNeeded()) {
        med.times = "";
        med.days = 0;
      }
      ScheduleRules.validate(med);
      synchronized (AlarmEngine.LOCK) {
        Store.get(this).save(med, System.currentTimeMillis(), stockWhenOpened);
        AlarmEngine.refresh(this);
      }
      if (!Alerts.allowed(this) || !AlarmEngine.exact(this)) {
        startActivity(
            new Intent(this, MainActivity.class)
                .putExtra("tab", 3)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        Ui.toast(this, "Medicamento salvo. Ative os alertas para receber os lembretes.");
      } else Ui.toast(this, "Lembretes salvos.");
      finish();
    } catch (NumberFormatException e) {
      Ui.toast(this, "Informe uma quantidade por dose e um estoque válidos.");
    } catch (Exception e) {
      Ui.toast(this, e.getMessage() == null ? "Revise os dados informados." : e.getMessage());
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle b) {
    super.onSaveInstanceState(b);
    b.putString("name", name.getText().toString());
    b.putString("strength", strength.getText().toString());
    b.putString("amount", amount.getText().toString());
    b.putString("notes", notes.getText().toString());
    b.putString("stock", stock.getText().toString());
    b.putDouble("stock_when_opened", stockWhenOpened);
    b.putBoolean("as_needed", asNeeded.isChecked());
    b.putBoolean("activate", activate.isChecked());
    if (reviewed != null) b.putBoolean("reviewed", reviewed.isChecked());
    b.putInt("unit", unit.getSelectedItemPosition());
    b.putInt("days", days);
    b.putInt("color", appearance.primaryColor());
    b.putString("icon", appearance.icon());
    b.putInt("secondary_color", appearance.secondaryColor());
    b.putString("start", start);
    b.putString("end", end);
    List<String> t = new ArrayList<>();
    for (LocalTime a : times) t.add(a.format(ScheduleRules.CLOCK));
    b.putString("times", String.join(",", t));
  }

  private void confirmLeave() {
    new AlertDialog.Builder(this)
        .setTitle("Sair do cadastro?")
        .setMessage("As alterações ainda não salvas serão descartadas.")
        .setNegativeButton("Continuar editando", null)
        .setPositiveButton("Sair", (a, b) -> finish())
        .show();
  }

  @Override
  public void onBackPressed() {
    confirmLeave();
  }

  private int dp(int value) {
    return Ui.dp(this, value);
  }
}
