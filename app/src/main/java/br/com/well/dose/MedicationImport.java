package br.com.well.dose;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.json.*;

/** A merge import. Its original report stays separate from the live dose ledger. */
public final class MedicationImport {
  public static final int MAX_BYTES = 1_500_000;
  public final JSONObject original;
  public final List<Item> items = new ArrayList<>();
  public final String id, label, generatedAt;
  public final byte[] pdf;

  public static final class Item {
    public final JSONObject source;
    public final String key, status;
    public final Medication medication;
    public boolean selected = true;
    public long existingId;
    public boolean confirmed;

    Item(JSONObject source, String key, String status, Medication medication) {
      this.source = source;
      this.key = key;
      this.status = status;
      this.medication = medication;
    }
  }

  private static String string(JSONObject j, String key) {
    Object v = j.opt(key);
    return v == null || v == JSONObject.NULL ? "" : v.toString();
  }

  public static String digest(byte[] bytes) {
    try {
      StringBuilder s = new StringBuilder();
      for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes))
        s.append(String.format(Locale.ROOT, "%02x", b & 255));
      return s.toString();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public MedicationImport(String text, LocalDate today) throws JSONException {
    if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
      throw new IllegalArgumentException("Arquivo de importação muito grande (máximo 1,5 MB).");
    original = new JSONObject(text);
    if (!"dose-medication-import".equals(original.optString("format"))
        || original.optInt("version") != 1)
      throw new IllegalArgumentException(
          "Escolha um arquivo de importação do Dose. Um backup usa a opção Restaurar backup.");
    JSONObject source = original.getJSONObject("source");
    label = string(source, "filename");
    generatedAt = string(source, "generated_at");
    if (!generatedAt.isEmpty()) LocalDateTime.parse(generatedAt);
    JSONObject document = original.optJSONObject("document");
    if (document != null && !string(document, "data_base64").isEmpty()) {
      if (!"application/pdf".equals(document.optString("mime_type")))
        throw new IllegalArgumentException("Tipo de documento original inválido.");
      try {
        pdf = Base64.getDecoder().decode(document.getString("data_base64"));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("PDF original inválido.");
      }
      if (pdf.length > 1_000_000
          || pdf.length < 5
          || !new String(pdf, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-"))
        throw new IllegalArgumentException("PDF original inválido ou muito grande.");
      String actual = digest(pdf);
      if (!actual.equals(document.getString("sha256"))
          || !actual.equals(source.getString("sha256")))
        throw new IllegalArgumentException(
            "O PDF não corresponde à assinatura de integridade do arquivo.");
      id = actual;
    } else {
      pdf = null;
      id = digest(original.toString().getBytes(StandardCharsets.UTF_8));
    }
    JSONArray records = original.getJSONArray("medications");
    if (records.length() < 1 || records.length() > 500)
      throw new IllegalArgumentException("A importação precisa conter de 1 a 500 medicamentos.");
    Set<String> keys = new HashSet<>();
    for (int n = 0; n < records.length(); n++) {
      JSONObject j = records.getJSONObject(n);
      String key = j.getString("source_id"), status = j.getString("status");
      if (!key.matches("[a-f0-9]{64}") || !keys.add(key))
        throw new IllegalArgumentException(
            "Identificadores de medicamento inválidos ou repetidos.");
      if (!Arrays.asList("active", "paused", "ended", "future", "as_needed").contains(status))
        throw new IllegalArgumentException("Estado de medicamento não reconhecido.");
      Medication m = new Medication();
      m.name = j.getString("name");
      m.amount = j.isNull("amount") ? 0 : j.getDouble("amount");
      m.unit = string(j, "unit");
      if (m.unit.isEmpty()) m.unit = "unidade(s)";
      m.stock = j.isNull("stock") ? -1 : j.getDouble("stock");
      m.kind = "as_needed".equals(status) ? "as_needed" : "scheduled";
      List<String> reasons = new ArrayList<>();
      JSONArray review = j.optJSONArray("review_reasons");
      if (review != null)
        for (int i = 0; i < review.length(); i++) reasons.add(review.getString(i));
      if (m.amount == 0 && reasons.isEmpty())
        reasons.add("Confira a quantidade por dose antes de ativar este medicamento.");
      m.review = String.join("\n", reasons);
      JSONArray times = j.getJSONArray("times");
      List<String> clocks = new ArrayList<>();
      for (int i = 0; i < times.length(); i++) clocks.add(times.getString(i));
      m.times = String.join(",", clocks);
      JSONArray days = j.getJSONArray("weekdays");
      m.days = 0;
      for (int i = 0; i < days.length(); i++) {
        int d = days.getInt(i);
        if (d < 1 || d > 7) throw new IllegalArgumentException("Dia da semana inválido.");
        m.days |= 1 << (d - 1);
      }
      m.start = string(j, "start_date");
      m.end = string(j, "end_date");
      boolean expired = !m.end.isEmpty() && LocalDate.parse(m.end).isBefore(today);
      m.enabled =
          ("active".equals(status) || "as_needed".equals(status)) && m.review.isEmpty() && !expired;
      if (m.start.isEmpty() && "active".equals(status) && !expired) m.start = today.toString();
      String condition = string(j, "condition");
      m.notes =
          (condition.isEmpty() ? "" : "Condição informada: " + condition + "\n")
              + string(j, "notes");
      ScheduleRules.validate(m);
      items.add(new Item(j, key, status, m));
    }
    JSONArray months = original.optJSONArray("monthly_history");
    if (months != null) {
      if (months.length() > 24) throw new IllegalArgumentException("Há meses demais no arquivo.");
      for (int i = 0; i < months.length(); i++) {
        JSONObject month = months.getJSONObject(i);
        YearMonth ym = YearMonth.of(month.getInt("year"), month.getInt("month"));
        if (month.optBoolean("actual_times_available", false))
          throw new IllegalArgumentException("Este formato aceita o histórico resumido por dia.");
        JSONArray rows = month.getJSONArray("rows");
        if (rows.length() > 500) throw new IllegalArgumentException("Histórico muito grande.");
        Set<String> rowKeys = new HashSet<>();
        for (int r = 0; r < rows.length(); r++) {
          JSONObject row = rows.getJSONObject(r);
          String key = row.getString("source_id");
          if (!keys.contains(key) || !rowKeys.add(key))
            throw new IllegalArgumentException(
                "Histórico sem medicamento correspondente ou duplicado.");
          JSONArray values = row.getJSONArray("days");
          Set<String> dates = new HashSet<>();
          if (values.length() > 31)
            throw new IllegalArgumentException("Histórico diário inválido.");
          for (int d = 0; d < values.length(); d++) {
            JSONObject day = values.getJSONObject(d);
            LocalDate date = LocalDate.parse(day.getString("date"));
            if (!YearMonth.from(date).equals(ym) || !dates.add(date.toString()))
              throw new IllegalArgumentException("Datas repetidas ou fora do mês do relatório.");
            JSONArray states = day.getJSONArray("states");
            if (states.length() > 12)
              throw new IllegalArgumentException("Estados diários em excesso.");
            for (int s = 0; s < states.length(); s++)
              if (!Arrays.asList("confirmed", "planned", "unknown", "skipped", "interrupted")
                  .contains(states.getString(s)))
                throw new IllegalArgumentException("Estado do histórico não reconhecido.");
          }
        }
      }
    }
  }

  public int selectedCount() {
    int n = 0;
    for (Item i : items) if (i.selected) n++;
    return n;
  }

  public int activeCount() {
    int n = 0;
    for (Item i : items)
      if (i.selected && i.existingId == 0 && i.medication.enabled && !i.medication.asNeeded()) n++;
    return n;
  }

  public static String statusLabel(String status) {
    switch (status) {
      case "active":
        return "Programado";
      case "as_needed":
        return "Sob demanda";
      case "ended":
        return "Encerrado";
      case "future":
        return "Início futuro";
      default:
        return "Pausado";
    }
  }
}
