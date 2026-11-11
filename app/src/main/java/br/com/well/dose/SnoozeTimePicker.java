package br.com.well.dose;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TimePicker;
import java.time.*;
import java.time.format.DateTimeFormatter;

/** A clock-time choice for one pending dose, shared by the agenda and the alarm screen. */
final class SnoozeTimePicker {
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM");

  static AlertDialog show(Activity activity, Dose dose, Runnable onSaved) {
    long now = System.currentTimeMillis();
    long suggested = dose.snoozed && dose.nextAlert > now && dose.nextAlert < dose.expires
        ? dose.nextAlert
        : Math.min(Math.max(now, dose.scheduled) + Prefs.snooze(activity) * 60_000L, dose.expires - 1);
    LocalTime initial = Instant.ofEpochMilli(suggested).atZone(ZoneId.systemDefault()).toLocalTime();

    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
    Context context = builder.getContext();
    TimePicker picker = new TimePicker(context);
    picker.setIs24HourView(true);
    picker.setHour(initial.getHour());
    picker.setMinute(initial.getMinute());
    TextView feedback = Ui.text(context, "", 15, Ui.INK, false);
    feedback.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
    int padding = Ui.dp(context, 24);
    feedback.setPadding(padding, 0, padding, Ui.dp(context, 12));

    LinearLayout contents = Ui.column(context);
    contents.addView(picker);
    contents.addView(feedback);
    ScrollView scroll = new ScrollView(context);
    scroll.addView(contents);
    AlertDialog dialog = builder
        .setTitle("Adiar até um horário")
        .setView(scroll)
        .setNegativeButton("Cancelar", null)
        .setPositiveButton("Adiar lembrete", null)
        .create();
    dialog.setOnShowListener(ignored -> {
      Button confirm = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
      Runnable updatePreview = () -> {
        long until = chosenTime(picker, dose);
        confirm.setEnabled(until > 0);
        feedback.setText(until > 0
            ? "Próximo aviso " + label(until) + "."
            : "Escolha um horário futuro, após a dose e antes de " + label(dose.expires) + ".");
      };
      picker.setOnTimeChangedListener((view, hour, minute) -> updatePreview.run());
      confirm.setOnClickListener(view -> {
        picker.clearFocus();
        long until = chosenTime(picker, dose);
        if (until == 0) {
          updatePreview.run();
          return;
        }
        try {
          if (!AlarmEngine.snoozeUntil(activity, dose.id, until)) {
            Ui.toast(activity, "Este lembrete não está mais disponível.");
            dialog.dismiss();
            return;
          }
        } catch (IllegalArgumentException e) {
          feedback.setText(e.getMessage());
          return;
        }
        Ui.toast(activity, "Lembrete adiado para " + label(until) + ".");
        dialog.dismiss();
        onSaved.run();
      });
      updatePreview.run();
    });
    dialog.show();
    return dialog;
  }

  private static long chosenTime(TimePicker picker, Dose dose) {
    return ScheduleRules.nextSnoozeTime(
        Math.max(System.currentTimeMillis(), dose.scheduled),
        dose.expires,
        LocalTime.of(picker.getHour(), picker.getMinute()),
        ZoneId.systemDefault());
  }

  static String label(long at) {
    ZonedDateTime target = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault());
    LocalDate day = target.toLocalDate();
    LocalDate today = LocalDate.now();
    String date = day.equals(today) ? "hoje"
        : day.equals(today.plusDays(1)) ? "amanhã" : "em " + day.format(DATE);
    return date + " às " + target.format(ScheduleRules.CLOCK);
  }
}
