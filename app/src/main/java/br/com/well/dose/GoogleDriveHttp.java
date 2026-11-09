package br.com.well.dose;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;

final class GoogleDriveHttp implements GoogleDriveApi.Transport {
  @Override
  public GoogleDriveApi.Reply send(
      String method,
      String url,
      String token,
      Map<String, String> headers,
      byte[] body,
      int limit,
      GoogleDriveApi.Check check)
      throws IOException {
    if (!GoogleDriveApi.trustedUrl(url)) throw new IOException("Endereço Google inválido.");
    HttpsURLConnection connection = (HttpsURLConnection) new URL(url).openConnection();
    connection.setInstanceFollowRedirects(false);
    connection.setConnectTimeout(20000);
    connection.setReadTimeout(60000);
    connection.setRequestMethod(method);
    connection.setRequestProperty("Authorization", "Bearer " + token);
    connection.setRequestProperty("Accept", "application/json");
    for (Map.Entry<String, String> header : headers.entrySet())
      connection.setRequestProperty(header.getKey(), header.getValue());
    try {
      check.run();
      if (body != null) {
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(body.length);
        try (OutputStream out = connection.getOutputStream()) {
          for (int offset = 0; offset < body.length; offset += 8192) {
            check.run();
            out.write(body, offset, Math.min(8192, body.length - offset));
          }
        }
      }
      int status = connection.getResponseCode();
      int bound = status >= 200 && status < 300 ? limit : 65536;
      InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      if (input != null)
        try (InputStream in = input) {
          byte[] buffer = new byte[8192];
          int n;
          while ((n = in.read(buffer)) != -1) {
            check.run();
            if (bytes.size() + n > bound) throw new IOException("Resposta Google muito grande.");
            bytes.write(buffer, 0, n);
          }
        }
      Map<String, String> result = new HashMap<>();
      for (Map.Entry<String, List<String>> entry : connection.getHeaderFields().entrySet())
        if (entry.getKey() != null && !entry.getValue().isEmpty())
          result.put(entry.getKey(), entry.getValue().get(0));
      return new GoogleDriveApi.Reply(status, bytes.toByteArray(), result);
    } finally {
      connection.disconnect();
    }
  }
}
