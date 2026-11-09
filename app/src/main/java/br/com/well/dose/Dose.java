package br.com.well.dose;

public final class Dose {
  public long id, medId, scheduled, expires, nextAlert, lastAlert, actual;
  public String key, name, detail, notes, status;
  public int color, alerts;
  public boolean snoozed;
  public double amount;

  public boolean actionable() {
    return "pending".equals(status);
  }
}
