package br.com.well.dose;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Build;
import android.view.*;
import android.widget.*;

public final class Ui {
  public static final int WHITE = 0xFFFFFFFF;
  public static int BG = 0xFFFAFBF8,
      SURFACE = 0xFFFFFFFF,
      INK = 0xFF19372F,
      MUTED = 0xFF64776F,
      TEAL = 0xFF137C70,
      LIGHT = 0xFFE8F4ED,
      LINE = 0xFFE5EBE4,
      LIME = 0xFFDAEBA8,
      RED = 0xFFB95245,
      AMBER = 0xFF9B6519,
      WARNING = 0xFFFFF3DB,
      SELECTED = 0xFF19372F,
      ON_SELECTED = 0xFFFFFFFF,
      ON_PRIMARY = 0xFFFFFFFF,
      HERO = 0xFF137C70;

  public static boolean isDark(Context c) {
    String mode = Prefs.get(c).getString("theme", "system");
    return "dark".equals(mode)
        || (!"light".equals(mode)
            && (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES);
  }

  public static void prepare(Activity a) {
    boolean dark = isDark(a);
    a.setTheme(dark ? R.style.AppThemeDark : R.style.AppThemeLight);
    palette(dark);
  }

  private static void palette(boolean dark) {
    BG = dark ? 0xFF101916 : 0xFFFAFBF8;
    SURFACE = dark ? 0xFF192620 : WHITE;
    INK = dark ? 0xFFE7F2EB : 0xFF19372F;
    MUTED = dark ? 0xFFA8BDB2 : 0xFF64776F;
    TEAL = dark ? 0xFF8ED5C3 : 0xFF137C70;
    LIGHT = dark ? 0xFF203D33 : 0xFFE8F4ED;
    LINE = dark ? 0xFF344B40 : 0xFFE5EBE4;
    RED = dark ? 0xFFFFB4A8 : 0xFFB95245;
    AMBER = dark ? 0xFFF0C47D : 0xFF9B6519;
    WARNING = dark ? 0xFF3C3020 : 0xFFFFF3DB;
    SELECTED = dark ? 0xFF8ED5C3 : 0xFF19372F;
    ON_SELECTED = dark ? 0xFF102D24 : WHITE;
    ON_PRIMARY = dark ? 0xFF102D24 : WHITE;
    HERO = dark ? 0xFF174B3F : 0xFF137C70;
  }

  public static int dp(Context c, float value) {
    return Math.round(value * c.getResources().getDisplayMetrics().density);
  }

  public static LinearLayout column(Context c) {
    LinearLayout v = new LinearLayout(c);
    v.setOrientation(LinearLayout.VERTICAL);
    return v;
  }

  public static LinearLayout row(Context c) {
    LinearLayout v = new LinearLayout(c);
    v.setOrientation(LinearLayout.HORIZONTAL);
    v.setGravity(Gravity.CENTER_VERTICAL);
    return v;
  }

  public static TextView text(Context c, String value, int size, int color, boolean bold) {
    TextView v = new TextView(c);
    v.setText(value);
    v.setTextSize(size);
    v.setTextColor(color);
    v.setFontFeatureSettings("kern");
    v.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
    v.setIncludeFontPadding(false);
    v.setLineSpacing(dp(c, 3), 1);
    return v;
  }

  public static GradientDrawable bg(int color, int radius, Context c) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(c, radius));
    return d;
  }

  public static GradientDrawable outline(int color, int border, int radius, Context c) {
    GradientDrawable d = bg(color, radius, c);
    d.setStroke(dp(c, 1), border);
    return d;
  }

  public static void touch(View v, int color, int radius) {
    v.setBackground(
        new RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x22137C70),
            bg(color, radius, v.getContext()),
            null));
    v.setClickable(true);
    v.setFocusable(true);
  }

  public static TextView button(Context c, String label, boolean primary, Runnable action) {
    TextView v = text(c, label, 15, primary ? ON_PRIMARY : TEAL, true);
    v.setGravity(Gravity.CENTER);
    v.setMinHeight(dp(c, 50));
    v.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
    touch(v, primary ? TEAL : LIGHT, 16);
    v.setOnClickListener(w -> action.run());
    return v;
  }

  public static void add(LinearLayout parent, View child, int top) {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.topMargin = dp(parent.getContext(), top);
    parent.addView(child, lp);
  }

  public static LinearLayout card(Context c) {
    LinearLayout box = column(c);
    box.setPadding(dp(c, 18), dp(c, 18), dp(c, 18), dp(c, 18));
    box.setBackground(outline(SURFACE, LINE, 22, c));
    return box;
  }

  public static View gap(Context c, int h) {
    View v = new View(c);
    v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, h)));
    return v;
  }

  public static LinearLayout root(Activity a) {
    palette(isDark(a));
    if (Build.VERSION.SDK_INT >= 30) a.getWindow().setDecorFitsSystemWindows(false);
    a.getWindow().setStatusBarColor(BG);
    a.getWindow().setNavigationBarColor(BG);
    a.getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            isDark(a)
                ? 0
                : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    LinearLayout root = column(a);
    root.setBackgroundColor(BG);
    root.setOnApplyWindowInsetsListener(
        (view, insets) -> {
          if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets bars =
                insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
          } else
            view.setPadding(
                insets.getSystemWindowInsetLeft(),
                insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(),
                insets.getSystemWindowInsetBottom());
          return insets;
        });
    a.setContentView(root);
    if (Build.VERSION.SDK_INT >= 30)
      root.post(
          () -> {
            WindowInsetsController controller = a.getWindow().getInsetsController();
            if (controller != null) {
              int light =
                  WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                      | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
              controller.setSystemBarsAppearance(isDark(a) ? 0 : light, light);
            }
          });
    return root;
  }

  public static LinearLayout scroll(LinearLayout root) {
    ScrollView scroll = new ScrollView(root.getContext());
    scroll.setFillViewport(true);
    scroll.setClipToPadding(false);
    scroll.setVerticalScrollBarEnabled(false);
    LinearLayout body = column(root.getContext());
    body.setPadding(
        dp(root.getContext(), 22),
        dp(root.getContext(), 12),
        dp(root.getContext(), 22),
        dp(root.getContext(), 26));
    scroll.addView(body);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    return body;
  }

  public static View icon(Context c, String kind, int color, int size) {
    Icon v = new Icon(c, kind, color);
    v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, size), dp(c, size)));
    v.setContentDescription(kind);
    return v;
  }

  public static MedicineIcon medicine(
      Context c, String icon, int primary, int secondary, int size) {
    MedicineIcon view = new MedicineIcon(c, icon, primary, secondary);
    view.setLayoutParams(new LinearLayout.LayoutParams(dp(c, size), dp(c, size)));
    return view;
  }

  public static TextView chip(Context c, String value, int background, int color) {
    TextView t = text(c, value, 12, color, true);
    t.setPadding(dp(c, 10), dp(c, 6), dp(c, 10), dp(c, 6));
    t.setBackground(bg(background, 30, c));
    return t;
  }

  public static void title(LinearLayout body, String title, String subtitle) {
    add(body, text(body.getContext(), title, 29, INK, true), 8);
    if (!subtitle.isEmpty()) add(body, text(body.getContext(), subtitle, 14, MUTED, false), 8);
  }

  public static void toast(Context c, String s) {
    Toast.makeText(c, s, Toast.LENGTH_LONG).show();
  }

  public static final class Icon extends View {
    final String kind;
    final int color;
    final Paint p = new Paint(3);

    Icon(Context c, String kind, int color) {
      super(c);
      this.kind = kind;
      this.color = color;
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      canvas.save();
      canvas.scale(getWidth() / 24f, getHeight() / 24f);
      p.setColor(color);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(1.8f);
      p.setStrokeCap(Paint.Cap.ROUND);
      p.setStrokeJoin(Paint.Join.ROUND);
      Path path = new Path();
      switch (kind) {
        case "pill":
          canvas.save();
          canvas.rotate(-43, 12, 12);
          canvas.drawRoundRect(7, 2.5f, 17, 21.5f, 5, 5, p);
          canvas.drawLine(7, 12, 17, 12, p);
          canvas.restore();
          break;
        case "plus":
          canvas.drawLine(12, 5, 12, 19, p);
          canvas.drawLine(5, 12, 19, 12, p);
          break;
        case "check":
          path.moveTo(5, 12);
          path.lineTo(10, 17);
          path.lineTo(20, 6);
          canvas.drawPath(path, p);
          break;
        case "clock":
          canvas.drawCircle(12, 12, 9, p);
          canvas.drawLine(12, 7, 12, 12, p);
          canvas.drawLine(12, 12, 16, 14, p);
          break;
        case "calendar":
          canvas.drawRoundRect(3, 5, 21, 21, 3, 3, p);
          canvas.drawLine(3, 10, 21, 10, p);
          canvas.drawLine(8, 2.5f, 8, 7, p);
          canvas.drawLine(16, 2.5f, 16, 7, p);
          canvas.drawLine(8, 15, 10, 15, p);
          canvas.drawLine(14, 15, 16, 15, p);
          break;
        case "history":
          canvas.drawArc(4, 4, 21, 21, -160, 315, false, p);
          path.moveTo(3, 3);
          path.lineTo(3, 9);
          path.lineTo(9, 9);
          canvas.drawPath(path, p);
          canvas.drawLine(12, 8, 12, 13, p);
          canvas.drawLine(12, 13, 16, 15, p);
          break;
        case "bell":
          path.moveTo(5, 16);
          path.lineTo(7, 13);
          path.lineTo(7, 9);
          path.cubicTo(7, 2, 17, 2, 17, 9);
          path.lineTo(17, 13);
          path.lineTo(19, 16);
          path.close();
          canvas.drawPath(path, p);
          canvas.drawArc(10, 18, 14, 22, 0, 180, false, p);
          canvas.drawLine(12, 2, 12, 3, p);
          break;
        case "shield":
          path.moveTo(12, 2);
          path.lineTo(21, 6);
          path.lineTo(20, 14);
          path.quadTo(18, 20, 12, 23);
          path.quadTo(6, 20, 4, 14);
          path.lineTo(3, 6);
          path.close();
          canvas.drawPath(path, p);
          path.reset();
          path.moveTo(8, 12);
          path.lineTo(11, 15);
          path.lineTo(16, 9);
          canvas.drawPath(path, p);
          break;
        case "back":
          path.moveTo(14, 5);
          path.lineTo(7, 12);
          path.lineTo(14, 19);
          canvas.drawPath(path, p);
          break;
        case "arrow":
          canvas.drawLine(4, 12, 20, 12, p);
          path.moveTo(14, 6);
          path.lineTo(20, 12);
          path.lineTo(14, 18);
          canvas.drawPath(path, p);
          break;
        case "dots":
          p.setStyle(Paint.Style.FILL);
          canvas.drawCircle(12, 5, 1.5f, p);
          canvas.drawCircle(12, 12, 1.5f, p);
          canvas.drawCircle(12, 19, 1.5f, p);
          break;
        case "volume":
          path.moveTo(3, 9);
          path.lineTo(7, 9);
          path.lineTo(12, 5);
          path.lineTo(12, 19);
          path.lineTo(7, 15);
          path.lineTo(3, 15);
          path.close();
          canvas.drawPath(path, p);
          canvas.drawArc(11, 5, 22, 19, -65, 130, false, p);
          canvas.drawArc(13, 9, 18, 15, -65, 130, false, p);
          break;
        default:
          canvas.drawCircle(12, 12, 9, p);
          canvas.drawLine(12, 7, 12, 13, p);
          canvas.drawPoint(12, 17, p);
      }
      canvas.restore();
    }
  }
}
