package br.com.well.dose;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.JSONObject;
import org.junit.Test;

public class GoogleDriveApiTest {
  private static final String TOKEN = "memory-only-test-token";
  private static final byte[] DATA = "{\"version\":4,\"meds\":[]}".getBytes(StandardCharsets.UTF_8);
  private static final GoogleDriveApi.Check CHECK = () -> {};

  private static final class Call {
    String method, url, token;
    Map<String, String> headers;
    byte[] body;

    Call(String m, String u, String t, Map<String, String> h, byte[] b) {
      method = m;
      url = u;
      token = t;
      headers = h;
      body = b;
    }
  }

  private static final class Server implements GoogleDriveApi.Transport {
    final Queue<GoogleDriveApi.Reply> replies = new ArrayDeque<>();
    final List<Call> calls = new ArrayList<>();

    Server reply(int status, String data) {
      replies.add(
          new GoogleDriveApi.Reply(
              status, data.getBytes(StandardCharsets.UTF_8), Collections.emptyMap()));
      return this;
    }

    Server session(String location) {
      replies.add(
          new GoogleDriveApi.Reply(
              200, new byte[0], Collections.singletonMap("location", location)));
      return this;
    }

    public GoogleDriveApi.Reply send(
        String m,
        String u,
        String t,
        Map<String, String> h,
        byte[] b,
        int limit,
        GoogleDriveApi.Check c)
        throws IOException {
      c.run();
      calls.add(new Call(m, u, t, h, b));
      if (replies.isEmpty()) throw new AssertionError("Unexpected request: " + m + " " + u);
      return replies.remove();
    }
  }

  private static String metadata(String id, String md5, int size) {
    return "{\"id\":\""
        + id
        + "\",\"name\":\"Dose-backup.json\",\"modifiedTime\":\"2026-11-08T22:00:00Z\",\"size\":\""
        + size
        + "\",\"md5Checksum\":\""
        + md5
        + "\"}";
  }

  private static GoogleDriveApi.RemoteFile file() throws IOException {
    return new GoogleDriveApi.RemoteFile(
        "backup_1", "Dose-backup.json", "old", DATA.length, GoogleDriveApi.md5(DATA));
  }

  @Test
  public void profileUsesActualGoogleIdentityAndMinimalFields() throws Exception {
    Server s =
        new Server()
            .reply(
                200,
                "{\"user\":{\"permissionId\":\"owner1\",\"emailAddress\":\"well@example.test\",\"displayName\":\"Well\"}}");
    GoogleDriveApi.Profile p = new GoogleDriveApi(s).profile(TOKEN, CHECK);
    assertEquals("owner1", p.id);
    assertEquals("well@example.test", p.email);
    assertEquals("Well", p.name);
    assertTrue(s.calls.get(0).url.contains("about?fields=user("));
    assertEquals(TOKEN, s.calls.get(0).token);
    assertFalse(s.calls.get(0).url.contains(TOKEN));
  }

  @Test
  public void incompleteProfileIsRejected() throws Exception {
    Server s = new Server().reply(200, "{\"user\":{\"emailAddress\":\"well@example.test\"}}");
    assertThrows(IOException.class, () -> new GoogleDriveApi(s).profile(TOKEN, CHECK));
  }

  @Test
  public void listOnlyAppMarkedJsonAndLatestVersion() throws Exception {
    Server s =
        new Server()
            .reply(
                200,
                "{\"files\":["
                    + metadata("backup_1", GoogleDriveApi.md5(DATA), DATA.length)
                    + "]}");
    GoogleDriveApi.RemoteFile f = new GoogleDriveApi(s).latest(TOKEN, CHECK);
    assertEquals("backup_1", f.id);
    assertEquals(DATA.length, f.size);
    String query = java.net.URLDecoder.decode(s.calls.get(0).url, "UTF-8");
    assertTrue(query.contains("dose_backup_v4"));
    assertTrue(query.contains("trashed = false"));
    assertTrue(query.contains("application/json"));
    assertTrue(query.contains("orderBy=modifiedTime desc"));
  }

  @Test
  public void emptyAccountHasNoBackup() throws Exception {
    assertNull(new GoogleDriveApi(new Server().reply(200, "{\"files\":[]}")).latest(TOKEN, CHECK));
  }

  @Test
  public void createFolderAndUploadVerifiedBytes() throws Exception {
    Server s =
        new Server()
            .reply(200, "{\"files\":[]}")
            .reply(200, "{\"id\":\"folder_1\"}")
            .session("https://www.googleapis.com/upload/drive/v3/files?upload_id=test")
            .reply(200, metadata("backup_1", GoogleDriveApi.md5(DATA), DATA.length));
    GoogleDriveApi.RemoteFile f = new GoogleDriveApi(s).upload(TOKEN, null, DATA, CHECK);
    assertEquals("backup_1", f.id);
    assertEquals(4, s.calls.size());
    assertEquals("POST", s.calls.get(1).method);
    JSONObject folder = new JSONObject(new String(s.calls.get(1).body, StandardCharsets.UTF_8));
    assertEquals("Dose", folder.getString("name"));
    JSONObject meta = new JSONObject(new String(s.calls.get(2).body, StandardCharsets.UTF_8));
    assertEquals("folder_1", meta.getJSONArray("parents").getString(0));
    assertEquals("PUT", s.calls.get(3).method);
    assertArrayEquals(DATA, s.calls.get(3).body);
    for (Call call : s.calls) assertFalse(call.url.contains(TOKEN));
  }

  @Test
  public void updateReusesExistingFileWithoutCreatingFolder() throws Exception {
    Server s =
        new Server()
            .session("https://www.googleapis.com/upload/drive/v3/files/backup_1?upload_id=test")
            .reply(200, metadata("backup_1", GoogleDriveApi.md5(DATA), DATA.length));
    new GoogleDriveApi(s).upload(TOKEN, file(), DATA, CHECK);
    assertEquals(2, s.calls.size());
    assertEquals("PATCH", s.calls.get(0).method);
    assertTrue(s.calls.get(0).url.contains("files/backup_1?uploadType=resumable"));
    assertFalse(
        new JSONObject(new String(s.calls.get(0).body, StandardCharsets.UTF_8)).has("parents"));
  }

  @Test
  public void uploadRejectsUnconfirmedChecksum() throws Exception {
    Server s =
        new Server()
            .session("https://www.googleapis.com/upload/drive/v3/files/backup_1")
            .reply(200, metadata("backup_1", "wrong", DATA.length));
    assertThrows(IOException.class, () -> new GoogleDriveApi(s).upload(TOKEN, file(), DATA, CHECK));
  }

  @Test
  public void uploadRejectsUnconfirmedSize() throws Exception {
    Server s =
        new Server()
            .session("https://www.googleapis.com/upload/drive/v3/files/backup_1")
            .reply(200, metadata("backup_1", GoogleDriveApi.md5(DATA), DATA.length + 1));
    assertThrows(IOException.class, () -> new GoogleDriveApi(s).upload(TOKEN, file(), DATA, CHECK));
  }

  @Test
  public void updateRejectsDifferentReturnedFile() throws Exception {
    Server s =
        new Server()
            .session("https://www.googleapis.com/upload/drive/v3/files/backup_1")
            .reply(200, metadata("other_file", GoogleDriveApi.md5(DATA), DATA.length));
    assertThrows(IOException.class, () -> new GoogleDriveApi(s).upload(TOKEN, file(), DATA, CHECK));
  }

  @Test
  public void foreignSessionNeverReceivesTokenOrBackup() throws Exception {
    Server s = new Server().session("https://attacker.example/upload");
    assertThrows(IOException.class, () -> new GoogleDriveApi(s).upload(TOKEN, file(), DATA, CHECK));
    assertEquals(1, s.calls.size());
  }

  @Test
  public void downloadVerifiesBytesBeforeReturningThem() throws Exception {
    Server s = new Server().reply(200, new String(DATA, StandardCharsets.UTF_8));
    assertArrayEquals(DATA, new GoogleDriveApi(s).download(TOKEN, file(), CHECK));
    assertTrue(s.calls.get(0).url.endsWith("files/backup_1?alt=media"));
  }

  @Test
  public void downloadRejectsCorruption() throws Exception {
    Server s = new Server().reply(200, "corrupted");
    assertThrows(IOException.class, () -> new GoogleDriveApi(s).download(TOKEN, file(), CHECK));
  }

  @Test
  public void invalidIdCannotChangeRequestPath() throws Exception {
    Server s = new Server();
    GoogleDriveApi.RemoteFile evil =
        new GoogleDriveApi.RemoteFile("../about?token=evil", "", "", 1, "");
    assertThrows(IOException.class, () -> new GoogleDriveApi(s).download(TOKEN, evil, CHECK));
    assertTrue(s.calls.isEmpty());
  }

  @Test
  public void unsafeTokenIsRejectedBeforeTransport() throws Exception {
    Server s = new Server();
    for (String token : Arrays.asList("", "bad\r\nheader"))
      assertThrows(IOException.class, () -> new GoogleDriveApi(s).profile(token, CHECK));
    assertTrue(s.calls.isEmpty());
  }

  @Test
  public void onlyPinnedHttpsEndpointIsTrusted() {
    assertTrue(GoogleDriveApi.trustedUrl("https://www.googleapis.com/drive/v3/about?fields=user"));
    for (String url :
        Arrays.asList(
            "http://www.googleapis.com/drive/v3/files",
            "https://www.googleapis.com.evil.test/drive/v3/files",
            "https://evil@www.googleapis.com/drive/v3/files",
            "https://www.googleapis.com:8443/drive/v3/files",
            "https://www.googleapis.com/other/path",
            "invalid")) assertFalse(url, GoogleDriveApi.trustedUrl(url));
  }

  @Test
  public void failuresDoNotExposeGoogleMessageOrTokens() throws Exception {
    Server s =
        new Server()
            .reply(
                403,
                "{\"error\":{\"message\":\""
                    + TOKEN
                    + "\",\"errors\":[{\"reason\":\"storageQuotaExceeded\"}]}}");
    GoogleDriveApi.ApiFailure e =
        assertThrows(
            GoogleDriveApi.ApiFailure.class, () -> new GoogleDriveApi(s).profile(TOKEN, CHECK));
    assertEquals(403, e.status);
    assertEquals("storageQuotaExceeded", e.reason);
    assertFalse(e.toString().contains(TOKEN));
  }

  @Test
  public void arbitraryErrorReasonIsRedacted() throws Exception {
    Server s = new Server().reply(401, "{\"error\":{\"errors\":[{\"reason\":\"" + TOKEN + "\"}]}}");
    GoogleDriveApi.ApiFailure e =
        assertThrows(
            GoogleDriveApi.ApiFailure.class, () -> new GoogleDriveApi(s).profile(TOKEN, CHECK));
    assertEquals("requestFailed", e.reason);
    assertFalse(e.toString().contains(TOKEN));
  }

  @Test
  public void cancellationPreventsRequest() throws Exception {
    Server s = new Server();
    assertThrows(
        InterruptedIOException.class,
        () ->
            new GoogleDriveApi(s)
                .profile(
                    TOKEN,
                    () -> {
                      throw new InterruptedIOException();
                    }));
    assertTrue(s.calls.isEmpty());
  }

  @Test
  public void oversizedMetadataIsRejected() throws Exception {
    Server s =
        new Server().reply(200, "{\"files\":[" + metadata("backup_1", "", 20_000_001) + "]}");
    assertThrows(IOException.class, () -> new GoogleDriveApi(s).latest(TOKEN, CHECK));
  }

  @Test
  public void missingChecksumIsRejected() throws Exception {
    Server s =
        new Server().reply(200, "{\"files\":[" + metadata("backup_1", "", DATA.length) + "]}");
    assertThrows(IOException.class, () -> new GoogleDriveApi(s).latest(TOKEN, CHECK));
  }

  @Test
  public void missingRemoteVersionIsRejected() throws Exception {
    JSONObject f = new JSONObject(metadata("backup_1", GoogleDriveApi.md5(DATA), DATA.length));
    f.remove("modifiedTime");
    Server s = new Server().reply(200, "{\"files\":[" + f + "]}");
    assertThrows(IOException.class, () -> new GoogleDriveApi(s).latest(TOKEN, CHECK));
  }

  @Test
  public void malformedJsonIsRejected() throws Exception {
    assertThrows(
        IOException.class,
        () -> new GoogleDriveApi(new Server().reply(200, "not json")).latest(TOKEN, CHECK));
  }

  @Test
  public void changedRemoteRequiresDecision() throws Exception {
    GoogleDriveApi.RemoteFile f = file();
    assertTrue(GoogleDriveBackup.sameVersion(f.id, f.modified, f));
    assertFalse(GoogleDriveBackup.sameVersion(f.id, "older", f));
    assertFalse(GoogleDriveBackup.sameVersion("other", f.modified, f));
    assertFalse(GoogleDriveBackup.sameVersion(f.id, "", f));
    assertTrue(GoogleDriveBackup.sameVersion("", "", null));
  }

  @Test
  public void accountMismatchIsRejected() throws Exception {
    GoogleDriveBackup.requireOwner("owner1", "owner1");
    assertThrows(IOException.class, () -> GoogleDriveBackup.requireOwner("owner1", "owner2"));
    assertThrows(IOException.class, () -> GoogleDriveBackup.requireOwner("", "owner2"));
  }

  @Test
  public void incompleteGoogleConfigurationRemainsDisabled() throws Exception {
    JSONObject cfg = new JSONObject();
    assertFalse(GoogleDriveConfig.valid(cfg, "br.com.well.dose", "A".repeat(40)));
    cfg.put("enabled", true)
        .put("drive_backup_use_authorized", true)
        .put("android_client_id", "1234-test.apps.googleusercontent.com")
        .put("package_name", "br.com.well.dose")
        .put("sha1", "A".repeat(40));
    assertTrue(GoogleDriveConfig.valid(cfg, "br.com.well.dose", "A".repeat(40)));
    assertFalse(GoogleDriveConfig.valid(cfg, "br.com.well.dose", "B".repeat(40)));
    assertFalse(GoogleDriveConfig.valid(cfg, "other.package", "A".repeat(40)));
    cfg.put("drive_backup_use_authorized", false);
    assertFalse(GoogleDriveConfig.valid(cfg, "br.com.well.dose", "A".repeat(40)));
  }
}
