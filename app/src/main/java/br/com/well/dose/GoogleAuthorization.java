package br.com.well.dose;

import android.accounts.Account;
import android.app.*;
import android.content.*;
import com.google.android.gms.auth.api.identity.*;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.*;
import com.google.android.gms.tasks.Tasks;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * Official Google Identity Services. No passwords, refresh tokens or access tokens are persisted.
 */
public final class GoogleAuthorization {
  public interface Result {
    void ready(String token);

    void resolution(PendingIntent intent);

    void failed(Exception error);
  }

  public static final class NeedsConsent extends IOException {
    public NeedsConsent() {
      super("Abra o Dose e reconecte sua conta Google.");
    }
  }

  private static List<Scope> scopes() {
    return Collections.singletonList(new Scope(GoogleDriveApi.SCOPE));
  }

  private static AuthorizationRequest request(String email, boolean choose) {
    AuthorizationRequest.Builder builder =
        AuthorizationRequest.builder()
            .setRequestedScopes(scopes())
            .setOptOutIncludingGrantedScopes(true);
    if (choose) builder.setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT);
    else if (email != null && !email.isEmpty())
      builder.setAccount(new Account(email, "com.google"));
    return builder.build();
  }

  private static String token(AuthorizationResult result) throws IOException {
    if (result.hasResolution()) throw new NeedsConsent();
    if (!result.getGrantedScopes().contains(GoogleDriveApi.SCOPE)
        || result.getAccessToken() == null
        || result.getAccessToken().isEmpty()) throw new NeedsConsent();
    return result.getAccessToken();
  }

  public static void begin(Activity activity, String email, boolean choose, Result callback) {
    if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity)
        != ConnectionResult.SUCCESS) {
      callback.failed(new IOException("Google Play Services indisponível."));
      return;
    }
    Identity.getAuthorizationClient(activity)
        .authorize(request(email, choose))
        .addOnSuccessListener(
            activity,
            result -> {
              if (result.hasResolution()) callback.resolution(result.getPendingIntent());
              else
                try {
                  callback.ready(token(result));
                } catch (Exception error) {
                  callback.failed(error);
                }
            })
        .addOnFailureListener(activity, callback::failed);
  }

  public static String fromIntent(Context c, Intent data) throws Exception {
    return token(Identity.getAuthorizationClient(c).getAuthorizationResultFromIntent(data));
  }

  public static String silent(Context c, String email) throws Exception {
    return token(
        Tasks.await(
            Identity.getAuthorizationClient(c).authorize(request(email, false)),
            40,
            TimeUnit.SECONDS));
  }

  public static void clear(Context c, String token) throws Exception {
    Tasks.await(
        Identity.getAuthorizationClient(c)
            .clearToken(ClearTokenRequest.builder().setToken(token).build()),
        20,
        TimeUnit.SECONDS);
  }

  public static void revoke(Context c, String email) throws Exception {
    RevokeAccessRequest request =
        RevokeAccessRequest.builder()
            .setAccount(new Account(email, "com.google"))
            .setScopes(scopes())
            .build();
    Tasks.await(Identity.getAuthorizationClient(c).revokeAccess(request), 30, TimeUnit.SECONDS);
  }

  public static boolean needsConsent(Throwable error) {
    while (error instanceof ExecutionException && error.getCause() != null)
      error = error.getCause();
    return error instanceof NeedsConsent
        || (error instanceof ApiException && ((ApiException) error).getStatusCode() == 4);
  }

  public static String errorMessage(Throwable error) {
    while ((error instanceof ExecutionException
            || error instanceof java.lang.reflect.InvocationTargetException)
        && error.getCause() != null) error = error.getCause();
    if (error instanceof NeedsConsent)
      return "Abra o Dose e reconecte sua conta Google para continuar.";
    if (error instanceof ApiException) {
      int status = ((ApiException) error).getStatusCode();
      if (status == 10)
        return "O cadastro OAuth do Dose no Google Cloud precisa ser conferido. Verifique pacote e"
            + " assinatura do APK no guia.";
      if (status == 16) return "A conexão com o Google foi cancelada.";
      if (status == 4) return "Reconecte sua conta Google no Dose.";
    }
    return "Não foi possível concluir com o Google. Confira sua conexão e a configuração da"
        + " integração.";
  }
}
