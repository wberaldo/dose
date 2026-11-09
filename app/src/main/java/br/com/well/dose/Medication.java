package br.com.well.dose;

import java.time.*;

public final class Medication {
  public long id, created;
  public String name = "",
      strength = "",
      unit = "comprimido(s)",
      notes = "",
      times = "08:00",
      start = LocalDate.now().toString(),
      end = "",
      kind = "scheduled",
      review = "",
      icon = "capsule";
  public double amount = 1, stock = -1;
  public int days = 127, color = 0xFF137C70, secondaryColor = 0xFF137C70;
  public boolean enabled = true, archived = false;

  public String detail() {
    if (amount == 0) return "Quantidade não informada • revisar";
    return ScheduleRules.number(amount) + " " + unit + (strength.isEmpty() ? "" : " • " + strength);
  }

  public boolean asNeeded() {
    return "as_needed".equals(kind);
  }
}
