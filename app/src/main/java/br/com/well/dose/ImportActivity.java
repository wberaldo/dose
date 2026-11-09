package br.com.well.dose;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.concurrent.*;
import org.json.*;

public final class ImportActivity extends Activity {
  private MedicationImport report;
  private File cache;
  private TextView finishButton;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();

  @Override
  public void onCreate(Bundle state) {
    Ui.prepare(this);
    super.onCreate(state);
    if (state != null && state.getString("cache") != null) {
      cache = new File(state.getString("cache"));
      if (cache.isFile()) {
        load();
        return;
      }
    }
    String initial = getIntent().getStringExtra("cache_file");
    if (initial != null)
      try {
        File f = new File(initial).getCanonicalFile();
        if (f.getParentFile().equals(getCacheDir().getCanonicalFile()) && f.isFile()) {
          cache = f;
          load();
          return;
        }
      } catch (IOException e) {
      }
    intro();
  }

  private LinearLayout body(String title, String subtitle) {
    LinearLayout root = Ui.root(this);
    LinearLayout top = Ui.row(this);
    top.setPadding(dp(22), dp(12), dp(22), dp(8));
    TextView back = Ui.button(this, "‹ Voltar", false, this::finish);
    top.addView(back);
    TextView brand = Ui.text(this, "dose", 25, Ui.INK, true);
    brand.setGravity(Gravity.END);
    top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
    root.addView(top);
    LinearLayout body = Ui.scroll(root);
    Ui.title(body, title, subtitle);
    return body;
  }

  private void intro() {
    LinearLayout b =
        body("Trazer sua rotina", "Importe um arquivo preparado a partir do relatório MyTherapy.");
    Ui.add(
        b,
        Ui.text(
            this,
            "Você confere os nomes, quantidades, horários e estados antes de salvar. Os cadastros"
                + " que já existem são mantidos.",
            16,
            Ui.MUTED,
            false),
        24);
    Ui.add(b, Ui.button(this, "Escolher arquivo de importação", true, this::choose), 24);
    Ui.add(
        b,
        Ui.text(
            this,
            "Escolha o arquivo .json de importação do Dose. O PDF original e o histórico por dia"
                + " podem vir no próprio arquivo e ficam disponíveis em Histórico → Relatórios"
                + " importados.",
            14,
            Ui.MUTED,
            false),
        18);
  }

  private void choose() {
    startActivityForResult(
        new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("*/*")
            .addCategory(Intent.CATEGORY_OPENABLE),
        201);
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request != 201 || result != RESULT_OK || data == null || data.getData() == null) return;
    try {
      cache = File.createTempFile("dose-import-", ".json", getCacheDir());
      try (InputStream in = getContentResolver().openInputStream(data.getData());
          OutputStream out = new FileOutputStream(cache)) {
        byte[] bytes = new byte[8192];
        int n, total = 0;
        while ((n = in.read(bytes)) != -1) {
          total += n;
          if (total > MedicationImport.MAX_BYTES)
            throw new IOException("Arquivo maior que 1,5 MB.");
          out.write(bytes, 0, n);
        }
      }
      load();
    } catch (Exception e) {
      Ui.toast(this, "Não foi possível abrir: " + e.getMessage());
      intro();
    }
  }

  private void load() {
    LinearLayout b = body("Lendo sua rotina", "Conferindo o arquivo de importação…");
    Ui.add(b, new ProgressBar(this), 28);
    worker.execute(
        () -> {
          try {
            String text =
                new String(
                    java.nio.file.Files.readAllBytes(cache.toPath()), StandardCharsets.UTF_8);
            if (text.startsWith("%PDF-"))
              throw new IllegalArgumentException(
                  "Escolha o arquivo de importação .json preparado a partir do PDF.");
            JSONObject data = new JSONObject(text);
            JSONArray draft = data.optJSONArray("_dose_preview");
            data.remove("_dose_preview");
            MedicationImport parsed = new MedicationImport(data.toString(), LocalDate.now());
            if (draft != null)
              for (int n = 0; n < draft.length(); n++) {
                JSONObject j = draft.getJSONObject(n);
                for (MedicationImport.Item item : parsed.items)
                  if (item.key.equals(j.getString("key"))) {
                    Medication m = item.medication;
                    item.selected = j.getBoolean("selected");
                    item.confirmed = j.optBoolean("confirmed");
                    m.amount = j.getDouble("amount");
                    m.unit = j.getString("unit");
                    m.times = j.getString("times");
                    m.start = j.getString("start");
                    m.end = j.getString("end");
                    m.enabled = j.getBoolean("enabled");
                    m.review = j.getString("review");
                    ScheduleRules.validate(m);
                    break;
                  }
              }
            Store.get(this).previewImport(parsed);
            runOnUiThread(
                () -> {
                  if (!isFinishing() && !isDestroyed()) {
                    report = parsed;
                    preview();
                  }
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  if (!isFinishing() && !isDestroyed()) {
                    Ui.toast(this, "Arquivo não importado: " + e.getMessage());
                    intro();
                  }
                });
          }
        });
  }

  private void preview() {
    LinearLayout b =
        body(
            "Confira sua importação",
            report.items.size()
                + " registros encontrados • "
                + report.generatedAt.replace('T', ' '));
    Ui.add(
        b,
        Ui.text(
            this,
            "Os novos lembretes começam após importar. Informações incertas ficam pausadas para"
                + " revisão. Os registros do mês são guardados como resumo, sem inventar horas de"
                + " tomada.",
            14,
            Ui.MUTED,
            false),
        18);
    if (report.pdf != null)
      Ui.add(
          b,
          Ui.button(
              this,
              "Ver relatório original e histórico",
              false,
              () ->
                  startActivity(
                      new Intent(this, ReportActivity.class)
                          .putExtra("cache_file", cache.getAbsolutePath()))),
          14);
    for (MedicationImport.Item item : report.items) {
      Medication m = item.medication;
      LinearLayout card = Ui.card(this);
      CheckBox include = new CheckBox(this);
      include.setText(m.name);
      include.setTextSize(17);
      include.setTextColor(Ui.INK);
      include.setChecked(item.selected);
      include.setOnCheckedChangeListener(
          (button, on) -> {
            item.selected = on;
            updateFooter();
          });
      card.addView(include);
      Ui.add(card, Ui.chip(this, MedicationImport.statusLabel(item.status), Ui.LIGHT, Ui.TEAL), 8);
      String frequency = item.source.optString("frequency_text", "");
      Ui.add(card, Ui.text(this, frequency, 14, Ui.MUTED, false), 12);
      if (!m.notes.isEmpty()) Ui.add(card, Ui.text(this, m.notes, 13, Ui.MUTED, false), 9);
      if (!m.end.isEmpty())
        Ui.add(
            card,
            Ui.text(
                this,
                "Término: "
                    + LocalDate.parse(m.end)
                        .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    + (item.source.optBoolean("end_date_calculated")
                        ? " • calculado pelos dias restantes"
                        : ""),
                13,
                Ui.TEAL,
                true),
            9);
      if (item.existingId > 0)
        Ui.add(
            card,
            Ui.text(
                this,
                "Já cadastrado: seus horários, estoque e histórico serão mantidos.",
                13,
                Ui.TEAL,
                true),
            10);
      else {
        if (!m.review.isEmpty())
          Ui.add(card, Ui.text(this, "Precisa de revisão\n" + m.review, 13, Ui.AMBER, true), 12);
        Ui.add(
            card,
            Ui.text(
                this,
                m.asNeeded()
                    ? "Sem alarmes automáticos"
                    : m.enabled ? "Lembretes serão ativados" : "Lembretes permanecerão pausados",
                13,
                m.enabled ? Ui.TEAL : Ui.MUTED,
                true),
            10);
        Ui.add(card, Ui.button(this, "Revisar cadastro", false, () -> edit(item)), 12);
      }
      Ui.add(b, card, 14);
    }
    LinearLayout root = (LinearLayout) b.getParent().getParent();
    LinearLayout footer = Ui.column(this);
    footer.setPadding(dp(22), dp(10), dp(22), dp(14));
    footer.setBackgroundColor(Ui.BG);
    finishButton = Ui.button(this, "", true, this::confirm);
    footer.addView(finishButton);
    root.addView(footer);
    updateFooter();
  }

  private void updateFooter() {
    if (finishButton != null)
      finishButton.setText(
          "Importar "
              + report.selectedCount()
              + " cadastros • "
              + report.activeCount()
              + " com alarmes");
  }

  private EditText input(LinearLayout b, String label, String value, int type) {
    Ui.add(b, Ui.text(this, label, 13, Ui.INK, true), 12);
    EditText e = new EditText(this);
    e.setTextColor(Ui.INK);
    e.setTextSize(15);
    e.setInputType(type);
    e.setText(value);
    e.setPadding(dp(12), dp(10), dp(12), dp(10));
    e.setBackground(Ui.outline(Ui.SURFACE, Ui.LINE, 12, this));
    Ui.add(b, e, 7);
    return e;
  }

  private void edit(MedicationImport.Item item) {
    Medication m = item.medication;
    ScrollView scroll = new ScrollView(this);
    LinearLayout fields = Ui.column(this);
    fields.setPadding(dp(20), dp(8), dp(20), dp(16));
    scroll.addView(fields);
    if (!m.review.isEmpty()) Ui.add(fields, Ui.text(this, m.review, 14, Ui.AMBER, false), 0);
    EditText quantity =
        input(
            fields,
            "Quantidade por dose",
            m.amount == 0 ? "" : ScheduleRules.number(m.amount),
            8194);
    EditText unit = input(fields, "Unidade", m.unit, 1);
    EditText clocks =
        input(
            fields,
            m.asNeeded()
                ? "Sob demanda: sem horários fixos"
                : "Horários (HH:mm, separados por vírgula)",
            m.times,
            1);
    clocks.setEnabled(!m.asNeeded());
    EditText start = input(fields, "Primeiro dia dos lembretes (AAAA-MM-DD)", m.start, 1);
    EditText end = input(fields, "Último dia (AAAA-MM-DD), se houver", m.end, 1);
    CheckBox verified = new CheckBox(this);
    verified.setText("Conferi estes dados com minha receita");
    verified.setTextColor(Ui.INK);
    verified.setChecked(m.review.isEmpty());
    Ui.add(fields, verified, 15);
    CheckBox active = new CheckBox(this);
    active.setText(
        m.asNeeded()
            ? "Manter disponível sob demanda, sem alarmes"
            : "Ativar lembretes após importar");
    active.setTextColor(Ui.INK);
    active.setChecked(m.enabled);
    Ui.add(fields, active, 8);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle(m.name)
            .setView(scroll)
            .setNegativeButton("Voltar", null)
            .setPositiveButton("Salvar revisão", null)
            .create();
    dialog.setOnShowListener(
        v ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    button -> {
                      try {
                        Medication candidate = new Medication();
                        candidate.name = m.name;
                        candidate.strength = m.strength;
                        candidate.stock = m.stock;
                        candidate.notes = m.notes;
                        candidate.color = m.color;
                        String q = quantity.getText().toString().trim();
                        candidate.amount =
                            q.isEmpty() ? 0 : Double.parseDouble(q.replace(',', '.'));
                        candidate.unit = unit.getText().toString().trim();
                        candidate.times = clocks.getText().toString().trim();
                        candidate.days = m.days;
                        candidate.kind = m.kind;
                        candidate.start = start.getText().toString().trim();
                        candidate.end = end.getText().toString().trim();
                        candidate.enabled = active.isChecked();
                        candidate.review = verified.isChecked() ? "" : m.review;
                        if (candidate.enabled && !verified.isChecked())
                          throw new IllegalArgumentException("Confirme a revisão antes de ativar.");
                        ScheduleRules.validate(candidate);
                        m.amount = candidate.amount;
                        m.unit = candidate.unit;
                        m.times = candidate.times;
                        m.start = candidate.start;
                        m.end = candidate.end;
                        m.enabled = candidate.enabled;
                        m.review = candidate.review;
                        item.confirmed = verified.isChecked();
                        dialog.dismiss();
                        preview();
                      } catch (Exception e) {
                        Ui.toast(
                            this, e.getMessage() == null ? "Confira os dados." : e.getMessage());
                      }
                    }));
    dialog.show();
  }

  private void confirm() {
    new AlertDialog.Builder(this)
        .setTitle("Concluir importação?")
        .setMessage(
            report.selectedCount()
                + " cadastros selecionados. "
                + report.activeCount()
                + " novos medicamentos terão alarmes. O relatório completo será preservado, e os"
                + " cadastros existentes não serão substituídos.")
        .setNegativeButton("Revisar", null)
        .setPositiveButton(
            "Importar",
            (a, b) -> {
              try {
                int[] result;
                synchronized (AlarmEngine.LOCK) {
                  result = Store.get(this).mergeImport(report, System.currentTimeMillis());
                  AlarmEngine.refresh(this);
                }
                Ui.toast(
                    this,
                    result[0]
                        + " medicamentos adicionados; "
                        + result[1]
                        + " cadastros existentes mantidos.");
                startActivity(
                    new Intent(this, MainActivity.class)
                        .putExtra("tab", 1)
                        .addFlags(
                            Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                finish();
              } catch (Exception e) {
                Ui.toast(this, "Não foi possível concluir a importação: " + e.getMessage());
              }
            })
        .show();
  }

  @Override
  protected void onSaveInstanceState(Bundle b) {
    super.onSaveInstanceState(b);
    if (cache != null) {
      b.putString("cache", cache.getAbsolutePath());
      if (report != null)
        try {
          JSONObject data = new JSONObject(report.original.toString());
          JSONArray draft = new JSONArray();
          for (MedicationImport.Item item : report.items) {
            Medication m = item.medication;
            draft.put(
                new JSONObject()
                    .put("key", item.key)
                    .put("selected", item.selected)
                    .put("confirmed", item.confirmed)
                    .put("amount", m.amount)
                    .put("unit", m.unit)
                    .put("times", m.times)
                    .put("start", m.start)
                    .put("end", m.end)
                    .put("enabled", m.enabled)
                    .put("review", m.review));
          }
          data.put("_dose_preview", draft);
          try (OutputStream out = new FileOutputStream(cache)) {
            out.write(data.toString().getBytes(StandardCharsets.UTF_8));
          }
        } catch (Exception e) {
          Ui.toast(
              this, "A revisão não pôde ser guardada; o arquivo original permanece disponível.");
        }
    }
  }

  @Override
  protected void onDestroy() {
    worker.shutdownNow();
    super.onDestroy();
  }

  private int dp(int n) {
    return Ui.dp(this, n);
  }
}
