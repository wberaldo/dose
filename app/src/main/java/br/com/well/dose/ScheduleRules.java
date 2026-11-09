package br.com.well.dose;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Pure Java scheduling rules, shared with JVM and on-device tests. */
public final class ScheduleRules {
  public static final long WINDOW = 6 * 60 * 60 * 1000L;
  public static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

  public static final class Occurrence {
    public final String key;
    public final long when;

    Occurrence(String key, long when) {
      this.key = key;
      this.when = when;
    }
  }

  public static SortedSet<LocalTime> parseTimes(String value) {
    if (value == null || value.trim().isEmpty())
      throw new IllegalArgumentException("Adicione pelo menos um horário.");
    SortedSet<LocalTime> out = new TreeSet<>();
    try {
      for (String part : value.split(",")) out.add(LocalTime.parse(part.trim(), CLOCK));
    } catch (java.time.format.DateTimeParseException e) {
      throw new IllegalArgumentException("Um dos horários está inválido. Use HH:mm.");
    }
    if (out.isEmpty() || out.size() > 12)
      throw new IllegalArgumentException("Escolha de 1 a 12 horários.");
    return out;
  }

  public static List<Occurrence> occurrences(Medication m, LocalDate from, int count, ZoneId zone) {
    List<Occurrence> out = new ArrayList<>();
    if (!m.enabled || m.archived || m.days == 0 || m.asNeeded() || !m.review.isEmpty()) return out;
    LocalDate start = LocalDate.parse(m.start);
    LocalDate end = m.end.isEmpty() ? LocalDate.MAX : LocalDate.parse(m.end);
    for (int i = 0; i < count; i++) {
      LocalDate date = from.plusDays(i);
      if (date.isBefore(start)
          || date.isAfter(end)
          || (m.days & (1 << (date.getDayOfWeek().getValue() - 1))) == 0) continue;
      for (LocalTime t : parseTimes(m.times)) {
        // atZone shifts a DST gap forward and chooses the earlier offset in an overlap.
        long when = date.atTime(t).atZone(zone).toInstant().toEpochMilli();
        if (when >= m.created) out.add(new Occurrence(date + "@" + t.format(CLOCK), when));
      }
    }
    out.sort(Comparator.comparingLong(o -> o.when));
    return out;
  }

  public static long expiry(long scheduled, long following) {
    return Math.min(scheduled + WINDOW, following > scheduled ? following : Long.MAX_VALUE);
  }

  public static long retryAt(long now, long expires, int count, int minutes, int maximum) {
    if (count >= maximum + 1) return 0;
    long next = now + minutes * 60_000L;
    return next < expires ? next : 0;
  }

  public static long snoozeAt(long now, long expires, int minutes) {
    long next = now + minutes * 60_000L;
    if (next >= expires)
      throw new IllegalArgumentException("Esse adiamento ultrapassa a janela deste lembrete.");
    return next;
  }

  public static String number(double d) {
    return d == Math.rint(d)
        ? Long.toString((long) d)
        : String.format(Locale.forLanguageTag("pt-BR"), "%.2f", d).replaceAll("0+$", "");
  }

  public static void validate(Medication m) {
    if (m.name.trim().isEmpty() || m.name.length() > 100)
      throw new IllegalArgumentException("Informe o nome do medicamento (até 100 caracteres).");
    if (!Double.isFinite(m.amount)
        || m.amount < 0
        || m.amount > 10000
        || (m.amount == 0 && (m.enabled || m.review.isEmpty())))
      throw new IllegalArgumentException("Informe uma quantidade de dose válida.");
    if (!Double.isFinite(m.stock) || m.stock < -1 || m.stock > 1_000_000)
      throw new IllegalArgumentException("Informe um estoque válido ou deixe em branco.");
    if (!Arrays.asList("scheduled", "as_needed").contains(m.kind) || m.review.length() > 2000)
      throw new IllegalArgumentException("Tipo ou revisão do medicamento inválido.");
    if (m.enabled && !m.review.isEmpty())
      throw new IllegalArgumentException(
          "Confira os dados importados antes de ativar o medicamento.");
    if (m.days < 0 || m.days > 127 || (!m.asNeeded() && m.days == 0))
      throw new IllegalArgumentException("Selecione pelo menos um dia da semana.");
    LocalDate start = m.start.isEmpty() ? null : LocalDate.parse(m.start);
    if (start == null && m.enabled && !m.asNeeded())
      throw new IllegalArgumentException("Defina o primeiro dia dos lembretes.");
    LocalDate end = m.end.isEmpty() ? null : LocalDate.parse(m.end);
    if (end != null && start != null && end.isBefore(start))
      throw new IllegalArgumentException("A data final deve ser igual ou posterior à inicial.");
    if (m.unit.length() > 40 || m.notes.length() > 1000 || m.strength.length() > 80)
      throw new IllegalArgumentException("Revise o tamanho das informações.");
    if (!m.asNeeded() || !m.times.isEmpty()) parseTimes(m.times);
  }
}
