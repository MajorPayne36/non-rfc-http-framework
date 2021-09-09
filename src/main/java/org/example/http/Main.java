package org.example.http;

import lombok.extern.java.Log;
import org.example.http.framework.Server;
import org.example.http.framework.exception.RequestHandleException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Log
public class Main {
  public static void main(String[] args) throws InterruptedException {
    final var server = new Server();
    server.get("/courses", (req, res) -> {
      final var body = "{\"courses\": []}";
      try {
        res.write(
            (
                // language=HTTP
                "HTTP/1.1 200 OK\r\n" +
                    "Content-Length: " + body.length() + "\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Connection: close\r\n" +
                    "\r\n" +
                    body
            ).getBytes(StandardCharsets.UTF_8)
        );
      } catch (IOException e) {
        throw new RequestHandleException(e);
      }
    });
    server.post("/courses", (req, res) -> {
      try {
        // TODO:
        res.write(
            (
                // language=HTTP
                "HTTP/1.1 201 Created\r\n" +
                "Content-Length: 0\r\n" +
                "Content-Type: application/json\r\n" +
                "Connection: close\r\n" +
                "\r\n"
            ).getBytes(StandardCharsets.UTF_8)
        );
      } catch (IOException e) {
        throw new RequestHandleException(e);
      }
    });
    server.listen(9999);
  }
}
