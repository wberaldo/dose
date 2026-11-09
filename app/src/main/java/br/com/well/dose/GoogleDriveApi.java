package br.com.well.dose;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Drive v3 protocol. Credentials stay in request memory and never enter URLs or diagnostics. */
public final class GoogleDriveApi {
  public static final String SCOPE = "https://www.googleapis.com/auth/drive.file";
  public static final int MAX_BACKUP = 20_000_000;
  private static final String API = "https://www.googleapis.com/drive/v3/";
  private static final String UPLOAD = "https://www.googleapis.com/upload/drive/v3/files";
  private static final String MARKER = "dose_backup_v4";
  private final Transport transport;

  public interface Check {
    void run() throws IOException;
  }

  public interface Transport {
    Reply send(
        String method,
        String url,
        String token,
        Map<String, String> headers,
        byte[] body,
        int limit,
        Check check)
        throws IOException;
  }

  public static final class Reply {
    public final int status;
    public final byte[] body;
    public final Map<String, String> headers;

    public Reply(int status, byte[] body, Map<String, String> headers) {
      this.status = status;
      this.body = body;
      this.headers = headers;
    }

    public String header(String name) {
      for (Map.Entry<String, String> entry : headers.entrySet())
        if (entry.getKey().equalsIgnoreCase(name)) return entry.getValue();
      return "";
    }
  }

  public static final class ApiFailure extends IOException {
    public final int status;
    public final String reason;

    public ApiFailure(int status, String reason) {
      super("Drive HTTP " + status + " (" + reason + ")");
      this.status = status;
      this.reason = reason;
    }
  }

  public static final class Profile {
    public final String id, email, name;

    public Profile(String id, String email, String name) {
      this.id = id;
      this.email = email;
      this.name = name;
    }
  }

  public static final class RemoteFile {
    public final String id, name, modified, checksum;
    public final long size;

    public RemoteFile(String id, String name, String modified, long size, String checksum) {
      this.id = id;
      this.name = name;
      this.modified = modified;
      this.size = size;
      this.checksum = checksum;
    }
  }

  public GoogleDriveApi(Transport transport) {
    this.transport = transport;
  }

  public Profile profile(String token, Check check) throws IOException {
    JSONObject user =
        json(request(
                "GET",
                API + "about?fields=user(permissionId,emailAddress,displayName)",
                token,
                null,
                null,
                262144,
                check))
            .optJSONObject("user");
    if (user == null
        || user.optString("permissionId").isEmpty()
        || !user.optString("emailAddress").contains("@"))
      throw new IOException("Identidade Google incompleta.");
    return new Profile(
        user.optString("permissionId"),
        user.optString("emailAddress"),
        user.optString("displayName"));
  }

  public RemoteFile latest(String token, Check check) throws IOException {
    String query =
        "trashed = false and mimeType = 'application/json' and appProperties has { key='dose_kind'"
            + " and value='"
            + MARKER
            + "' }";
    JSONObject result =
        json(
            request(
                "GET",
                API
                    + "files?q="
                    + encode(query)
                    + "&orderBy=modifiedTime%20desc&pageSize=1&fields=files(id,name,modifiedTime,size,md5Checksum)",
                token,
                null,
                null,
                262144,
                check));
    JSONArray files = result.optJSONArray("files");
    return files == null || files.length() == 0 ? null : remote(object(files, 0));
  }

  public RemoteFile upload(String token, RemoteFile previous, byte[] data, Check check)
      throws IOException {
    if (data.length == 0 || data.length > MAX_BACKUP)
      throw new IOException("Tamanho de backup inválido.");
    JSONObject metadata = new JSONObject();
    try {
      metadata.put("name", "Dose-backup.json");
      metadata.put("mimeType", "application/json");
      metadata.put("appProperties", new JSONObject().put("dose_kind", MARKER));
      if (previous == null) metadata.put("parents", new JSONArray().put(folder(token, check)));
    } catch (JSONException e) {
      throw new IOException("Metadados inválidos.", e);
    }
    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", "application/json; charset=UTF-8");
    headers.put("X-Upload-Content-Type", "application/json");
    headers.put("X-Upload-Content-Length", Integer.toString(data.length));
    String target =
        UPLOAD
            + (previous == null ? "" : "/" + fileId(previous.id))
            + "?uploadType=resumable&fields=id,name,modifiedTime,size,md5Checksum";
    Reply session =
        request(
            previous == null ? "POST" : "PATCH",
            target,
            token,
            headers,
            utf8(metadata.toString()),
            262144,
            check);
    String location = session.header("Location");
    if (!trustedUrl(location)) throw new IOException("Destino de upload não reconhecido.");
    headers = new HashMap<>();
    headers.put("Content-Type", "application/json");
    RemoteFile uploaded =
        remote(json(request("PUT", location, token, headers, data, 262144, check)));
    if (uploaded.size != data.length || !uploaded.checksum.equalsIgnoreCase(md5(data)))
      throw new IOException("O Drive não confirmou a integridade do backup.");
    if (previous != null && !previous.id.equals(uploaded.id))
      throw new IOException("O Drive respondeu com outro arquivo.");
    return uploaded;
  }

  public byte[] download(String token, RemoteFile file, Check check) throws IOException {
    byte[] bytes =
        request(
                "GET",
                API + "files/" + fileId(file.id) + "?alt=media",
                token,
                null,
                null,
                MAX_BACKUP,
                check)
            .body;
    if (bytes.length == 0
        || bytes.length != file.size
        || file.checksum.isEmpty()
        || !md5(bytes).equalsIgnoreCase(file.checksum))
      throw new IOException("O arquivo recebido não passou na conferência de integridade.");
    return bytes;
  }

  private String folder(String token, Check check) throws IOException {
    String q =
        "trashed = false and mimeType = 'application/vnd.google-apps.folder' and appProperties has"
            + " { key='dose_kind' and value='dose_folder_v1' }";
    JSONObject existing =
        json(
            request(
                "GET",
                API + "files?q=" + encode(q) + "&pageSize=1&fields=files(id)",
                token,
                null,
                null,
                262144,
                check));
    JSONArray files = existing.optJSONArray("files");
    if (files != null && files.length() > 0) return fileId(object(files, 0).optString("id"));
    try {
      JSONObject meta =
          new JSONObject()
              .put("name", "Dose")
              .put("mimeType", "application/vnd.google-apps.folder")
              .put("appProperties", new JSONObject().put("dose_kind", "dose_folder_v1"));
      Map<String, String> headers =
          Collections.singletonMap("Content-Type", "application/json; charset=UTF-8");
      return fileId(
          json(request(
                  "POST",
                  API + "files?fields=id",
                  token,
                  headers,
                  utf8(meta.toString()),
                  262144,
                  check))
              .optString("id"));
    } catch (JSONException e) {
      throw new IOException("Metadados inválidos.", e);
    }
  }

  private Reply request(
      String method,
      String url,
      String token,
      Map<String, String> headers,
      byte[] body,
      int limit,
      Check check)
      throws IOException {
    check.run();
    if (!trustedUrl(url)
        || token == null
        || token.isEmpty()
        || token.indexOf('\r') >= 0
        || token.indexOf('\n') >= 0) throw new IOException("Requisição Google inválida.");
    Reply response =
        transport.send(
            method,
            url,
            token,
            headers == null ? Collections.emptyMap() : headers,
            body,
            limit,
            check);
    check.run();
    if (response.body.length > limit) throw new IOException("Resposta do Drive muito grande.");
    if (response.status < 200 || response.status >= 300)
      throw new ApiFailure(response.status, reason(response));
    return response;
  }

  private static String reason(Reply reply) {
    try {
      JSONObject error =
          new JSONObject(new String(reply.body, StandardCharsets.UTF_8)).optJSONObject("error");
      JSONArray errors = error == null ? null : error.optJSONArray("errors");
      String value =
          errors == null || errors.length() == 0 ? "" : object(errors, 0).optString("reason");
      return Arrays.asList(
                  "storageQuotaExceeded",
                  "userRateLimitExceeded",
                  "rateLimitExceeded",
                  "accessNotConfigured",
                  "insufficientPermissions",
                  "appNotAuthorizedToFile")
              .contains(value)
          ? value
          : "requestFailed";
    } catch (Exception ignored) {
      return "requestFailed";
    }
  }

  private static JSONObject object(JSONArray list, int i) throws IOException {
    try {
      return list.getJSONObject(i);
    } catch (JSONException e) {
      throw new IOException("Resposta Google inválida.", e);
    }
  }

  private static JSONObject json(Reply response) throws IOException {
    try {
      return new JSONObject(new String(response.body, StandardCharsets.UTF_8));
    } catch (JSONException e) {
      throw new IOException("Resposta Google inválida.", e);
    }
  }

  private static RemoteFile remote(JSONObject object) throws IOException {
    String id = fileId(object.optString("id"));
    long size;
    try {
      size = Long.parseLong(object.optString("size", "-1"));
    } catch (NumberFormatException e) {
      throw new IOException("Tamanho Google inválido.", e);
    }
    if (size < 0 || size > MAX_BACKUP)
      throw new IOException("Backup no Drive excede o limite permitido.");
    if (object.optString("modifiedTime").isEmpty()
        || !object.optString("md5Checksum").matches("[A-Fa-f0-9]{32}"))
      throw new IOException("Metadados de integridade Google incompletos.");
    return new RemoteFile(
        id,
        object.optString("name", "Dose-backup.json"),
        object.optString("modifiedTime"),
        size,
        object.optString("md5Checksum"));
  }

  private static String fileId(String id) throws IOException {
    if (!id.matches("[A-Za-z0-9_-]{1,200}"))
      throw new IOException("Identificador Google inválido.");
    return id;
  }

  public static boolean trustedUrl(String value) {
    try {
      URI uri = new URI(value);
      return "https".equals(uri.getScheme())
          && "www.googleapis.com".equals(uri.getHost())
          && uri.getUserInfo() == null
          && (uri.getPort() == -1 || uri.getPort() == 443)
          && (uri.getPath().startsWith("/drive/v3/")
              || uri.getPath().startsWith("/upload/drive/v3/"));
    } catch (Exception e) {
      return false;
    }
  }

  private static String encode(String value) throws IOException {
    return URLEncoder.encode(value, "UTF-8");
  }

  private static byte[] utf8(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  public static String md5(byte[] data) throws IOException {
    try {
      StringBuilder out = new StringBuilder();
      for (byte b : MessageDigest.getInstance("MD5").digest(data))
        out.append(String.format(Locale.ROOT, "%02x", b & 255));
      return out.toString();
    } catch (Exception e) {
      throw new IOException("Não foi possível conferir o backup.", e);
    }
  }
}
