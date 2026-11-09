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
      end = "";
  public double amount = 1, stock = -1;
  public int days = 127, color = 0xFF137C70;
  public boolean enabled = true, archived = false;

  public String detail() {
    return ScheduleRules.number(amount) + " " + unit + (strength.isEmpty() ? "" : " • " + strength);
  }
}
