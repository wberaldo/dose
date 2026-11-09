package br.com.well.dose;

import android.app.*;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.json.*;

/** Imported summaries are evidence from the source, never fabricated dose events. */
public final class ReportActivity extends Activity {
  private JSONObject source;
  private boolean showFuture;
  private PdfRenderer renderer;
  private ParcelFileDescriptor descriptor;
  private Bitmap image;
  private int page;
  private float zoom = 1;

  @Override
  public void onCreate(Bundle state) {
    Ui.prepare(this);
    super.onCreate(state);
    try {
      String cached = getIntent().getStringExtra("cache_file");
      if (cached != null) {
        File file = new File(cached).getCanonicalFile();
        if (!file.getParentFile().equals(getCacheDir().getCanonicalFile())
            || file.length() > MedicationImport.MAX_BYTES)
          throw new IOException("Arquivo indisponível.");
        source =
            new JSONObject(
                new String(
                    java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
      } else source = Store.get(this).importedSource(getIntent().getStringExtra("source_id"));
      if (source == null) throw new IOException("Relatório não encontrado.");
      if (state != null) {
        showFuture = state.getBoolean("future");
        page = state.getInt("page");
      }
      summary();
    } catch (Exception e) {
      Ui.toast(this, "Não foi possível abrir o relatório: " + e.getMessage());
      finish();
    }
  }

  private LinearLayout root(String heading) {
    LinearLayout root = Ui.root(this);
    LinearLayout top = Ui.row(this);
    top.setPadding(dp(18), dp(10), dp(18), dp(8));
    top.addView(Ui.button(this, "‹ Voltar", false, this::finish));
    TextView title = Ui.text(this, heading, 18, Ui.INK, true);
    title.setGravity(Gravity.END);
    top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    root.addView(top);
    return root;
  }

  private void summary() {
    try {
      LinearLayout b = Ui.scroll(root("Relatório importado"));
      JSONObject meta = source.getJSONObject("source");
      Ui.title(
          b,
          meta.optString("app", "Relatório"),
          meta.optString("generated_at", "").replace('T', ' '));
      if (!meta.optString("profile", "").isEmpty())
        Ui.add(b, Ui.text(this, "Perfil: " + meta.getString("profile"), 14, Ui.MUTED, false), 12);
      Ui.add(
          b,
          Ui.text(
              this,
              "Os estados abaixo foram copiados do relatório. Eles não informam a hora real da"
                  + " tomada e não alteram estoque ou confirmações feitas no Dose.",
              14,
              Ui.MUTED,
              false),
          18);
      if (source.optJSONObject("document") != null)
        Ui.add(b, Ui.button(this, "Ver PDF original", false, this::openPdf), 18);
      CheckBox future = new CheckBox(this);
      future.setText("Mostrar também o planejamento futuro do relatório");
      future.setTextColor(Ui.INK);
      future.setChecked(showFuture);
      future.setOnCheckedChangeListener(
          (button, on) -> {
            showFuture = on;
            summary();
          });
      Ui.add(b, future, 18);
      LocalDate cutoff = LocalDate.parse(meta.getString("generated_at").substring(0, 10));
      JSONArray months = source.optJSONArray("monthly_history");
      if (months != null)
        for (int h = 0; h < months.length(); h++) {
          JSONObject month = months.getJSONObject(h);
          YearMonth ym = YearMonth.of(month.getInt("year"), month.getInt("month"));
          Ui.add(
              b,
              Ui.text(
                  this,
                  ym.format(
                      DateTimeFormatter.ofPattern(
                          "MMMM 'de' yyyy", Locale.forLanguageTag("pt-BR"))),
                  22,
                  Ui.INK,
                  true),
              26);
          JSONArray rows = month.getJSONArray("rows");
          for (int r = 0; r < rows.length(); r++) {
            JSONObject row = rows.getJSONObject(r);
            LinearLayout card = Ui.card(this);
            card.addView(Ui.text(this, row.getString("name"), 17, Ui.INK, true));
            JSONArray days = row.getJSONArray("days");
            int shown = 0;
            for (int d = 0; d < days.length(); d++) {
              JSONObject value = days.getJSONObject(d);
              LocalDate date = LocalDate.parse(value.getString("date"));
              if (!showFuture && date.isAfter(cutoff)) continue;
              JSONArray states = value.getJSONArray("states");
              if (states.length() == 0) continue;
              Map<String, Integer> counts = new LinkedHashMap<>();
              for (int s = 0; s < states.length(); s++) {
                String v = states.getString(s);
                counts.put(v, counts.getOrDefault(v, 0) + 1);
              }
              List<String> labels = new ArrayList<>();
              for (Map.Entry<String, Integer> e : counts.entrySet())
                labels.add(e.getValue() + " " + stateLabel(e.getKey(), e.getValue()));
              Ui.add(
                  card,
                  Ui.text(
                      this,
                      date.format(DateTimeFormatter.ofPattern("dd/MM"))
                          + " • "
                          + String.join(" · ", labels),
                      13,
                      Ui.MUTED,
                      false),
                  10);
              shown++;
            }
            if (shown == 0)
              Ui.add(
                  card,
                  Ui.text(this, "Sem marcações até a geração do relatório.", 13, Ui.MUTED, false),
                  10);
            Ui.add(b, card, 13);
          }
        }
      Ui.add(b, Ui.text(this, "Cadastros no documento", 22, Ui.INK, true), 28);
      JSONArray meds = source.getJSONArray("medications");
      for (int i = 0; i < meds.length(); i++) {
        JSONObject m = meds.getJSONObject(i);
        LinearLayout card = Ui.card(this);
        card.addView(Ui.text(this, m.getString("name"), 17, Ui.INK, true));
        Ui.add(
            card,
            Ui.chip(this, MedicationImport.statusLabel(m.getString("status")), Ui.LIGHT, Ui.TEAL),
            9);
        Ui.add(card, Ui.text(this, m.optString("frequency_text", ""), 13, Ui.MUTED, false), 10);
        String note = m.optString("notes", "");
        if (!note.isEmpty())
          Ui.add(card, Ui.text(this, "Nota do relatório: " + note, 13, Ui.MUTED, false), 9);
        String condition = m.optString("condition", "");
        if (!condition.isEmpty())
          Ui.add(card, Ui.text(this, "Condição informada: " + condition, 13, Ui.MUTED, false), 9);
        Ui.add(b, card, 12);
      }
    } catch (Exception e) {
      Ui.toast(this, e.getMessage());
    }
  }

  private String stateLabel(String status, int n) {
    String word;
    switch (status) {
      case "confirmed":
        word = "confirmado";
        break;
      case "planned":
        word = "planejado";
        break;
      case "skipped":
        word = "ignorado";
        break;
      case "interrupted":
        word = "interrompido";
        break;
      default:
        word = "desconhecido";
    }
    return word + (n == 1 ? "" : "s");
  }

  private void openPdf() {
    try {
      if (renderer == null) {
        JSONObject doc = source.getJSONObject("document");
        byte[] bytes = Base64.getDecoder().decode(doc.getString("data_base64"));
        if (bytes.length > 1_000_000
            || !MedicationImport.digest(bytes).equals(doc.getString("sha256")))
          throw new IOException("PDF inválido.");
        File file = new File(getCacheDir(), "relatorio-" + MedicationImport.digest(bytes) + ".pdf");
        try (OutputStream out = new FileOutputStream(file)) {
          out.write(bytes);
        }
        descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        renderer = new PdfRenderer(descriptor);
      }
      renderPdf();
    } catch (Exception e) {
      Ui.toast(this, "Não foi possível abrir o PDF: " + e.getMessage());
    }
  }

  private void renderPdf() {
    try {
      LinearLayout root = root("PDF original");
      root.addView(Ui.button(this, "‹ Voltar ao resumo", false, this::summary));
      LinearLayout controls = Ui.row(this);
      controls.setPadding(dp(10), dp(6), dp(10), dp(6));
      controls.addView(
          Ui.button(
              this,
              "‹",
              false,
              () -> {
                if (page > 0) {
                  page--;
                  renderPdf();
                }
              }),
          new LinearLayout.LayoutParams(0, -2, 1));
      TextView count =
          Ui.text(this, (page + 1) + " / " + renderer.getPageCount(), 14, Ui.INK, true);
      count.setGravity(Gravity.CENTER);
      controls.addView(count, new LinearLayout.LayoutParams(0, -2, 1));
      controls.addView(
          Ui.button(
              this,
              "›",
              false,
              () -> {
                if (page + 1 < renderer.getPageCount()) {
                  page++;
                  renderPdf();
                }
              }),
          new LinearLayout.LayoutParams(0, -2, 1));
      controls.addView(
          Ui.button(
              this,
              zoom > 1 ? "−" : "+",
              false,
              () -> {
                zoom = zoom > 1 ? 1 : 2;
                renderPdf();
              }),
          new LinearLayout.LayoutParams(0, -2, 1));
      root.addView(controls);
      ScrollView vertical = new ScrollView(this);
      HorizontalScrollView horizontal = new HorizontalScrollView(this);
      ImageView view = new ImageView(this);
      horizontal.addView(view);
      vertical.addView(horizontal);
      root.addView(vertical, new LinearLayout.LayoutParams(-1, 0, 1));
      if (image != null) {
        image.recycle();
        image = null;
      }
      try (PdfRenderer.Page p = renderer.openPage(page)) {
        int width =
            Math.min(2200, Math.round(getResources().getDisplayMetrics().widthPixels * zoom));
        int height = Math.round(width * p.getHeight() / (float) p.getWidth());
        image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        image.eraseColor(Color.WHITE);
        p.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        view.setImageBitmap(image);
        view.setLayoutParams(new android.widget.FrameLayout.LayoutParams(width, height));
      }
    } catch (Exception e) {
      Ui.toast(this, e.getMessage());
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle b) {
    super.onSaveInstanceState(b);
    b.putBoolean("future", showFuture);
    b.putInt("page", page);
  }

  @Override
  protected void onDestroy() {
    if (renderer != null) renderer.close();
    try {
      if (descriptor != null) descriptor.close();
    } catch (IOException e) {
    }
    super.onDestroy();
  }

  private int dp(int n) {
    return Ui.dp(this, n);
  }
}
