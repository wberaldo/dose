package br.com.well.dose;

import android.app.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

public final class BackupActivity extends Activity {
  private boolean working;

  @Override
  public void onCreate(Bundle state) {
    Ui.prepare(this);
    super.onCreate(state);
  }

  @Override
  public void onResume() {
    super.onResume();
    render();
  }

  private void render() {
    LinearLayout root = Ui.root(this);
    LinearLayout body = Ui.scroll(root);
    Ui.add(body, Ui.button(this, "‹ Voltar", false, this::finish), 8);
    Ui.title(body, "Backup e Google Drive", "Seu cuidado pode acompanhar você em outro celular.");
    LinearLayout account = Ui.card(this);
    account.addView(Ui.text(this, "Backup automático da conta", 20, Ui.INK, true));
    boolean enabled = CloudBackup.enabled(this);
    Ui.add(
        account, Ui.chip(this, enabled ? "Ativado no Dose" : "Desativado", Ui.LIGHT, Ui.TEAL), 14);
    Ui.add(
        account,
        Ui.text(
            this,
            "O Android guarda os dados na conta de backup escolhida no celular. Nas configurações,"
                + " selecione sua conta Google e ative o backup do aparelho.",
            14,
            Ui.MUTED,
            false),
        14);
    Ui.add(
        account,
        Ui.text(
            this,
            "A recuperação automática acontece ao reinstalar o Dose ou configurar outro aparelho"
                + " com essa conta. O envio depende das condições e do horário definidos pelo"
                + " Android.",
            14,
            Ui.MUTED,
            false),
        10);
    if (Build.VERSION.SDK_INT >= 28)
      Ui.add(
          account,
          Ui.button(
              this,
              enabled ? "Desativar no Dose" : "Ativar no Dose",
              !enabled,
              () -> {
                if (enabled) {
                  CloudBackup.setEnabled(this, false);
                  render();
                } else
                  new AlertDialog.Builder(this)
                      .setTitle("Ativar backup da conta?")
                      .setMessage(
                          "Inclui medicamentos, histórico, estoque, relatórios importados, cores e"
                              + " cancelamentos por dia. A cópia na nuvem exige a proteção por"
                              + " criptografia do Android. Confira a conta de destino nas"
                              + " configurações do celular.")
                      .setNegativeButton("Voltar", null)
                      .setPositiveButton(
                          "Ativar",
                          (d, w) -> {
                            CloudBackup.setEnabled(this, true);
                            render();
                          })
                      .show();
              }),
          18);
    else
      Ui.add(
          account,
          Ui.text(
              this,
              "O automático exige Android 9 ou mais recente. Use o arquivo manual neste aparelho.",
              13,
              Ui.MUTED,
              false),
          18);
    Ui.add(
        account,
        Ui.button(this, "Configurar conta e backup no Android", false, this::accountSettings),
        10);
    long prepared = CloudBackup.prefs(this).getLong("prepared_at", 0);
    Ui.add(
        account,
        Ui.text(
            this,
            prepared > 0
                ? "Dados preparados para o Android: " + stamp(prepared)
                : "O Dose ainda não recebeu uma execução de backup do Android.",
            12,
            Ui.MUTED,
            false),
        14);
    Ui.add(
        account,
        Ui.text(
            this,
            "A confirmação do envio para a nuvem aparece nas configurações de backup do Android.",
            12,
            Ui.MUTED,
            false),
        8);
    long restored = CloudBackup.prefs(this).getLong("restored_at", 0);
    if (restored > 0)
      Ui.add(
          account,
          Ui.text(this, "Recuperado da conta: " + stamp(restored), 12, Ui.TEAL, false),
          10);
    String error = CloudBackup.prefs(this).getString("error", "");
    if (!error.isEmpty()) Ui.add(account, Ui.text(this, error, 13, Ui.AMBER, false), 12);
    Ui.add(body, account, 24);
    LinearLayout manual = Ui.card(this);
    manual.addView(Ui.text(this, "Arquivo no Drive ou no celular", 20, Ui.INK, true));
    Ui.add(
        manual,
        Ui.text(
            this,
            "Para guardar uma cópia agora, escolha Google Drive no seletor de arquivos e confira a"
                + " conta e a pasta. O Drive precisa estar instalado e conectado. Você também pode"
                + " escolher o celular.",
            14,
            Ui.MUTED,
            false),
        14);
    TextView save =
        Ui.button(
            this,
            working ? "Aguarde…" : "Salvar arquivo de backup",
            true,
            () -> choose(BackupFiles.createIntent(), 101));
    save.setEnabled(!working);
    Ui.add(manual, save, 18);
    TextView restore =
        Ui.button(
            this,
            "Restaurar arquivo de backup",
            false,
            () ->
                new AlertDialog.Builder(this)
                    .setTitle("Restaurar um backup?")
                    .setMessage(
                        "Substitui seus medicamentos e seu histórico atuais. Salve uma cópia antes"
                            + " se quiser guardar os dados deste celular.")
                    .setNegativeButton("Voltar", null)
                    .setPositiveButton(
                        "Escolher arquivo", (d, w) -> choose(BackupFiles.openIntent(), 102))
                    .show());
    restore.setEnabled(!working);
    Ui.add(manual, restore, 10);
    long saved = CloudBackup.prefs(this).getLong("manual_at", 0);
    if (saved > 0)
      Ui.add(
          manual,
          Ui.text(
              this,
              "Último arquivo salvo: "
                  + stamp(saved)
                  + "\n"
                  + CloudBackup.prefs(this).getString("manual_name", ""),
              12,
              Ui.MUTED,
              false),
          14);
    Ui.add(body, manual, 18);
  }

  private void accountSettings() {
    startActivity(new Intent(Settings.ACTION_SETTINGS));
    Ui.toast(this, "Procure Backup do Google e confira a conta.");
  }

  private void choose(Intent intent, int code) {
    try {
      startActivityForResult(intent, code);
    } catch (ActivityNotFoundException error) {
      Ui.toast(this, "O Android não encontrou um seletor de arquivos.");
    }
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (result != RESULT_OK
        || data == null
        || data.getData() == null
        || (request != 101 && request != 102)) return;
    working = true;
    render();
    BackupFiles.transfer(
        this,
        data.getData(),
        request == 102,
        () -> {
          working = false;
          render();
        });
  }

  private String stamp(long at) {
    return Instant.ofEpochMilli(at)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm"));
  }
}
