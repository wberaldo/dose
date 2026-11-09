package br.com.well.dose;

import android.app.AlertDialog;
import android.content.Context;
import android.view.*;
import android.widget.*;
import java.util.function.Consumer;

/** Live preview with independent capsule halves and a single color for other forms. */
public final class AppearancePicker extends LinearLayout {
  private String icon;
  private int primary, secondary;
  private static final int[] COLORS = {
    0xFFFFFFFF, 0xFFFFF1C9, 0xFFFFD554, 0xFFF28A39,
    0xFFD94343, 0xFFF1A8C3, 0xFFAB3564, 0xFF9060B4,
    0xFF387AC1, 0xFF96CEE8, 0xFF137C70, 0xFFA8D5AB,
    0xFF99613F, 0xFFD5B593, 0xFF9A9FA2, 0xFF303436
  };
  private static final String[] COLOR_NAMES = {
    "Branco", "Creme", "Amarelo", "Laranja", "Vermelho", "Rosa", "Vinho", "Roxo",
    "Azul", "Azul claro", "Verde", "Verde claro", "Marrom", "Bege", "Cinza", "Preto"
  };

  public AppearancePicker(Context c, String icon, int primary, int secondary) {
    super(c);
    setOrientation(VERTICAL);
    this.icon = MedicineStyle.valid(icon) ? icon : "capsule";
    this.primary = primary;
    this.secondary = secondary;
    render();
  }

  public String icon() {
    return icon;
  }

  public int primaryColor() {
    return primary;
  }

  public int secondaryColor() {
    return secondary;
  }

  private void render() {
    removeAllViews();
    Context c = getContext();
    LinearLayout preview = Ui.card(c);
    LinearLayout line = Ui.row(c);
    line.addView(Ui.medicine(c, icon, primary, secondary, 76));
    LinearLayout labels = Ui.column(c);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
    lp.leftMargin = dp(14);
    line.addView(labels, lp);
    labels.addView(Ui.text(c, MedicineStyle.label(icon), 19, Ui.INK, true));
    Ui.add(
        labels,
        Ui.text(
            c,
            MedicineStyle.twoColors(icon)
                ? "Duas cores, uma em cada metade"
                : "Uma cor para este formato",
            13,
            Ui.MUTED,
            false),
        8);
    preview.addView(line);
    Ui.add(this, preview, 0);
    TextView format = Ui.button(c, "Escolher formato", false, this::chooseShape);
    format.setContentDescription("Escolher formato do medicamento");
    Ui.add(this, format, 12);
    LinearLayout colors = Ui.row(c);
    boolean two = MedicineStyle.twoColors(icon);
    colors.addView(
        colorButton(
            two ? "Cor 1" : "Cor",
            primary,
            value -> {
              primary = value;
              render();
            }),
        new LinearLayout.LayoutParams(0, -2, 1));
    if (two) {
      LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, -2, 1);
      second.leftMargin = dp(10);
      colors.addView(
          colorButton(
              "Cor 2",
              secondary,
              value -> {
                secondary = value;
                render();
              }),
          second);
    }
    Ui.add(this, colors, 10);
  }

  private View colorButton(String label, int color, Consumer<Integer> accept) {
    Context c = getContext();
    LinearLayout button = Ui.row(c);
    button.setPadding(dp(13), dp(12), dp(10), dp(12));
    button.setMinimumHeight(dp(62));
    Ui.touch(button, Ui.LIGHT, 16);
    button.setContentDescription(
        "Escolher " + label.toLowerCase(java.util.Locale.ROOT) + ": " + colorName(color));
    View swatch = new View(c);
    swatch.setBackground(Ui.outline(color, 0xFF718279, 9, c));
    button.addView(swatch, new LinearLayout.LayoutParams(dp(28), dp(28)));
    LinearLayout words = Ui.column(c);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
    lp.leftMargin = dp(10);
    button.addView(words, lp);
    words.addView(Ui.text(c, label, 14, Ui.INK, true));
    Ui.add(words, Ui.text(c, colorName(color), 11, Ui.MUTED, false), 4);
    button.setOnClickListener(v -> chooseColor(label, color, accept));
    return button;
  }

  private static String colorName(int color) {
    for (int i = 0; i < COLORS.length; i++) if (COLORS[i] == color) return COLOR_NAMES[i];
    return "Personalizada";
  }

  private LinearLayout dialogBody(ScrollView scroll) {
    LinearLayout b = Ui.column(getContext());
    b.setPadding(dp(12), dp(8), dp(12), dp(14));
    scroll.addView(b);
    return b;
  }

  private void chooseShape() {
    Context c = getContext();
    ScrollView scroll = new ScrollView(c);
    LinearLayout body = dialogBody(scroll);
    AlertDialog dialog =
        new AlertDialog.Builder(c)
            .setTitle("Formato do medicamento")
            .setView(scroll)
            .setNegativeButton("Voltar", null)
            .create();
    LinearLayout row = null;
    for (int i = 0; i < MedicineStyle.ICONS.size(); i++) {
      if (i % 3 == 0) {
        row = Ui.row(c);
        Ui.add(body, row, 6);
      }
      String value = MedicineStyle.ICONS.get(i);
      boolean selected = value.equals(icon);
      LinearLayout tile = Ui.column(c);
      tile.setGravity(Gravity.CENTER);
      tile.setPadding(dp(5), dp(10), dp(5), dp(10));
      tile.setMinimumHeight(dp(100));
      Ui.touch(tile, selected ? Ui.LIGHT : Ui.SURFACE, 14);
      if (selected) tile.setBackground(Ui.outline(Ui.LIGHT, Ui.TEAL, 14, c));
      tile.setSelected(selected);
      tile.setContentDescription(
          "Formato: " + MedicineStyle.label(value) + (selected ? ", selecionado" : ""));
      tile.addView(Ui.medicine(c, value, primary, secondary, 40));
      TextView name = Ui.text(c, MedicineStyle.label(value), 12, Ui.INK, selected);
      name.setGravity(Gravity.CENTER);
      Ui.add(tile, name, 8);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
      lp.setMargins(dp(3), 0, dp(3), 0);
      row.addView(tile, lp);
      tile.setOnClickListener(
          v -> {
            icon = value;
            render();
            dialog.dismiss();
          });
    }
    dialog.show();
  }

  private void chooseColor(String label, int current, Consumer<Integer> accept) {
    Context c = getContext();
    ScrollView scroll = new ScrollView(c);
    LinearLayout body = dialogBody(scroll);
    AlertDialog dialog =
        new AlertDialog.Builder(c)
            .setTitle("Escolher " + label.toLowerCase(java.util.Locale.ROOT))
            .setView(scroll)
            .setNegativeButton("Voltar", null)
            .setNeutralButton("Outra cor…", (a, b) -> customColor(current, accept))
            .create();
    LinearLayout row = null;
    for (int i = 0; i < COLORS.length; i++) {
      if (i % 4 == 0) {
        row = Ui.row(c);
        Ui.add(body, row, 8);
      }
      int color = COLORS[i];
      LinearLayout tile = Ui.column(c);
      tile.setGravity(Gravity.CENTER);
      tile.setPadding(dp(3), dp(3), dp(3), dp(3));
      tile.setMinimumHeight(dp(74));
      Ui.touch(tile, Ui.SURFACE, 10);
      tile.setContentDescription(COLOR_NAMES[i] + (color == current ? ", selecionada" : ""));
      TextView swatch =
          Ui.text(c, color == current ? "✓" : "", 24, MedicineIcon.onColor(color), true);
      swatch.setGravity(Gravity.CENTER);
      swatch.setBackground(Ui.outline(color, 0xFF718279, 12, c));
      tile.addView(swatch, new LinearLayout.LayoutParams(-1, dp(44)));
      TextView name = Ui.text(c, COLOR_NAMES[i], 10, Ui.INK, false);
      name.setGravity(Gravity.CENTER);
      Ui.add(tile, name, 6);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
      lp.setMargins(dp(3), 0, dp(3), 0);
      row.addView(tile, lp);
      tile.setOnClickListener(
          v -> {
            accept.accept(color);
            dialog.dismiss();
          });
    }
    dialog.show();
  }

  private void customColor(int current, Consumer<Integer> accept) {
    Context c = getContext();
    LinearLayout body = Ui.column(c);
    body.setPadding(dp(22), dp(14), dp(22), dp(14));
    View preview = new View(c);
    body.addView(preview, new LinearLayout.LayoutParams(-1, dp(52)));
    SeekBar[] sliders = new SeekBar[3];
    TextView[] labels = new TextView[3];
    String[] names = {"Vermelho", "Verde", "Azul"};
    for (int i = 0; i < 3; i++) {
      labels[i] = Ui.text(c, "", 14, Ui.INK, true);
      Ui.add(body, labels[i], 18);
      sliders[i] = new SeekBar(c);
      sliders[i].setMax(255);
      sliders[i].setProgress((current >> (16 - 8 * i)) & 255);
      sliders[i].setContentDescription(names[i]);
      sliders[i].setMinimumHeight(dp(48));
      Ui.add(body, sliders[i], 5);
    }
    java.util.function.IntSupplier chosen =
        () ->
            0xFF000000
                | (sliders[0].getProgress() << 16)
                | (sliders[1].getProgress() << 8)
                | sliders[2].getProgress();
    Runnable refresh =
        () -> {
          preview.setBackground(Ui.outline(chosen.getAsInt(), 0xFF718279, 14, c));
          for (int i = 0; i < 3; i++) labels[i].setText(names[i] + ": " + sliders[i].getProgress());
        };
    for (SeekBar slider : sliders)
      slider.setOnSeekBarChangeListener(
          new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
              refresh.run();
            }

            public void onStartTrackingTouch(SeekBar s) {}

            public void onStopTrackingTouch(SeekBar s) {}
          });
    refresh.run();
    ScrollView scroll = new ScrollView(c);
    scroll.addView(body);
    new AlertDialog.Builder(c)
        .setTitle("Ajustar cor")
        .setView(scroll)
        .setNegativeButton("Cancelar", null)
        .setPositiveButton("Usar esta cor", (a, b) -> accept.accept(chosen.getAsInt()))
        .show();
  }

  private int dp(int n) {
    return Ui.dp(getContext(), n);
  }
}
