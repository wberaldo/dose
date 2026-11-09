package br.com.well.dose;

import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

/** Google authorization stays separate from the optional Android document picker. */
public final class BackupActivity extends Activity {
  private static final int AUTHORIZE = 440;
  private boolean authorizing, awaitingGoogle, wasBusy;
  private final Handler watcher = new Handler(Looper.getMainLooper());
  private final Runnable watch =
      new Runnable() {
        public void run() {
          if (wasBusy != (GoogleDriveBackup.busy() || authorizing)) render();
          watcher.postDelayed(this, 700);
        }
      };

  @Override
  public void onCreate(Bundle state) {
    Ui.prepare(this);
    super.onCreate(state);
    authorizing = awaitingGoogle = state != null && state.getBoolean("awaitingGoogle");
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    super.onSaveInstanceState(state);
    state.putBoolean("awaitingGoogle", awaitingGoogle);
  }

  @Override
  public void onResume() {
    super.onResume();
    GoogleDriveBackup.schedule(this);
    render();
    watcher.postDelayed(watch, 700);
  }

  @Override
  public void onPause() {
    watcher.removeCallbacksAndMessages(null);
    super.onPause();
  }

  @Override
  public void onStop() {
    // Activity-bound Google Task listeners are removed on stop. Allow an explicit retry
    // when no Google resolution was launched; an open resolution returns via onActivityResult.
    if (!awaitingGoogle) authorizing = false;
    super.onStop();
  }

  private void render() {
    wasBusy = GoogleDriveBackup.busy() || authorizing;
    boolean configured = GoogleDriveConfig.enabled(this),
        connected = GoogleDriveBackup.connected(this),
        protectedFile = GoogleDriveBackup.protectedFile(this);
    SharedPreferences p = GoogleDriveBackup.prefs(this);
    LinearLayout root = Ui.root(this), body = Ui.scroll(root);
    Ui.add(body, Ui.button(this, "‹ Voltar", false, this::finish), 8);
    Ui.title(body, "Backup na conta Google", "Salve e recupere sua rotina pelo Google Drive.");
    LinearLayout account = Ui.card(this);
    account.addView(Ui.text(this, "Sua conta Google", 21, Ui.INK, true));
    Ui.add(
        account,
        Ui.chip(
            this,
            !configured
                ? "Configuração Google pendente"
                : connected
                    ? p.getBoolean("needs_reconnect", false) ? "Reconectar conta" : "Conectado"
                    : "Não conectado",
            Ui.LIGHT,
            Ui.TEAL),
        12);
    if (!configured) {
      Ui.add(
          account,
          Ui.text(
              this,
              "A conexão Google ainda precisa ser ativada pelo responsável pelo app. Enquanto"
                  + " isso, nenhuma conta será solicitada e nenhum dado será enviado ao Drive.",
              14,
              Ui.MUTED,
              false),
          14);
      button(account, "Ver configuração necessária", true, this::setup, true);
    } else if (!connected) {
      Ui.add(
          account,
          Ui.text(
              this,
              "Conecte sua conta pelo Google e permita acesso aos arquivos que o Dose criar. O"
                  + " aplicativo Google Drive não precisa estar instalado.",
              14,
              Ui.MUTED,
              false),
          14);
      button(account, "Conectar conta Google", true, () -> connect(true), true);
    } else {
      Ui.add(
          account,
          Ui.text(
              this, p.getString("name", "") + "\n" + p.getString("email", ""), 17, Ui.INK, true),
          14);
      Ui.add(
          account,
          Ui.text(this, "Destino: pasta Dose no Google Drive desta conta.", 13, Ui.MUTED, false),
          10);
      if (protectedFile) {
        Ui.add(
            account,
            Ui.text(
                this,
                "Já existe uma cópia do Dose nesta conta. Restaure ou confirme a substituição pelos"
                    + " dados deste celular antes de fazer novos backups.",
                14,
                Ui.MUTED,
                false),
            14);
        button(account, "Restaurar do Google Drive", true, this::restore, true);
        button(account, "Substituir pelos dados deste celular", false, this::replace, true);
      } else {
        button(
            account,
            wasBusy ? "Aguarde…" : "Fazer backup agora",
            true,
            () -> perform(false, token -> GoogleDriveBackup.save(this, token)),
            !p.getBoolean("needs_reconnect", false));
        button(
            account,
            "Restaurar do Google Drive",
            false,
            this::restore,
            p.contains("file_id") && !p.getBoolean("needs_reconnect", false));
      }
      long saved = p.getLong("saved_at", 0);
      Ui.add(
          account,
          Ui.text(
              this,
              saved > 0
                  ? "Último backup confirmado: " + stamp(saved)
                  : p.contains("file_id")
                      ? "Cópia encontrada: "
                          + p.getString("file_name", "")
                          + "\n"
                          + p.getString("file_modified", "")
                      : "Nenhum backup do Dose encontrado nesta conta.",
              12,
              Ui.MUTED,
              false),
          14);
      button(
          account,
          p.getBoolean("needs_reconnect", false)
              ? "Reconectar conta Google"
              : "Trocar conta Google",
          false,
          () -> connect(true),
          true);
      button(account, "Desconectar conta Google", false, this::disconnect, true);
    }
    String error = p.getString("error", "");
    if (!error.isEmpty()) Ui.add(account, Ui.text(this, error, 13, Ui.AMBER, false), 12);
    Ui.add(body, account, 22);
    LinearLayout daily = Ui.card(this);
    daily.addView(Ui.text(this, "Backup automático diário", 20, Ui.INK, true));
    boolean automatic = GoogleDriveBackup.automatic(this);
    Ui.add(daily, Ui.chip(this, automatic ? "Ativado" : "Desativado", Ui.LIGHT, Ui.TEAL), 12);
    Ui.add(
        daily,
        Ui.text(
            this,
            "Depois de conectar, o Dose pode atualizar a cópia no Drive aproximadamente uma vez por"
                + " dia, com conexão e bateria suficiente. Os horários são gerenciados pelo"
                + " Android.",
            14,
            Ui.MUTED,
            false),
        12);
    button(
        daily,
        automatic ? "Desativar backup diário" : "Ativar backup diário",
        !automatic,
        () -> {
          try {
            GoogleDriveBackup.setAutomatic(this, !automatic);
            Ui.toast(this, automatic ? "Backup diário desativado." : "Backup diário ativado.");
          } catch (RuntimeException failure) {
            Ui.toast(this, "Confira a conexão Google e a cópia existente primeiro.");
          }
          render();
        },
        configured && connected && !protectedFile && !p.getBoolean("needs_reconnect", false));
    Ui.add(body, daily, 18);
    LinearLayout files = Ui.card(this);
    files.addView(Ui.text(this, "Backup em arquivo", 20, Ui.INK, true));
    Ui.add(
        files,
        Ui.text(
            this,
            "Exporte ou restaure um arquivo por conta própria. Esta opção usa o seletor do Android"
                + " e é independente da conexão Google acima.",
            14,
            Ui.MUTED,
            false),
        12);
    button(
        files,
        "Abrir opções de arquivo",
        false,
        () -> startActivity(new Intent(this, FileBackupActivity.class)),
        true);
    Ui.add(body, files, 18);
  }

  private void button(
      LinearLayout box, String label, boolean primary, Runnable action, boolean enabled) {
    TextView v = Ui.button(this, label, primary, action);
    v.setEnabled(enabled && !wasBusy);
    v.setAlpha(v.isEnabled() ? 1f : 0.4f);
    Ui.add(box, v, 12);
  }

  private void setup() {
    String sha;
    try {
      sha = GoogleDriveConfig.fingerprint(this);
    } catch (Exception e) {
      sha = "Consulte o guia do projeto.";
    }
    new AlertDialog.Builder(this)
        .setTitle("Configuração do responsável pelo app")
        .setMessage(
            "É necessário cadastrar o cliente OAuth Android, habilitar a API do Drive e configurar"
                + " a integração no projeto. O guia GOOGLE-DRIVE-CONFIGURACAO.md está no ZIP.\n\n"
                + "Pacote: "
                + getPackageName()
                + "\nSHA-1: "
                + sha
                + "\n\nEste APK ainda não está habilitado para conexão real.")
        .setPositiveButton("Entendi", null)
        .show();
  }

  private void connect(boolean choose) {
    if (!GoogleDriveConfig.enabled(this)) {
      setup();
      return;
    }
    new AlertDialog.Builder(this)
        .setTitle("Conectar sua conta Google?")
        .setMessage(
            "O Dose verá o nome e o e-mail da conta e terá acesso aos arquivos que criar no Drive."
                + " O backup inclui medicamentos, histórico, estoque, relatórios e preferências."
                + " Nenhum dado será enviado até você tocar em Fazer backup agora ou ativar o"
                + " diário.")
        .setNegativeButton("Voltar", null)
        .setPositiveButton(
            "Escolher conta",
            (d, w) -> {
              if (GoogleDriveBackup.automatic(this)) GoogleDriveBackup.setAutomatic(this, false);
              authorizing = true;
              render();
              GoogleAuthorization.begin(
                  this,
                  GoogleDriveBackup.prefs(this).getString("email", ""),
                  choose,
                  new GoogleAuthorization.Result() {
                    public void ready(String token) {
                      authorizing = false;
                      adopt(token);
                    }

                    public void resolution(PendingIntent pending) {
                      try {
                        awaitingGoogle = true;
                        startIntentSenderForResult(
                            pending.getIntentSender(), AUTHORIZE, null, 0, 0, 0);
                      } catch (Exception error) {
                        failed(error);
                      }
                    }

                    public void failed(Exception error) {
                      authorizing = awaitingGoogle = false;
                      GoogleDriveBackup.prefs(BackupActivity.this)
                          .edit()
                          .putString("error", GoogleAuthorization.errorMessage(error))
                          .commit();
                      render();
                    }
                  });
            })
        .show();
  }

  private void adopt(String token) {
    perform(false, signal -> GoogleDriveBackup.connect(this, token, signal));
  }

  private void restore() {
    new AlertDialog.Builder(this)
        .setTitle("Restaurar do Google Drive?")
        .setMessage(
            "Substitui os medicamentos, o histórico, o estoque e as preferências atuais pelos dados"
                + " do backup desta conta. Salve uma cópia dos dados atuais antes, se precisar.")
        .setNegativeButton("Voltar", null)
        .setPositiveButton(
            "Restaurar", (d, w) -> perform(true, token -> GoogleDriveBackup.restore(this, token)))
        .show();
  }

  private void replace() {
    new AlertDialog.Builder(this)
        .setTitle("Substituir o backup do Drive?")
        .setMessage(
            "A cópia desta conta será atualizada com os dados deste celular. Para recuperar a cópia"
                + " anterior, escolha Restaurar.")
        .setNegativeButton("Voltar", null)
        .setPositiveButton(
            "Substituir",
            (d, w) ->
                perform(
                    false,
                    token -> {
                      GoogleDriveBackup.replaceConfirmed(this);
                      return GoogleDriveBackup.save(this, token);
                    }))
        .show();
  }

  private void disconnect() {
    new AlertDialog.Builder(this)
        .setTitle("Desconectar conta Google?")
        .setMessage(
            "Interrompe o diário e revoga o acesso do Dose. Seus medicamentos e arquivos no Drive"
                + " serão mantidos.")
        .setNegativeButton("Voltar", null)
        .setPositiveButton(
            "Desconectar", (d, w) -> perform(false, token -> GoogleDriveBackup.disconnect(this)))
        .show();
  }

  private void perform(boolean restoring, GoogleDriveBackup.Work work) {
    boolean started =
        GoogleDriveBackup.submit(
            this,
            new CancellationSignal(),
            work,
            (ok, message) -> {
              if (isFinishing() || isDestroyed()) return;
              if (restoring) Ui.prepare(this);
              Ui.toast(this, message);
              render();
            });
    if (!started) Ui.toast(this, "Aguarde a operação atual terminar.");
    render();
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request != AUTHORIZE) return;
    authorizing = awaitingGoogle = false;
    if (result != RESULT_OK || data == null) {
      Ui.toast(this, "Conexão Google cancelada.");
      render();
      return;
    }
    try {
      adopt(GoogleAuthorization.fromIntent(this, data));
    } catch (Exception error) {
      GoogleDriveBackup.prefs(this)
          .edit()
          .putString("error", GoogleAuthorization.errorMessage(error))
          .commit();
      render();
    }
  }

  private String stamp(long value) {
    return Instant.ofEpochMilli(value)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm"));
  }
}
