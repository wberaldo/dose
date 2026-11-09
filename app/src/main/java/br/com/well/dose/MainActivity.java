package br.com.well.dose;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.json.*;

public final class MainActivity extends Activity {
  private int tab = 0;
  private String filter = "all";
  private LocalDate selected = LocalDate.now();
  private final Locale pt = Locale.forLanguageTag("pt-BR");
  private boolean registered = false;
  private final BroadcastReceiver changed =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
          render();
        }
      };
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable clock =
      new Runnable() {
        public void run() {
          render();
          handler.postDelayed(this, 30000);
        }
      };

  @Override
  public void onCreate(Bundle state) {
    Ui.prepare(this);
    super.onCreate(state);
    if (state != null) {
      tab = state.getInt("tab", tab);
      selected = LocalDate.parse(state.getString("date", LocalDate.now().toString()));
    }
    applyTab(getIntent());
    Alerts.channels(this);
  }

  @Override
  protected void onSaveInstanceState(Bundle b) {
    super.onSaveInstanceState(b);
    b.putInt("tab", tab);
    b.putString("date", selected.toString());
  }

  @Override
  protected void onNewIntent(Intent i) {
    super.onNewIntent(i);
    setIntent(i);
    applyTab(i);
    render();
  }

  private void applyTab(Intent intent) {
    if (intent.hasExtra("tab")) {
      tab = intent.getIntExtra("tab", tab);
      intent.removeExtra("tab");
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    AlarmEngine.refresh(this);
    if (!registered) {
      if (Build.VERSION.SDK_INT >= 33)
        registerReceiver(
            changed, new IntentFilter("br.com.well.dose.CHANGED"), Context.RECEIVER_NOT_EXPORTED);
      else registerReceiver(changed, new IntentFilter("br.com.well.dose.CHANGED"));
      registered = true;
    }
    render();
    handler.postDelayed(clock, 30000);
  }

  @Override
  protected void onPause() {
    handler.removeCallbacksAndMessages(null);
    if (registered) {
      unregisterReceiver(changed);
      registered = false;
    }
    super.onPause();
  }

  private void render() {
    LinearLayout root = Ui.root(this);
    LinearLayout top = Ui.row(this);
    top.setPadding(dp(22), dp(14), dp(22), dp(8));
    LinearLayout logo = Ui.row(this);
    logo.addView(Ui.icon(this, "pill", Ui.TEAL, 25));
    TextView brand = Ui.text(this, "dose", 27, Ui.INK, true);
    LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-2, -2);
    bl.leftMargin = dp(8);
    logo.addView(brand, bl);
    top.addView(logo, new LinearLayout.LayoutParams(0, -2, 1));
    TextView badge = Ui.chip(this, "SEU CUIDADO DIÁRIO", Ui.LIGHT, Ui.TEAL);
    top.addView(badge);
    root.addView(top);
    LinearLayout body = Ui.scroll(root);
    switch (tab) {
      case 0:
        home(body);
        break;
      case 1:
        medicines(body);
        break;
      case 2:
        history(body);
        break;
      default:
        settings(body);
    }
    LinearLayout nav = Ui.row(this);
    nav.setBackgroundColor(Ui.SURFACE);
    nav.setPadding(dp(8), dp(7), dp(8), dp(7));
    String[] icons = {"calendar", "pill", "history", "bell"},
        labels = {"Hoje", "Remédios", "Histórico", "Alertas"};
    for (int j = 0; j < 4; j++) {
      final int n = j;
      LinearLayout item = Ui.column(this);
      item.setGravity(Gravity.CENTER);
      item.setMinimumHeight(dp(60));
      Ui.touch(item, tab == j ? Ui.LIGHT : Ui.SURFACE, 18);
      item.addView(Ui.icon(this, icons[j], tab == j ? Ui.TEAL : Ui.MUTED, 23));
      TextView navLabel = Ui.text(this, labels[j], 11, tab == j ? Ui.TEAL : Ui.MUTED, tab == j);
      navLabel.setGravity(Gravity.CENTER);
      Ui.add(item, navLabel, 5);
      nav.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
      item.setContentDescription(labels[j]);
      item.setOnClickListener(
          v -> {
            tab = n;
            render();
          });
    }
    root.addView(nav);
  }

  private void home(LinearLayout body) {
    Ui.add(body, Ui.text(this, "Seu cuidado, em dia.", 29, Ui.INK, true), 10);
    Ui.add(
        body,
        Ui.text(
            this,
            capitalize(
                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", pt))),
            14,
            Ui.MUTED,
            false),
        8);
    Store db = Store.get(this);
    List<Medication> meds = db.medications();
    List<Dose> today = db.forDate(LocalDate.now());
    int taken = 0;
    for (Dose d : today) if ("taken".equals(d.status)) taken++;
    List<Dose> upcoming =
        db.doses(
            "status='pending' AND reminder_cancelled=0 AND expires>?",
            new String[] {"" + System.currentTimeMillis()},
            "scheduled,id LIMIT 1");
    LinearLayout hero = Ui.column(this);
    hero.setPadding(dp(22), dp(22), dp(22), dp(22));
    hero.setBackground(Ui.bg(Ui.HERO, 26, this));
    LinearLayout heroTop = Ui.row(this);
    TextView tag =
        Ui.text(
            this,
            upcoming.isEmpty() ? "UM DIA DE CADA VEZ" : "PRÓXIMO LEMBRETE",
            11,
            0xFFD4EEE5,
            true);
    tag.setLetterSpacing(.1f);
    heroTop.addView(tag, new LinearLayout.LayoutParams(0, -2, 1));
    heroTop.addView(Ui.icon(this, "pill", 0xFFD9EBAA, 27));
    hero.addView(heroTop);
    if (upcoming.isEmpty()) {
      Ui.add(
          hero,
          Ui.text(
              this,
              meds.isEmpty() ? "Mais leve.\nMais no seu tempo." : "Tudo certo por aqui.",
              27,
              Ui.WHITE,
              true),
          14);
      Ui.add(
          hero,
          Ui.text(
              this,
              meds.isEmpty()
                  ? "Organize seus remédios. A gente lembra você."
                  : "Os próximos cuidados aparecem aqui.",
              14,
              0xFFD4EEE5,
              false),
          10);
    } else {
      Dose next = upcoming.get(0);
      Ui.add(hero, Ui.text(this, next.name, 26, Ui.WHITE, true), 14);
      Ui.add(hero, Ui.text(this, next.detail, 14, 0xFFD4EEE5, false), 8);
      String at =
          Instant.ofEpochMilli(next.scheduled)
              .atZone(ZoneId.systemDefault())
              .format(DateTimeFormatter.ofPattern("HH:mm"));
      String day =
          Instant.ofEpochMilli(next.scheduled)
                  .atZone(ZoneId.systemDefault())
                  .toLocalDate()
                  .equals(LocalDate.now())
              ? "Hoje"
              : "Próximo dia";
      Ui.add(hero, Ui.chip(this, day + " · " + at, 0xFF2B8D7F, Ui.WHITE), 16);
    }
    Ui.add(body, hero, 23);
    if (!today.isEmpty()) {
      LinearLayout progress = Ui.row(this);
      progress.addView(
          Ui.text(this, taken + " de " + today.size() + " doses tomadas", 13, Ui.MUTED, false),
          new LinearLayout.LayoutParams(0, -2, 1));
      progress.addView(
          Ui.text(this, Math.round(taken * 100f / today.size()) + "%", 13, Ui.TEAL, true));
      Ui.add(body, progress, 16);
      ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
      bar.setMax(today.size());
      bar.setProgress(taken);
      bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.TEAL));
      bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.LINE));
      Ui.add(body, bar, 6);
    }
    if (!Alerts.allowed(this) || !AlarmEngine.exact(this)) {
      LinearLayout warn = Ui.row(this);
      warn.setPadding(dp(14), dp(14), dp(14), dp(14));
      Ui.touch(warn, Ui.WARNING, 18);
      warn.addView(Ui.icon(this, "bell", Ui.AMBER, 23));
      TextView t =
          Ui.text(
              this,
              "Falta ativar seus lembretes\nToque para conferir as permissões",
              13,
              Ui.AMBER,
              true);
      LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1);
      tp.leftMargin = dp(12);
      warn.addView(t, tp);
      warn.setOnClickListener(
          v -> {
            tab = 3;
            render();
          });
      Ui.add(body, warn, 18);
    }
    LinearLayout section = Ui.row(this);
    section.addView(
        Ui.text(this, "Sua rotina", 21, Ui.INK, true), new LinearLayout.LayoutParams(0, -2, 1));
    TextView plus = Ui.button(this, "+ Adicionar", false, () -> editor(0));
    section.addView(plus);
    Ui.add(body, section, 26);
    LinearLayout week = Ui.row(this);
    LocalDate first = LocalDate.now().minusDays(2);
    for (int i = 0; i < 7; i++) {
      LocalDate date = first.plusDays(i);
      boolean chosen = date.equals(selected);
      LinearLayout day = Ui.column(this);
      day.setPadding(0, dp(11), 0, dp(11));
      day.setGravity(Gravity.CENTER);
      day.setMinimumHeight(dp(62));
      day.setSelected(chosen);
      Ui.touch(day, chosen ? Ui.SELECTED : Ui.BG, 16);
      String letter = date.format(DateTimeFormatter.ofPattern("EEE", pt)).replace(".", "");
      TextView weekday = Ui.text(this, letter, 11, chosen ? Ui.ON_SELECTED : Ui.MUTED, false);
      weekday.setGravity(Gravity.CENTER);
      Ui.add(day, weekday, 0);
      TextView number =
          Ui.text(this, "" + date.getDayOfMonth(), 19, chosen ? Ui.ON_SELECTED : Ui.INK, true);
      number.setGravity(Gravity.CENTER);
      Ui.add(day, number, 9);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
      lp.setMargins(dp(2), 0, dp(2), 0);
      week.addView(day, lp);
      day.setOnClickListener(
          v -> {
            selected = date;
            render();
          });
      day.setContentDescription(
          date.format(DateTimeFormatter.ofPattern("d 'de' MMMM", pt))
              + (chosen ? ", selecionado" : ""));
    }
    Ui.add(body, week, 17);
    List<Dose> rows = db.forDate(selected);
    if (rows.isEmpty())
      empty(
          body,
          meds.isEmpty() ? "Seu primeiro lembrete começa aqui" : "Nenhuma dose neste dia",
          meds.isEmpty()
              ? "Cadastre um medicamento com os horários da sua receita."
              : "Os horários cadastrados aparecem na sua agenda.",
          meds.isEmpty() ? "Cadastrar medicamento" : null,
          () -> editor(0));
    else for (Dose d : rows) doseCard(body, d);
    Ui.add(body, Ui.text(this, "No seu ritmo, um cuidado de cada vez.", 12, Ui.MUTED, false), 22);
  }

  private void doseCard(LinearLayout body, Dose d) {
    LinearLayout card = Ui.card(this);
    LinearLayout line = Ui.row(this);
    TextView time =
        Ui.text(
            this,
            Instant.ofEpochMilli(d.scheduled)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
                .format(ScheduleRules.CLOCK),
            23,
            Ui.INK,
            true);
    line.addView(time, new LinearLayout.LayoutParams(0, -2, 1));
    String status;
    int bg = Ui.LIGHT, color = Ui.TEAL;
    switch (d.status) {
      case "taken":
        status = "Tomado";
        break;
      case "skipped":
        status = "Pulado";
        bg = Ui.LINE;
        color = Ui.MUTED;
        break;
      case "missed":
        status = "Sem registro";
        bg = Ui.WARNING;
        color = Ui.AMBER;
        break;
      default:
        if (d.reminderCancelled) {
          status = "Sem avisos no dia";
          bg = Ui.LINE;
          color = Ui.MUTED;
        } else if (d.snoozed) {
          status = "Adiado · " + time(d.nextAlert);
        } else if (d.scheduled < System.currentTimeMillis()) {
          status = "Aguardando você";
          bg = Ui.WARNING;
          color = Ui.AMBER;
        } else status = "Programado";
    }
    line.addView(Ui.chip(this, status, bg, color));
    card.addView(line);
    LinearLayout label = Ui.row(this);
    View icon = Ui.medicine(this, d.icon, d.color, d.secondaryColor, 40);
    label.addView(icon);
    LinearLayout info = Ui.column(this);
    LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, -2, 1);
    ip.leftMargin = dp(12);
    label.addView(info, ip);
    Ui.add(info, Ui.text(this, d.name, 18, Ui.INK, true), 0);
    Ui.add(info, Ui.text(this, d.detail, 13, Ui.MUTED, false), 6);
    Ui.add(card, label, 18);
    if (!d.notes.isEmpty()) Ui.add(card, Ui.text(this, d.notes, 13, Ui.MUTED, false), 12);
    LocalDate doseDate =
        Instant.ofEpochMilli(d.scheduled).atZone(ZoneId.systemDefault()).toLocalDate();
    boolean canAdjustReminder =
        !d.reminderCancelled
            && !doseDate.isAfter(LocalDate.now())
            && d.scheduled <= System.currentTimeMillis() + 300000;
    if (d.actionable() && !doseDate.isAfter(LocalDate.now())) {
      LinearLayout actions = Ui.row(this);
      TextView take = Ui.button(this, "✓  Tomei", true, () -> take(d));
      take.setTag("take:" + d.id);
      take.setContentDescription("Tomei " + d.name + ", dose prevista para " + time(d.scheduled));
      actions.addView(take, new LinearLayout.LayoutParams(0, -2, 1));
      if (canAdjustReminder) {
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, -2, 1);
        sp.leftMargin = dp(9);
        actions.addView(Ui.button(this, "Adiar", false, () -> snooze(d)), sp);
      }
      Ui.add(card, actions, 17);
    }
    if (d.actionable() && canAdjustReminder) {
      TextView skip = Ui.text(this, "Pular esta dose", 12, Ui.MUTED, false);
      skip.setPadding(0, dp(16), 0, dp(5));
      skip.setMinHeight(dp(44));
      skip.setGravity(Gravity.CENTER);
      skip.setOnClickListener(
          v ->
              new AlertDialog.Builder(this)
                  .setTitle("Pular esta dose?")
                  .setMessage(
                      "Ela será registrada como pulada e os avisos desta dose serão encerrados.")
                  .setNegativeButton("Voltar", null)
                  .setPositiveButton(
                      "Pular",
                      (a, b) -> {
                        AlarmEngine.action(this, d.id, "SKIP");
                        render();
                      })
                  .show());
      card.addView(skip);
    }
    if ("taken".equals(d.status))
      Ui.add(card, Ui.text(this, "Registrado em " + time(d.actual), 12, Ui.TEAL, false), 12);
    if ("missed".equals(d.status))
      Ui.add(card, Ui.button(this, "Já tomei • registrar horário", false, () -> recordTime(d)), 14);
    if (d.actionable() && !d.reminderCancelled) {
      TextView cancel =
          Ui.button(this, "Cancelar lembretes do dia", false, () -> cancelReminders(d));
      cancel.setTag("cancel:" + d.id);
      cancel.setContentDescription("Cancelar lembretes de " + d.name + " em " + doseDate);
      Ui.add(card, cancel, 10);
    }
    Ui.add(body, card, 14);
  }

  private void medicines(LinearLayout body) {
    Ui.add(
        body,
        Ui.button(
            this,
            "Importar medicamentos",
            false,
            () -> startActivity(new Intent(this, ImportActivity.class))),
        10);
    Ui.title(body, "Seus medicamentos", "Todos os detalhes do seu cuidado, em um só lugar.");
    Ui.add(body, Ui.button(this, "+  Novo medicamento", true, () -> editor(0)), 22);
    List<Medication> list = Store.get(this).medications();
    if (list.isEmpty()) {
      empty(
          body,
          "Uma rotina só sua",
          "Adicione o nome, a dose e os horários da sua receita.",
          null,
          () -> {});
      return;
    }
    for (Medication m : list) {
      LinearLayout card = Ui.card(this);
      LinearLayout head = Ui.row(this);
      head.addView(Ui.medicine(this, m.icon, m.color, m.secondaryColor, 46));
      LinearLayout labels = Ui.column(this);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
      lp.leftMargin = dp(13);
      head.addView(labels, lp);
      labels.addView(Ui.text(this, m.name, 19, Ui.INK, true));
      Ui.add(labels, Ui.text(this, m.detail(), 13, Ui.MUTED, false), 6);
      TextView menu = Ui.text(this, "⋮", 26, Ui.MUTED, true);
      menu.setGravity(Gravity.CENTER);
      menu.setMinWidth(dp(44));
      menu.setMinHeight(dp(48));
      head.addView(menu);
      menu.setOnClickListener(v -> medMenu(m));
      card.addView(head);
      Ui.add(
          card,
          Ui.text(
              this,
              m.asNeeded() ? "Quando necessário • sem alarmes" : m.times.replace(",", "  ·  "),
              18,
              Ui.TEAL,
              true),
          18);
      Ui.add(
          card,
          Ui.text(
              this,
              (m.asNeeded() ? "Sob demanda" : days(m.days))
                  + (m.end.isEmpty()
                      ? " • Uso contínuo"
                      : " • Até "
                          + LocalDate.parse(m.end)
                              .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))),
              12,
              Ui.MUTED,
              false),
          8);
      if (!m.notes.isEmpty()) Ui.add(card, Ui.text(this, m.notes, 13, Ui.MUTED, false), 12);
      LinearLayout foot = Ui.row(this);
      foot.addView(
          Ui.chip(
              this,
              !m.review.isEmpty()
                  ? "Revisar"
                  : m.asNeeded()
                      ? "Sob demanda"
                      : !m.end.isEmpty() && LocalDate.parse(m.end).isBefore(LocalDate.now())
                          ? "Encerrado"
                          : m.enabled ? "Ativo" : "Pausado",
              m.enabled ? Ui.LIGHT : Ui.LINE,
              m.enabled ? Ui.TEAL : Ui.MUTED));
      if (m.stock >= 0) {
        TextView stock =
            Ui.text(
                this,
                "Estoque: " + ScheduleRules.number(m.stock) + (m.stock <= 5 ? " • baixo" : ""),
                12,
                m.stock <= 5 ? Ui.AMBER : Ui.MUTED,
                true);
        stock.setGravity(Gravity.END);
        foot.addView(stock, new LinearLayout.LayoutParams(0, -2, 1));
      }
      Ui.add(card, foot, 16);
      Ui.add(body, card, 16);
    }
  }

  private void medMenu(Medication m) {
    String[] options = {
      "Editar medicamento",
      !m.review.isEmpty()
          ? "Revisar dados importados"
          : m.asNeeded()
              ? (m.enabled ? "Desativar cadastro" : "Ativar cadastro")
              : m.enabled ? "Pausar lembretes" : "Retomar lembretes",
      "Atualizar estoque",
      "Arquivar medicamento"
    };
    new AlertDialog.Builder(this)
        .setTitle(m.name)
        .setItems(
            options,
            (dialog, index) -> {
              if (index == 0) editor(m.id);
              if (index == 1) {
                if (!m.review.isEmpty()) {
                  editor(m.id);
                  return;
                }
                Store.get(this).setEnabled(m.id, !m.enabled, false);
                AlarmEngine.refresh(this);
                render();
              }
              if (index == 2) {
                EditText input = new EditText(this);
                input.setInputType(8194);
                input.setHint("Quantidade disponível");
                if (m.stock >= 0) input.setText(ScheduleRules.number(m.stock));
                new AlertDialog.Builder(this)
                    .setTitle("Atualizar estoque")
                    .setView(input)
                    .setNegativeButton("Voltar", null)
                    .setPositiveButton(
                        "Salvar",
                        (a, b) -> {
                          try {
                            Store.get(this)
                                .replenish(
                                    m.id,
                                    Double.parseDouble(
                                        input.getText().toString().replace(",", ".")));
                            render();
                          } catch (Exception e) {
                            Ui.toast(this, "Informe uma quantidade válida.");
                          }
                        })
                    .show();
              }
              if (index == 3)
                new AlertDialog.Builder(this)
                    .setTitle("Arquivar " + m.name + "?")
                    .setMessage(
                        "Os próximos lembretes serão encerrados. As doses registradas continuam no"
                            + " histórico.")
                    .setNegativeButton("Voltar", null)
                    .setPositiveButton(
                        "Arquivar",
                        (a, b) -> {
                          Store.get(this).setEnabled(m.id, false, true);
                          AlarmEngine.refresh(this);
                          render();
                        })
                    .show();
            })
        .show();
  }

  private void history(LinearLayout body) {
    Ui.title(body, "Seu histórico", "Pequenos registros, uma rotina mais organizada.");
    for (String[] source : Store.get(this).importedSources()) {
      LinearLayout report = Ui.card(this);
      report.addView(Ui.text(this, "Relatório importado", 16, Ui.INK, true));
      Ui.add(
          report,
          Ui.text(this, source[1] + " • resumo por dia e PDF original", 13, Ui.MUTED, false),
          8);
      report.setOnClickListener(
          v ->
              startActivity(
                  new Intent(this, ReportActivity.class).putExtra("source_id", source[0])));
      Ui.add(body, report, 14);
    }
    LinearLayout filters = Ui.row(this);
    String[] names = {"Todos", "Tomados", "Sem registro"}, keys = {"all", "taken", "missed"};
    for (int i = 0; i < 3; i++) {
      String f = keys[i];
      TextView b =
          Ui.chip(
              this,
              names[i],
              filter.equals(f) ? Ui.SELECTED : Ui.LINE,
              filter.equals(f) ? Ui.ON_SELECTED : Ui.MUTED);
      b.setMinHeight(dp(44));
      b.setGravity(Gravity.CENTER);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
      lp.rightMargin = dp(5);
      filters.addView(b, lp);
      b.setOnClickListener(
          v -> {
            filter = f;
            render();
          });
    }
    Ui.add(body, filters, 22);
    List<Dose> events = Store.get(this).history();
    String last = "";
    int shown = 0;
    for (Dose d : events) {
      if (!filter.equals("all") && !filter.equals(d.status)) continue;
      String date =
          Instant.ofEpochMilli(d.scheduled)
              .atZone(ZoneId.systemDefault())
              .toLocalDate()
              .format(DateTimeFormatter.ofPattern("d 'de' MMMM, yyyy", pt));
      if (!date.equals(last)) {
        Ui.add(body, Ui.text(this, date.toUpperCase(pt), 11, Ui.MUTED, true), 24);
        last = date;
      }
      LinearLayout card = Ui.card(this);
      LinearLayout row = Ui.row(this);
      row.addView(Ui.medicine(this, d.icon, d.color, d.secondaryColor, 36));
      LinearLayout info = Ui.column(this);
      info.addView(Ui.text(this, d.name, 17, Ui.INK, true));
      Ui.add(
          info,
          Ui.text(this, d.detail + " • previsto " + time(d.scheduled), 12, Ui.MUTED, false),
          7);
      LinearLayout.LayoutParams historyInfo = new LinearLayout.LayoutParams(0, -2, 1);
      historyInfo.leftMargin = dp(12);
      row.addView(info, historyInfo);
      row.addView(
          Ui.icon(
              this,
              "taken".equals(d.status) ? "check" : "clock",
              "taken".equals(d.status) ? Ui.TEAL : Ui.AMBER,
              23));
      card.addView(row);
      Ui.add(
          card,
          Ui.text(
              this,
              "taken".equals(d.status)
                  ? "Tomado às " + time(d.actual)
                  : "skipped".equals(d.status) ? "Dose pulada" : "Sem registro de tomada",
              13,
              "taken".equals(d.status) ? Ui.TEAL : Ui.AMBER,
              true),
          13);
      if (!"skipped".equals(d.status)) {
        TextView edit =
            Ui.text(
                this,
                "taken".equals(d.status)
                    ? "Corrigir horário registrado"
                    : "Já tomei • registrar horário",
                12,
                Ui.MUTED,
                false);
        edit.setMinHeight(dp(44));
        edit.setGravity(Gravity.CENTER_VERTICAL);
        edit.setOnClickListener(v -> recordTime(d));
        Ui.add(card, edit, 3);
      }
      Ui.add(body, card, 12);
      shown++;
    }
    if (shown == 0)
      empty(
          body,
          "Seu histórico começa com você",
          "As doses tomadas, puladas e sem registro aparecem aqui.",
          null,
          () -> {});
    Ui.add(
        body,
        Ui.text(
            this,
            "Exibindo até 250 registros recentes. O backup inclui todo o histórico.",
            12,
            Ui.MUTED,
            false),
        22);
  }

  private void settings(LinearLayout body) {
    Ui.title(body, "Central de alertas", "Um minuto agora. Mais tranquilidade depois.");
    String theme = Prefs.get(this).getString("theme", "system");
    option(
        body,
        "Aparência",
        "dark".equals(theme)
            ? "Modo escuro"
            : "light".equals(theme) ? "Modo claro" : "Seguir o celular",
        this::appearance);
    boolean n = Alerts.allowed(this),
        e = AlarmEngine.exact(this),
        f = Alerts.fullScreenAllowed(this);
    boolean over = Alerts.overOtherApps(this), dnd = AlarmAudio.dndAllowsAlarms(this);
    boolean ready = n && e && f && over && dnd;
    boolean battery =
        ((PowerManager) getSystemService(POWER_SERVICE))
            .isIgnoringBatteryOptimizations(getPackageName());
    LinearLayout state = Ui.card(this);
    state.setBackground(Ui.bg(ready ? Ui.LIGHT : Ui.WARNING, 22, this));
    LinearLayout line = Ui.row(this);
    line.addView(Ui.icon(this, "shield", ready ? Ui.TEAL : Ui.AMBER, 32));
    TextView title =
        Ui.text(
            this,
            ready ? "Alarmes e tela cheia prontos" : "Seus alertas precisam de atenção",
            18,
            ready ? Ui.TEAL : Ui.AMBER,
            true);
    LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1);
    tp.leftMargin = dp(14);
    line.addView(title, tp);
    state.addView(line);
    Ui.add(
        state,
        Ui.text(
            this,
            "Confira as opções abaixo e faça um teste com a tela bloqueada.",
            13,
            Ui.MUTED,
            false),
        12);
    Ui.add(body, state, 22);
    permission(
        body,
        "Notificações",
        "Avisos e botões para confirmar ou adiar.",
        n,
        this::notificationPermission);
    permission(
        body,
        "Alarmes e lembretes",
        "Permite tocar no horário escolhido, inclusive em repouso.",
        e,
        () ->
            open(
                new Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName()))));
    permission(
        body,
        "Tela cheia na tela bloqueada",
        "Autorize a abertura do alarme com o celular bloqueado.",
        f,
        () -> {
          if (Build.VERSION.SDK_INT >= 34)
            open(
                new Intent(
                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:" + getPackageName())));
          else Ui.toast(this, "Seu Android já permite essa opção.");
        });
    permission(
        body,
        "Tela cheia com o celular em uso",
        "Permita aparecer sobre outros apps para abrir o alarme também durante o uso do celular.",
        over,
        () ->
            open(
                new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()))));
    permission(
        body,
        "Som no Não perturbe",
        "O modo ativo do celular precisa permitir alarmes.",
        dnd,
        this::dndOptions);
    permission(
        body,
        "Bateria sem restrições",
        "Ajuda a evitar que o sistema suspenda os lembretes.",
        battery,
        () ->
            open(
                new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()))));
    Ui.add(body, Ui.button(this, "Testar alarme em 15 segundos", true, this::testAlarm), 21);
    Ui.add(
        body,
        Ui.text(
            this,
            "O teste abre a tela de alarme quando as permissões acima estão ativas. Há apenas um"
                + " aviso, com Encerrar teste e Adiar. Nenhuma dose ou estoque será alterado.",
            12,
            Ui.MUTED,
            false),
        10);
    long test = Prefs.get(this).getLong("test_at", 0);
    if (test > 0)
      Ui.add(
          body,
          Ui.text(
              this,
              "Teste programado para " + time(test) + ". Nenhuma dose será registrada.",
              12,
              Ui.TEAL,
              true),
          10);
    Ui.add(body, Ui.text(this, "Do seu jeito", 21, Ui.INK, true), 28);
    option(
        body,
        "Som e vibração do aviso",
        "Escolha o som, a importância e a exibição do canal no Android.",
        () ->
            open(
                new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())
                    .putExtra(Settings.EXTRA_CHANNEL_ID, Alerts.CHANNEL)));
    option(body, "Volume mínimo do alarme", volumeLabel(), this::volume);
    toggle(
        body, "Som do alarme", "Tocar por até 1 minuto, usando o áudio de alarmes.", "ring", true);
    toggle(
        body,
        "Tocar no vibrar ou silencioso",
        "Usa o volume mínimo do Dose durante o alarme e restaura o volume anterior ao finalizar.",
        "force_sound",
        true);
    toggle(
        body,
        "Vibração contínua",
        "Vibrar durante o alarme, se o canal permitir.",
        "vibrate",
        true);
    toggle(body, "Tela de alarme", "Usar tela cheia quando houver permissão.", "fullscreen", true);
    toggle(
        body,
        "Privacidade na tela bloqueada",
        "Ocultar os detalhes até desbloquear o aparelho.",
        "private",
        false);
    option(
        body,
        "Repetir se eu não responder",
        "A cada " + Prefs.interval(this) + " minutos",
        () ->
            choose(
                "Intervalo de repetição",
                new int[] {2, 5, 10, 15, 30},
                "repeat_minutes",
                " minutos"));
    option(
        body,
        "Número de repetições",
        Prefs.repeats(this) == 0
            ? "Sem repetição automática"
            : Prefs.repeats(this) + " repetições após o aviso inicial",
        () ->
            choose(
                "Repetições após o aviso inicial",
                new int[] {0, 1, 2, 3, 5},
                "repeat_count",
                " repetições"));
    option(
        body,
        "Adiamento rápido",
        Prefs.snooze(this) + " minutos na notificação",
        () -> choose("Adiamento rápido", new int[] {5, 10, 15, 30}, "snooze_minutes", " minutos"));
    Ui.add(body, Ui.text(this, "Seu aparelho", 21, Ui.INK, true), 28);
    LinearLayout note = Ui.card(this);
    Ui.add(note, Ui.text(this, "Samsung / One UI", 16, Ui.INK, true), 0);
    Ui.add(
        note,
        Ui.text(
            this,
            "Em Configurações → Bateria → Limites de uso em segundo plano, adicione Dose aos"
                + " aplicativos que nunca entram em suspensão. Em Informações do app → Bateria,"
                + " selecione Sem restrições, se disponível.",
            13,
            Ui.MUTED,
            false),
        10);
    Ui.add(
        note,
        Ui.text(
            this,
            "O som do Dose usa o áudio de alarmes, separado do toque de chamadas. No Não perturbe,"
                + " permita alarmes no modo ativo do sistema. O app não desativa esse modo.",
            13,
            Ui.MUTED,
            false),
        13);
    Ui.add(
        note,
        Ui.text(
            this,
            "Os horários seguem o fuso atual. Após reiniciar, desbloqueie o celular uma vez. Se"
                + " usar Forçar parada, abra o Dose novamente para retomar os lembretes.",
            13,
            Ui.MUTED,
            false),
        13);
    Ui.add(body, note, 14);
    Ui.add(body, Ui.text(this, "Seus dados", 21, Ui.INK, true), 28);
    option(
        body,
        "Importar medicamentos",
        "Trazer cadastros e histórico de um relatório, sem substituir os dados atuais.",
        () -> startActivity(new Intent(this, ImportActivity.class)));
    option(
        body,
        "Salvar backup",
        "Exportar medicamentos e histórico para um arquivo JSON.",
        this::exportBackup);
    option(
        body,
        "Restaurar backup",
        "Substitui os dados atuais após sua confirmação.",
        this::importBackup);
    long last = Prefs.get(this).getLong("last_delivery", 0);
    if (last > 0)
      Ui.add(
          body,
          Ui.text(
              this,
              "Último envio ao Android: "
                  + Instant.ofEpochMilli(last)
                      .atZone(ZoneId.systemDefault())
                      .format(DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm")),
              12,
              Ui.MUTED,
              false),
          24);
    long wake = Prefs.get(this).getLong("next_wake", 0);
    if (wake > 0)
      Ui.add(
          body,
          Ui.text(
              this,
              "Próxima verificação: "
                  + Instant.ofEpochMilli(wake)
                      .atZone(ZoneId.systemDefault())
                      .format(DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm:ss")),
              12,
              Ui.MUTED,
              false),
          9);
    String error = Prefs.get(this).getString("last_error", "");
    if (!error.isEmpty())
      Ui.add(body, Ui.text(this, "Última ocorrência: " + error, 12, Ui.AMBER, false), 14);
    Ui.add(
        body,
        Ui.text(
            this,
            "Dose 1.4.0 • Funciona sem conta e sem internet.\n"
                + "Os dados ficam neste aparelho. Faça backups antes de desinstalar. Cadastre"
                + " apenas os horários da sua prescrição.",
            12,
            Ui.MUTED,
            false),
        26);
  }

  private void permission(
      LinearLayout body, String title, String subtitle, boolean ok, Runnable action) {
    LinearLayout card = Ui.card(this);
    LinearLayout row = Ui.row(this);
    LinearLayout texts = Ui.column(this);
    texts.addView(Ui.text(this, title, 15, Ui.INK, true));
    Ui.add(texts, Ui.text(this, subtitle, 12, Ui.MUTED, false), 7);
    row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
    TextView status =
        Ui.chip(this, ok ? "Ativo" : "Ativar", ok ? Ui.LIGHT : Ui.WARNING, ok ? Ui.TEAL : Ui.AMBER);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
    lp.leftMargin = dp(12);
    row.addView(status, lp);
    card.addView(row);
    card.setOnClickListener(v -> action.run());
    Ui.add(body, card, 11);
  }

  private void option(LinearLayout body, String title, String subtitle, Runnable action) {
    LinearLayout box = Ui.card(this);
    box.addView(Ui.text(this, title, 15, Ui.INK, true));
    Ui.add(box, Ui.text(this, subtitle, 12, Ui.MUTED, false), 7);
    box.setOnClickListener(v -> action.run());
    Ui.add(body, box, 11);
  }

  private void toggle(LinearLayout body, String title, String subtitle, String key, boolean def) {
    LinearLayout box = Ui.card(this);
    LinearLayout row = Ui.row(this);
    LinearLayout label = Ui.column(this);
    label.addView(Ui.text(this, title, 15, Ui.INK, true));
    Ui.add(label, Ui.text(this, subtitle, 12, Ui.MUTED, false), 7);
    row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
    Switch toggle = new Switch(this);
    toggle.setChecked(Prefs.get(this).getBoolean(key, def));
    toggle.setContentDescription(title);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
    lp.leftMargin = dp(12);
    row.addView(toggle, lp);
    box.addView(row);
    toggle.setOnCheckedChangeListener(
        (v, on) -> Prefs.get(this).edit().putBoolean(key, on).apply());
    Ui.add(body, box, 11);
  }

  private void choose(String title, int[] values, String key, String suffix) {
    String[] labels = new String[values.length];
    for (int i = 0; i < values.length; i++) labels[i] = values[i] + suffix;
    new AlertDialog.Builder(this)
        .setTitle(title)
        .setItems(
            labels,
            (a, b) -> {
              Prefs.get(this).edit().putInt(key, values[b]).apply();
              render();
            })
        .show();
  }

  private String volumeLabel() {
    return "No mínimo "
        + AlarmAudio.minimumPercent(this)
        + "% durante o alarme; o volume anterior volta ao finalizar.";
  }

  private void volume() {
    SeekBar seek = new SeekBar(this);
    seek.setMax(99);
    seek.setProgress(AlarmAudio.minimumPercent(this) - 1);
    LinearLayout body = Ui.column(this);
    body.setPadding(dp(22), dp(12), dp(22), dp(10));
    TextView label =
        Ui.text(this, "Volume mínimo: " + AlarmAudio.minimumPercent(this) + "%", 16, Ui.INK, true);
    body.addView(label);
    Ui.add(body, seek, 14);
    seek.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar v, int progress, boolean user) {
            label.setText("Volume mínimo: " + (progress + 1) + "%");
          }

          public void onStartTrackingTouch(SeekBar v) {}

          public void onStopTrackingTouch(SeekBar v) {}
        });
    new AlertDialog.Builder(this)
        .setTitle("Volume mínimo do alarme")
        .setView(body)
        .setNegativeButton("Cancelar", null)
        .setPositiveButton(
            "Salvar",
            (a, b) -> {
              Prefs.get(this).edit().putInt("alarm_min_volume", seek.getProgress() + 1).apply();
              render();
            })
        .show();
  }

  private void dndOptions() {
    new AlertDialog.Builder(this)
        .setTitle("Som no Não perturbe")
        .setMessage(
            "Permita alarmes no modo Não perturbe ativo. Você também pode autorizar o Dose como"
                + " exceção de notificações. O Android continua controlando os modos que bloqueiam"
                + " todos os alarmes.")
        .setPositiveButton(
            "Permitir alarmes",
            (a, b) -> open(new Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS)))
        .setNeutralButton(
            "Autorizar Dose",
            (a, b) -> open(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)))
        .setNegativeButton("Voltar", null)
        .show();
  }

  private void notificationPermission() {
    if (Build.VERSION.SDK_INT >= 33
        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
      if (Prefs.get(this).getBoolean("asked_notifications", false)
          && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
        open(
            new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
      } else {
        Prefs.get(this).edit().putBoolean("asked_notifications", true).apply();
        requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 33);
      }
    } else
      open(
          new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
              .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
  }

  private void appearance() {
    String[] values = {"system", "light", "dark"};
    String current = Prefs.get(this).getString("theme", "system");
    int selected = Arrays.asList(values).indexOf(current);
    new AlertDialog.Builder(this)
        .setTitle("Aparência")
        .setSingleChoiceItems(
            new String[] {"Seguir o celular", "Modo claro", "Modo escuro"},
            Math.max(0, selected),
            (dialog, which) -> {
              Prefs.get(this).edit().putString("theme", values[which]).apply();
              dialog.dismiss();
              recreate();
            })
        .setNegativeButton("Voltar", null)
        .show();
  }

  private void testAlarm() {
    if (!Alerts.allowed(this)) {
      notificationPermission();
      return;
    }
    if (!AlarmEngine.exact(this)) {
      Ui.toast(this, "Ative Alarmes e lembretes para testar a pontualidade.");
      open(
          new Intent(
              Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
              Uri.parse("package:" + getPackageName())));
      return;
    }
    AlarmEngine.testIn(this, 15);
    Ui.toast(this, "Teste agendado. Bloqueie a tela e aguarde 15 segundos.");
    render();
  }

  @Override
  public void onRequestPermissionsResult(int r, String[] p, int[] g) {
    super.onRequestPermissionsResult(r, p, g);
    render();
  }

  private void open(Intent intent) {
    try {
      startActivity(intent);
    } catch (ActivityNotFoundException e) {
      Ui.toast(this, "Essa opção não está disponível neste Android.");
    }
  }

  private void editor(long id) {
    startActivity(new Intent(this, EditorActivity.class).putExtra("med_id", id));
  }

  private void take(Dose d) {
    if (d.scheduled > System.currentTimeMillis() + 60000)
      new AlertDialog.Builder(this)
          .setTitle("Confirmar antes do horário?")
          .setMessage(
              d.name
                  + "\n"
                  + d.detail
                  + "\nPrevisto para "
                  + time(d.scheduled)
                  + "\n\nRegistre apenas se você já tomou esta dose.")
          .setNegativeButton("Voltar", null)
          .setPositiveButton(
              "Já tomei",
              (a, b) -> {
                AlarmEngine.action(this, d.id, "TAKE");
                render();
              })
          .show();
    else {
      AlarmEngine.action(this, d.id, "TAKE");
      render();
    }
  }

  private void cancelReminders(Dose d) {
    LocalDate day = Instant.ofEpochMilli(d.scheduled).atZone(ZoneId.systemDefault()).toLocalDate();
    String date = day.format(DateTimeFormatter.ofPattern("d 'de' MMMM", pt));
    new AlertDialog.Builder(this)
        .setTitle("Cancelar lembretes de " + date + "?")
        .setMessage(
            d.name
                + "\n\nEncerra os avisos de todos os horários deste medicamento nesse dia."
                + " Você ainda poderá registrar Tomei. O estoque não será alterado."
                + "\n\nOs outros medicamentos e os próximos dias continuam normalmente.")
        .setNegativeButton("Voltar", null)
        .setPositiveButton(
            "Cancelar neste dia",
            (dialog, which) -> {
              AlarmEngine.cancelReminders(this, d.medId, day);
              render();
            })
        .show();
  }

  private void snooze(Dose d) {
    int[] values = {5, 10, 15, 30};
    new AlertDialog.Builder(this)
        .setTitle("Lembrar novamente em…")
        .setItems(
            new String[] {"5 minutos", "10 minutos", "15 minutos", "30 minutos"},
            (a, index) -> {
              try {
                Store.get(this).snooze(d.id, values[index], System.currentTimeMillis());
                Alerts.cancel(this, d.id);
                RingingService.stopSound(this);
                AlarmEngine.refresh(this);
                render();
              } catch (IllegalArgumentException e) {
                Ui.toast(this, e.getMessage());
              }
            })
        .show();
  }

  private void recordTime(Dose d) {
    ZonedDateTime current =
        Instant.ofEpochMilli(d.actual > 0 ? d.actual : System.currentTimeMillis())
            .atZone(ZoneId.systemDefault());
    new DatePickerDialog(
            this,
            (picker, y, m, day) ->
                new TimePickerDialog(
                        this,
                        (view, h, min) -> {
                          long actual =
                              LocalDate.of(y, m + 1, day)
                                  .atTime(h, min)
                                  .atZone(ZoneId.systemDefault())
                                  .toInstant()
                                  .toEpochMilli();
                          if (actual > System.currentTimeMillis() + 60000) {
                            Ui.toast(this, "Escolha o horário em que você já tomou a dose.");
                            return;
                          }
                          new AlertDialog.Builder(this)
                              .setTitle("Confirmar registro de tomada?")
                              .setMessage(
                                  d.name
                                      + "\n"
                                      + Instant.ofEpochMilli(actual)
                                          .atZone(ZoneId.systemDefault())
                                          .format(
                                              DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm"))
                                      + "\n\nConfirme apenas se essa dose foi realmente tomada.")
                              .setNegativeButton("Voltar", null)
                              .setPositiveButton(
                                  "Confirmar",
                                  (a, b) -> {
                                    synchronized (AlarmEngine.LOCK) {
                                      if ("taken".equals(d.status)) {
                                        ContentValues values = new ContentValues();
                                        values.put("actual", actual);
                                        Store.get(this)
                                            .getWritableDatabase()
                                            .update(
                                                "doses",
                                                values,
                                                "id=? AND status='taken'",
                                                new String[] {"" + d.id});
                                      } else Store.get(this).resolve(d.id, "taken", actual);
                                    }
                                    Alerts.cancel(this, d.id);
                                    AlarmEngine.refresh(this);
                                    render();
                                  })
                              .show();
                        },
                        current.getHour(),
                        current.getMinute(),
                        true)
                    .show(),
            current.getYear(),
            current.getMonthValue() - 1,
            current.getDayOfMonth())
        .show();
  }

  private void exportBackup() {
    Intent i =
        new Intent(Intent.ACTION_CREATE_DOCUMENT)
            .setType("application/json")
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_TITLE, "Dose-backup-" + LocalDate.now() + ".json");
    startActivityForResult(i, 101);
  }

  private void importBackup() {
    new AlertDialog.Builder(this)
        .setTitle("Restaurar um backup?")
        .setMessage(
            "Isso substitui seus medicamentos e seu histórico atuais. Salve um backup antes se"
                + " quiser guardá-los.")
        .setNegativeButton("Voltar", null)
        .setPositiveButton(
            "Escolher arquivo",
            (a, b) ->
                startActivityForResult(
                    new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .setType("*/*")
                        .addCategory(Intent.CATEGORY_OPENABLE),
                    102))
        .show();
  }

  @Override
  protected void onActivityResult(int r, int result, Intent data) {
    super.onActivityResult(r, result, data);
    if (result != RESULT_OK || data == null || data.getData() == null) return;
    try {
      if (r == 101) {
        byte[] bytes = Store.get(this).exportJson().toString(2).getBytes("UTF-8");
        try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
          out.write(bytes);
        }
        Ui.toast(this, "Backup salvo.");
      }
      if (r == 102) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream in = getContentResolver().openInputStream(data.getData())) {
          byte[] b = new byte[8192];
          int n;
          while ((n = in.read(b)) != -1) {
            if (buffer.size() + n > 20_000_000) throw new IOException("Backup muito grande.");
            buffer.write(b, 0, n);
          }
        }
        JSONObject root = new JSONObject(buffer.toString("UTF-8"));
        synchronized (AlarmEngine.LOCK) {
          Store.get(this).importJson(root);
          ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancelAll();
          RingingService.stopSound(this);
          Store.get(this).rezone(System.currentTimeMillis());
          AlarmEngine.refresh(this);
        }
        Ui.toast(this, "Backup restaurado.");
        render();
      }
    } catch (Exception e) {
      Ui.toast(
          this,
          "Não foi possível concluir: "
              + (e.getMessage() == null ? "arquivo inválido" : e.getMessage()));
    }
  }

  private void empty(
      LinearLayout body, String title, String subtitle, String button, Runnable action) {
    LinearLayout box = Ui.column(this);
    box.setGravity(Gravity.CENTER);
    box.setPadding(dp(14), dp(32), dp(14), dp(26));
    View icon = Ui.icon(this, "pill", Ui.TEAL, 48);
    box.addView(icon);
    TextView t = Ui.text(this, title, 20, Ui.INK, true);
    t.setGravity(Gravity.CENTER);
    Ui.add(box, t, 19);
    TextView s = Ui.text(this, subtitle, 14, Ui.MUTED, false);
    s.setGravity(Gravity.CENTER);
    Ui.add(box, s, 10);
    if (button != null) Ui.add(box, Ui.button(this, button, true, action), 22);
    Ui.add(body, box, 10);
  }

  private String time(long ms) {
    return Instant.ofEpochMilli(ms)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(ScheduleRules.CLOCK);
  }

  private String capitalize(String s) {
    return s.substring(0, 1).toUpperCase(pt) + s.substring(1);
  }

  private int dp(int n) {
    return Ui.dp(this, n);
  }

  static String days(int mask) {
    if (mask == 127) return "Todos os dias";
    String[] names = {"Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"};
    List<String> a = new ArrayList<>();
    for (int i = 0; i < 7; i++) if ((mask & (1 << i)) != 0) a.add(names[i]);
    return String.join(", ", a);
  }
}
