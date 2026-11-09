package br.com.well.dose;

import android.content.*;
import android.content.pm.*;
import android.os.Build;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import org.json.JSONObject;

public final class GoogleDriveConfig {
  private GoogleDriveConfig() {}

  public static boolean enabled(Context c) {
    try (InputStream in = c.getResources().openRawResource(R.raw.google_drive_config)) {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      byte[] b = new byte[1024];
      int n;
      while ((n = in.read(b)) != -1) {
        if (bytes.size() + n > 16384) return false;
        bytes.write(b, 0, n);
      }
      return valid(
          new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8)),
          c.getPackageName(),
          fingerprint(c));
    } catch (Exception ignored) {
      return false;
    }
  }

  public static boolean valid(JSONObject value, String pkg, String sha1) {
    return value.optBoolean("enabled", false)
        && value
            .optString("android_client_id")
            .matches("[0-9]+-[A-Za-z0-9_-]+\\.apps\\.googleusercontent\\.com")
        && sha1.replace(":", "").matches("[A-Fa-f0-9]{40}")
        && pkg.equals(value.optString("package_name"))
        && sha1.replace(":", "").equalsIgnoreCase(value.optString("sha1").replace(":", ""));
  }

  public static String fingerprint(Context c) throws Exception {
    byte[] cert;
    if (Build.VERSION.SDK_INT >= 28)
      cert =
          c.getPackageManager()
              .getPackageInfo(c.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES)
              .signingInfo
              .getApkContentsSigners()[0]
              .toByteArray();
    else
      cert =
          c.getPackageManager()
              .getPackageInfo(c.getPackageName(), PackageManager.GET_SIGNATURES)
              .signatures[0]
              .toByteArray();
    StringBuilder out = new StringBuilder();
    for (byte b : MessageDigest.getInstance("SHA-1").digest(cert))
      out.append(String.format(Locale.ROOT, "%02X", b & 255));
    return out.toString();
  }
}
