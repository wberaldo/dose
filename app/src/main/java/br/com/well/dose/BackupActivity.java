package br.com.well.dose;

import android.app.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

public final class BackupActivity extends Activity {
  private boolean wasBusy;
  private boolean manualWorking;
  private final Handler watcher = new Handler(Looper.getMainLooper());
  private final Runnable watch =
      new Runnable() {
        public void run() {
          if (wasBusy != (DriveBackup.busy() || manualWorking)) render();
          watcher.postDelayed(this, 500);
        }
      };

  @Override
  public void onCreate(Bundle state) {
    Ui.prepare(this);
    super.onCreate(state);
  }

  @Override
  public void onResume() {
    super.onResume();
    DriveBackup.ensureScheduled(this);
    render();
    watcher.postDelayed(watch, 500);
  }

  @Override
  public void onPause() {
    watcher.removeCallbacksAndMessages(null);
    super.onPause();
  }

  private void render() {
    wasBusy = DriveBackup.busy() || manualWorking;
    LinearLayout root = Ui.root(this), body = Ui.scroll(root);
    Ui.add(body, Ui.button(this, "‹ Voltar", false, this::finish), 8);
    Ui.title(body, "Backup no Google Drive", "Configure uma vez. Depois, salve com um toque.");
    LinearLayout target = Ui.card(this);
    target.addView(Ui.text(this, "Seu backup", 21, Ui.INK, true));
    boolean configured = DriveBackup.destination(this) != null;
    boolean access = DriveBackup.hasAccess(this, DriveBackup.destination(this));
    boolean protectedFile = DriveBackup.prefs(this).getBoolean("needs_restore", false);
    Ui.add(
        target,
        Ui.chip(
            this,
            configured ? access ? "Destino salvo" : "Reconectar destino" : "Ainda não configurado",
            Ui.LIGHT,
            Ui.TEAL),
        12);
    if (!configured) {
      Ui.add(
          target,
          Ui.text(
              this,
              "Escolha Google Drive, sua conta e o arquivo de backup uma única vez. Depois, o Dose"
                  + " atualizará esse mesmo arquivo sem abrir o seletor novamente.",
              14,
              Ui.MUTED,
              false),
          14);
      button(target, "Configurar Google Drive", true, this::configure, true);
      button(
          target, "Já tenho um backup", false, () -> choose(DriveBackup.choose(true), 202), true);
    } else {
      Ui.add(
          target,
          Ui.text(
              this,
              DriveBackup.prefs(this).getString("provider", "Destino escolhido")
                  + "\n"
                  + DriveBackup.prefs(this).getString("name", "Arquivo de backup"),
              15,
              Ui.INK,
              true),
          14);
      if (protectedFile) {
        Ui.add(
            target,
            Ui.text(
                this,
                "Este arquivo já contém um backup. Restaure a cópia ou confirme que deseja"
                    + " substituí-la pelos dados deste celular.",
                14,
                Ui.MUTED,
                false),
            12);
        button(target, "Restaurar este backup", true, () -> restoreDialog(false), access);
        button(
            target, "Substituir pelos dados deste celular", false, this::replaceExisting, access);
      } else {
        button(
            target,
            wasBusy ? "Fazendo backup…" : "Fazer backup agora",
            true,
            () -> perform(false, token -> DriveBackup.save(this, token), null),
            access);
        button(target, "Restaurar este backup", false, () -> restoreDialog(false), access);
      }
      long saved = DriveBackup.prefs(this).getLong("saved_at", 0);
      Ui.add(
          target,
          Ui.text(
              this,
              saved > 0
                  ? "Última gravação: " + stamp(saved)
                  : "Este arquivo ainda não foi atualizado pelo Dose neste celular.",
              12,
              Ui.MUTED,
              false),
          14);
      long restored = DriveBackup.prefs(this).getLong("restored_at", 0);
      if (restored > 0)
        Ui.add(target, Ui.text(this, "Restaurado: " + stamp(restored), 12, Ui.TEAL, false), 8);
      Ui.add(
          target,
          Ui.text(
              this,
              "O Drive sincroniza o arquivo com sua conta. A gravação no arquivo é conferida pelo"
                  + " Dose; o envio à nuvem depende do Drive e da conexão.",
              12,
              Ui.MUTED,
              false),
          10);
      button(target, "Trocar destino", false, this::configure, true);
      button(target, "Desconectar destino", false, this::disconnect, true);
    }
    String error = DriveBackup.prefs(this).getString("error", "");
    if (!error.isEmpty()) {
      Ui.add(target, Ui.text(this, error, 13, Ui.AMBER, false), 12);
      if (DriveBackup.hasLocalCopy(this))
        button(target, "Recuperar última cópia local", false, () -> restoreDialog(true), true);
    }
    Ui.add(body, target, 22);
    LinearLayout daily = Ui.card(this);
    daily.addView(Ui.text(this, "Backup automático diário", 20, Ui.INK, true));
    boolean automatic = DriveBackup.automatic(this);
    Ui.add(daily, Ui.chip(this, automatic ? "Ativado" : "Desativado", Ui.LIGHT, Ui.TEAL), 12);
    Ui.add(
        daily,
        Ui.text(
            this,
            "Atualiza o destino salvo aproximadamente uma vez por dia, com conexão e bateria"
                + " suficiente. O Android pode adiar a execução para economizar bateria.",
            14,
            Ui.MUTED,
            false),
        12);
    if (!configured || !access || protectedFile)
      Ui.add(
          daily,
          Ui.text(
              this,
              "Configure o destino e escolha os dados que deseja manter para ativar.",
              13,
              Ui.MUTED,
              false),
          10);
    button(
        daily,
        automatic ? "Desativar backup diário" : "Ativar backup diário",
        !automatic,
        () -> {
          try {
            DriveBackup.setAutomatic(this, !automatic);
            Ui.toast(
                this,
                automatic
                    ? "Backup diário desativado."
                    : "Backup diário ativado. Você também pode salvar agora.");
          } catch (RuntimeException failure) {
            Ui.toast(this, "Confira o destino e tente novamente.");
          }
          render();
        },
        configured && access && !protectedFile);
    Ui.add(body, daily, 18);
    LinearLayout files = Ui.card(this);
    files.addView(Ui.text(this, "Outros arquivos", 20, Ui.INK, true));
    Ui.add(
        files,
        Ui.text(
            this,
            "Medicamentos, histórico, estoque, relatórios, cores, preferências e cancelamentos por"
                + " dia acompanham o backup.",
            14,
            Ui.MUTED,
            false),
        12);
    button(
        files, "Exportar outra cópia", false, () -> choose(BackupFiles.createIntent(), 101), true);
    button(files, "Restaurar de outro arquivo", false, this::otherRestore, true);
    Ui.add(body, files, 18);
    LinearLayout system = Ui.card(this);
    system.addView(Ui.text(this, "Cópia do Android • opcional", 18, Ui.INK, true));
    Ui.add(
        system,
        Ui.text(
            this,
            "Esta é a opção da versão anterior. Segue a conta e os horários do sistema e funciona"
                + " de forma independente do arquivo escolhido acima.",
            13,
            Ui.MUTED,
            false),
        12);
    boolean account = CloudBackup.enabled(this);
    if (Build.VERSION.SDK_INT >= 28)
      button(
          system,
          account ? "Desativar cópia do Android" : "Ativar cópia do Android",
          false,
          () -> {
            if (account) {
              CloudBackup.setEnabled(this, false);
              render();
            } else
              new AlertDialog.Builder(this)
                  .setTitle("Ativar cópia do Android?")
                  .setMessage(
                      "Inclui os dados do Dose no backup da conta configurada no Android, com"
                          + " proteção por criptografia. O sistema decide quando enviar e"
                          + " restaurar.")
                  .setNegativeButton("Voltar", null)
                  .setPositiveButton(
                      "Ativar",
                      (d, w) -> {
                        CloudBackup.setEnabled(this, true);
                        render();
                      })
                  .show();
          },
          true);
    button(
        system,
        "Configurações de backup do Android",
        false,
        () -> {
          startActivity(new Intent(Settings.ACTION_SETTINGS));
          Ui.toast(this, "Procure Backup do Google e confira a conta.");
        },
        true);
    Ui.add(body, system, 18);
  }

  private void button(
      LinearLayout box, String label, boolean primary, Runnable action, boolean enabled) {
    TextView view = Ui.button(this, label, primary, action);
    view.setEnabled(enabled && !wasBusy);
    Ui.add(box, view, 12);
  }

  private void configure() {
    new AlertDialog.Builder(this)
        .setTitle("Escolher o destino no Drive")
        .setMessage(
            "Na próxima tela, escolha Google Drive, confira a conta e salve o arquivo. O Dose"
                + " guardará acesso somente a esse arquivo para os próximos backups. Inclui"
                + " medicamentos, histórico, estoque, relatórios e preferências. Se o Drive não"
                + " aparecer, abra o aplicativo Drive e conecte sua conta primeiro.")
        .setNegativeButton("Voltar", null)
        .setPositiveButton("Escolher no Drive", (d, w) -> choose(DriveBackup.choose(false), 201))
        .show();
  }

  private void choose(Intent intent, int request) {
    try {
      startActivityForResult(intent, request);
    } catch (ActivityNotFoundException failure) {
      Ui.toast(this, "O Android não encontrou um seletor de arquivos.");
    }
  }

  private void replaceExisting() {
    new AlertDialog.Builder(this)
        .setTitle("Substituir o backup existente?")
        .setMessage(
            "O arquivo escolhido será atualizado com os medicamentos e o histórico deste celular."
                + " Para recuperar o que está no arquivo, use Restaurar este backup.")
        .setNegativeButton("Voltar", null)
        .setPositiveButton(
            "Substituir",
            (d, w) ->
                perform(
                    false,
                    token -> {
                      DriveBackup.useCurrentData(this);
                      return DriveBackup.save(this, token);
                    },
                    null))
        .show();
  }

  private void restoreDialog(boolean local) {
    new AlertDialog.Builder(this)
        .setTitle(local ? "Recuperar a cópia local?" : "Restaurar este backup?")
        .setMessage(
            "Substitui os medicamentos, o histórico, o estoque e as preferências atuais pelos dados"
                + " da cópia escolhida. Salve outra cópia antes, se quiser guardar os dados deste"
                + " celular.")
        .setNegativeButton("Voltar", null)
        .setPositiveButton(
            "Restaurar",
            (d, w) -> perform(true, token -> DriveBackup.restore(this, local, token), null))
        .show();
  }

  private void otherRestore() {
    new AlertDialog.Builder(this)
        .setTitle("Restaurar outro arquivo?")
        .setMessage("Substitui seus dados atuais. Confira se escolheu o backup correto.")
        .setNegativeButton("Voltar", null)
        .setPositiveButton("Escolher arquivo", (d, w) -> choose(BackupFiles.openIntent(), 102))
        .show();
  }

  private void disconnect() {
    new AlertDialog.Builder(this)
        .setTitle("Desconectar este destino?")
        .setMessage(
            "Interrompe o backup diário e remove o acesso salvo no Dose. O arquivo no Drive será"
                + " mantido.")
        .setNegativeButton("Voltar", null)
        .setPositiveButton(
            "Desconectar",
            (d, w) ->
                perform(
                    false,
                    token -> {
                      DriveBackup.disconnect(this);
                      return "Destino desconectado. O arquivo foi mantido.";
                    },
                    null))
        .show();
  }

  private void perform(boolean restore, DriveBackup.Operation operation, Runnable after) {
    boolean started =
        DriveBackup.submit(
            this,
            new CancellationSignal(),
            operation,
            (ok, message) -> {
              if (isFinishing() || isDestroyed()) return;
              if (restore) Ui.prepare(this);
              Ui.toast(this, message);
              render();
              if (ok && after != null) after.run();
            });
    if (!started) Ui.toast(this, "Aguarde a operação atual terminar.");
    render();
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (result != RESULT_OK || data == null || data.getData() == null) return;
    if (request == 201 || request == 202)
      perform(
          false,
          token ->
              DriveBackup.configure(this, data.getData(), data.getFlags(), request == 202, token),
          request == 202 ? () -> restoreDialog(false) : null);
    else if (request == 101 || request == 102) {
      manualWorking = true;
      render();
      BackupFiles.transfer(
          this,
          data.getData(),
          request == 102,
          () -> {
            manualWorking = false;
            render();
          });
    }
  }

  private String stamp(long at) {
    return Instant.ofEpochMilli(at)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm"));
  }
}
