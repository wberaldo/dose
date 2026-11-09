package br.com.well.dose;

import java.time.*;
import java.util.*;

public final class ScheduleTests {
  private static int count;

  private static void check(boolean value, String description) {
    if (!value) throw new AssertionError(description);
    count++;
    System.out.println("PASS " + description);
  }

  private static Medication med() {
    Medication m = new Medication();
    m.name = "Exemplo de teste";
    m.start = "2026-11-01";
    m.created = 0;
    m.times = "08:00,20:00";
    return m;
  }

  public static void main(String[] args) {
    Medication m = med();
    ZoneId sp = ZoneId.of("America/Sao_Paulo");
    List<ScheduleRules.Occurrence> rows =
        ScheduleRules.occurrences(m, LocalDate.parse("2026-11-07"), 2, sp);
    check(rows.size() == 4, "duas doses por dia, inclusive virada do dia");
    check(
        rows.get(0).when
            == ZonedDateTime.of(2026, 11, 7, 8, 0, 0, 0, sp).toInstant().toEpochMilli(),
        "conversão correta de horário local em São Paulo");
    m.days = 1 << 0;
    rows = ScheduleRules.occurrences(m, LocalDate.parse("2026-11-07"), 7, sp);
    check(
        rows.size() == 2 && rows.get(0).key.startsWith("2026-11-09"),
        "seleção de dia da semana ISO");
    m = med();
    m.end = "2026-11-07";
    check(
        ScheduleRules.occurrences(m, LocalDate.parse("2026-11-07"), 5, sp).size() == 2,
        "data final inclusiva e encerramento da recorrência");
    m = med();
    m.created = ZonedDateTime.of(2026, 11, 7, 9, 0, 0, 0, sp).toInstant().toEpochMilli();
    rows = ScheduleRules.occurrences(m, LocalDate.parse("2026-11-07"), 1, sp);
    check(
        rows.size() == 1 && rows.get(0).key.endsWith("20:00"),
        "não disparar horários retroativos ao cadastrar ou editar");
    m = med();
    m.enabled = false;
    check(
        ScheduleRules.occurrences(m, LocalDate.parse("2026-11-07"), 7, sp).isEmpty(),
        "medicamento pausado não gera doses");
    m = med();
    m.archived = true;
    check(
        ScheduleRules.occurrences(m, LocalDate.parse("2026-11-07"), 7, sp).isEmpty(),
        "medicamento arquivado não gera doses");
    check(
        ScheduleRules.parseTimes("20:00,08:00,08:00").size() == 2,
        "horários duplicados são eliminados");
    long first = 100000, following = first + 3600000;
    check(
        ScheduleRules.expiry(first, following) == following,
        "repetição termina antes da próxima dose do mesmo medicamento");
    check(
        ScheduleRules.retryAt(1000, 2000000, 1, 5, 3) == 301000,
        "primeira repetição a partir do disparo real");
    check(
        ScheduleRules.retryAt(1000, 2000000, 4, 5, 3) == 0,
        "limite de três repetições após o aviso inicial");
    check(ScheduleRules.retryAt(1000, 10000, 1, 5, 3) == 0, "não repetir além da janela da dose");
    boolean invalid = false;
    try {
      ScheduleRules.snoozeAt(1000, 5000, 10);
    } catch (IllegalArgumentException e) {
      invalid = true;
    }
    check(invalid, "adiamento não atravessa a próxima dose");
    m = med();
    m.start = "2026-03-01";
    m.times = "02:30";
    rows =
        ScheduleRules.occurrences(
            m, LocalDate.parse("2026-03-08"), 1, ZoneId.of("America/New_York"));
    check(
        Instant.ofEpochMilli(rows.get(0).when).atZone(ZoneId.of("America/New_York")).getHour() == 3,
        "lacuna de horário de verão avança com uma ocorrência");
    m.times = "01:30";
    rows =
        ScheduleRules.occurrences(
            m, LocalDate.parse("2026-11-01"), 1, ZoneId.of("America/New_York"));
    check(rows.size() == 1, "horário de verão repetido não duplica a dose");
    m = med();
    m.amount = Double.NaN;
    invalid = false;
    try {
      ScheduleRules.validate(m);
    } catch (IllegalArgumentException e) {
      invalid = true;
    }
    check(invalid, "rejeitar quantidades NaN");
    m = med();
    m.days = 0;
    invalid = false;
    try {
      ScheduleRules.validate(m);
    } catch (IllegalArgumentException e) {
      invalid = true;
    }
    check(invalid, "rejeitar agenda sem dias ativos");
    m = med();
    m.times = "99:99";
    invalid = false;
    try {
      ScheduleRules.validate(m);
    } catch (Exception e) {
      invalid = true;
    }
    check(invalid, "rejeitar horários inválidos");
    System.out.println("Passed " + count + " scheduling checks.");
  }
}
