package br.com.well.dose;

import java.util.*;

/** Stable identifiers used by the database, backups and vector drawing. */
public final class MedicineStyle {
  private MedicineStyle() {}

  public static final List<String> ICONS =
      Collections.unmodifiableList(
          Arrays.asList(
              "capsule",
              "capsule_short",
              "tablet_round",
              "tablet_scored",
              "tablet_cross",
              "tablet_oval",
              "tablet_oblong",
              "tablet_triangle",
              "tablet_square",
              "tablet_diamond",
              "drops",
              "bottle"));
  private static final String[] LABELS = {
    "Cápsula",
    "Cápsula curta",
    "Redondo",
    "Redondo com sulco",
    "Redondo cruzado",
    "Oval",
    "Alongado",
    "Triangular",
    "Quadrado",
    "Losango",
    "Gotas",
    "Frasco"
  };

  public static boolean valid(String icon) {
    return ICONS.contains(icon);
  }

  public static boolean twoColors(String icon) {
    return "capsule".equals(icon) || "capsule_short".equals(icon);
  }

  public static String label(String icon) {
    int index = ICONS.indexOf(icon);
    return LABELS[index < 0 ? 0 : index];
  }
}
