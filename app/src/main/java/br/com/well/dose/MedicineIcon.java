package br.com.well.dose;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Resolution-independent medicine silhouettes, shared with notification artwork. */
public final class MedicineIcon extends View {
  private final String icon;
  private final int primary, secondary;

  public MedicineIcon(Context c, String icon, int primary, int secondary) {
    super(c);
    this.icon = MedicineStyle.valid(icon) ? icon : "capsule";
    this.primary = primary;
    this.secondary = secondary;
    setContentDescription(MedicineStyle.label(this.icon));
  }

  public String style() {
    return icon;
  }

  public int primaryColor() {
    return primary;
  }

  public int secondaryColor() {
    return secondary;
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    paint(canvas, icon, primary, secondary, getWidth(), getHeight());
  }

  public static Bitmap bitmap(String icon, int primary, int secondary, int size) {
    Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    paint(new Canvas(bitmap), icon, primary, secondary, size, size);
    return bitmap;
  }

  private static void paint(Canvas canvas, String icon, int primary, int secondary, int w, int h) {
    canvas.save();
    float size = Math.min(w, h);
    canvas.translate((w - size) / 2, (h - size) / 2);
    canvas.scale(size / 64f, size / 64f);
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    p.setStrokeCap(Paint.Cap.ROUND);
    p.setStrokeJoin(Paint.Join.ROUND);
    Path shape = new Path();
    boolean two = MedicineStyle.twoColors(icon);
    if (two) {
      canvas.rotate(-35, 32, 32);
      float top = "capsule_short".equals(icon) ? 11 : 5;
      shape.addRoundRect(19, top, 45, 64 - top, 13, 13, Path.Direction.CW);
    } else
      switch (icon) {
        case "tablet_scored":
        case "tablet_cross":
        case "tablet_round":
          shape.addCircle(32, 32, 23, Path.Direction.CW);
          break;
        case "tablet_oval":
          canvas.rotate(-30, 32, 32);
          shape.addOval(6, 15, 58, 49, Path.Direction.CW);
          break;
        case "tablet_oblong":
          canvas.rotate(-30, 32, 32);
          shape.addRoundRect(5, 18, 59, 46, 10, 10, Path.Direction.CW);
          break;
        case "tablet_triangle":
          shape.moveTo(32, 7);
          shape.lineTo(58, 53);
          shape.lineTo(6, 53);
          shape.close();
          break;
        case "tablet_square":
          shape.addRoundRect(11, 11, 53, 53, 9, 9, Path.Direction.CW);
          break;
        case "tablet_diamond":
          shape.moveTo(32, 5);
          shape.lineTo(58, 32);
          shape.lineTo(32, 59);
          shape.lineTo(6, 32);
          shape.close();
          break;
        case "drops":
          shape.moveTo(32, 5);
          shape.cubicTo(26, 17, 12, 29, 12, 39);
          shape.cubicTo(12, 65, 52, 65, 52, 39);
          shape.cubicTo(52, 29, 38, 17, 32, 5);
          shape.close();
          break;
        case "bottle":
          shape.moveTo(23, 15);
          shape.lineTo(41, 15);
          shape.lineTo(41, 22);
          shape.cubicTo(41, 26, 51, 26, 51, 33);
          shape.lineTo(51, 54);
          shape.quadTo(51, 59, 46, 59);
          shape.lineTo(18, 59);
          shape.quadTo(13, 59, 13, 54);
          shape.lineTo(13, 33);
          shape.cubicTo(13, 26, 23, 26, 23, 22);
          shape.close();
          break;
        default:
          shape.addRoundRect(19, 5, 45, 59, 13, 13, Path.Direction.CW);
      }
    p.setColor(primary);
    p.setStyle(Paint.Style.FILL);
    if ("tablet_triangle".equals(icon) || "tablet_diamond".equals(icon))
      p.setPathEffect(new CornerPathEffect(5));
    canvas.drawPath(shape, p);
    if (two) {
      canvas.save();
      canvas.clipPath(shape);
      p.setColor(secondary);
      canvas.drawRect(0, 32, 64, 64, p);
      canvas.restore();
    }
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(2);
    p.setColor(0xFF718279);
    canvas.drawPath(shape, p);
    p.setPathEffect(null);
    p.setStrokeWidth(1.8f);
    p.setColor(onColor(primary));
    if (two) {
      // A neutral seam stays visible even when one half is white or very dark.
      p.setColor(0xFF718279);
      canvas.drawLine(19.5f, 32, 44.5f, 32, p);
    } else if ("tablet_scored".equals(icon) || "tablet_cross".equals(icon)) {
      canvas.drawLine(18, 32, 46, 32, p);
      if ("tablet_cross".equals(icon)) canvas.drawLine(32, 18, 32, 46, p);
    } else if ("tablet_oblong".equals(icon)) canvas.drawLine(32, 23, 32, 41, p);
    else if ("bottle".equals(icon)) {
      p.setStyle(Paint.Style.FILL);
      p.setColor(primary);
      canvas.drawRoundRect(21, 5, 43, 15, 3, 3, p);
      p.setStyle(Paint.Style.STROKE);
      p.setColor(0xFF718279);
      canvas.drawRoundRect(21, 5, 43, 15, 3, 3, p);
      p.setColor(onColor(primary));
      canvas.drawRoundRect(21, 34, 43, 48, 3, 3, p);
    }
    canvas.restore();
  }

  public static int onColor(int color) {
    double luminance = 0;
    double[] weight = {.2126, .7152, .0722};
    int[] shifts = {16, 8, 0};
    for (int i = 0; i < 3; i++) {
      double v = ((color >> shifts[i]) & 255) / 255.0;
      luminance += weight[i] * (v <= .04045 ? v / 12.92 : Math.pow((v + .055) / 1.055, 2.4));
    }
    return luminance > .179 ? 0xFF20332C : 0xFFFFFFFF;
  }
}
