package org.example.http.framework;

import lombok.extern.java.Log;
import org.example.http.framework.exception.MalformedRequestException;
import org.example.http.framework.exception.RequestBodyTooLarge;
import org.example.http.framework.exception.RequestHandleException;
import org.example.http.framework.exception.ServerException;
import org.example.http.framework.guava.Bytes;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.stream.Collectors;

@Log
public class Server {
  private static final byte[] CRLF = new byte[]{'\r', '\n'};
  private static final byte[] CRLFCRLF = new byte[]{'\r', '\n', '\r', '\n'};
  private final static int headersLimit = 4096;
  private final static long bodyLimit = 10 * 1024 * 1024;
  private final ExecutorService service = Executors.newFixedThreadPool(64);
  // GET, "/search", handler
  private final Map<String, Map<String, Handler>> routes = new HashMap<>();
  // 404 Not Found ->
  // 500 Internal Server error ->
  private final Handler notFoundHandler = (request, response) -> {
    // language=JSON
    final var body = "{\"status\": \"error\"}";
    try {
      response.write(
          (
              // language=HTTP
              "HTTP/1.1 404 Not Found\r\n" +
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
  };
  private final Handler internalErrorHandler = (request, response) -> {
    // language=JSON
    final var body = "{\"status\": \"error\"}";
    try {
      response.write(
          (
              // language=HTTP
              "HTTP/1.1 500 Internal Server Error\r\n" +
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
  };

  // state -> NOT_STARTED, STARTED, STOP, STOPPED
  private volatile boolean stop = false;

  public void get(String path, Handler handler) {
    register(HttpMethods.GET, path, handler);
  }

  public void post(String path, Handler handler) {
    register(HttpMethods.POST, path, handler);
  }

  public synchronized void register(String method, String path, Handler handler) {
    Optional.ofNullable(routes.get(method))
        .ifPresentOrElse(
            map -> map.put(path, handler),
            () -> routes.put(method, new HashMap<>(Map.of(path, handler)))
        );

//    final var map = routes.get(method);
//    if (map != null) {
//      map.put(path, handler);
//      return;
//    }
//    routes.put(method, new HashMap<>(Map.of(path, handler)));
  }

  public void listen(int port) {
    try (
        final var serverSocket = new ServerSocket(port)
    ) {
      log.log(Level.INFO, "server started at port: " + serverSocket.getLocalPort());
      while (!stop) {
        final var socket = serverSocket.accept();
        service.submit(() -> handle(socket));
      }
    } catch (IOException e) {
      throw new ServerException(e);
    }
  }

  public void stop() {
    this.stop = true;
    service.shutdownNow();
  }

  public void handle(final Socket socket) {
    try (
        socket;
        final var in = new BufferedInputStream(socket.getInputStream());
        final var out = new BufferedOutputStream(socket.getOutputStream());
    ) {
      log.log(Level.INFO, "connected: " + socket.getPort());
      final var buffer = new byte[headersLimit];
      in.mark(headersLimit);

      final var read = in.read(buffer);

      try {
        final var requestLineEndIndex = Bytes.indexOf(buffer, CRLF, 0, read) + CRLF.length;
        if (requestLineEndIndex == 1) {
          throw new MalformedRequestException("request line end not found");
        }

        final var requestLineParts = new String(buffer, 0, requestLineEndIndex).trim().split(" ");
        if (requestLineParts.length != 3) {
          throw new MalformedRequestException("request line must contains 3 parts");
        }

        final var method = requestLineParts[0];
        // TODO: uri split ? -> URLDecoder
        final var uri = requestLineParts[1];

        final var headersEndIndex = Bytes.indexOf(buffer, CRLFCRLF, requestLineEndIndex, read) + CRLFCRLF.length;
        if (headersEndIndex == 3) {
          throw new MalformedRequestException("headers too big");
        }

        var lastIndex = requestLineEndIndex;
        final var headers = new HashMap<String, String>();
        while (lastIndex < headersEndIndex - CRLF.length) {
          final var headerEndIndex = Bytes.indexOf(buffer, CRLF, lastIndex, headersEndIndex) + CRLF.length;
          if (headerEndIndex == 1) {
            throw new MalformedRequestException("can't find header end index");
          }
          final var header = new String(buffer, lastIndex, headerEndIndex - lastIndex);
          final var headerParts = Arrays.stream(header.split(":", 2))
              .map(String::trim)
              .collect(Collectors.toList());

          if (headerParts.size() != 2) {
            throw new MalformedRequestException("Invalid header: " + header);
          }

          headers.put(headerParts.get(0), headerParts.get(1));
          lastIndex = headerEndIndex;
        }

        final var contentLength = Integer.parseInt(headers.getOrDefault("Content-Length", "0"));

        if (contentLength > bodyLimit) {
          throw new RequestBodyTooLarge();
        }

        in.reset();
        in.skipNBytes(headersEndIndex);
        final var body = in.readNBytes(contentLength);

        final var request = Request.builder()
            .method(method)
            .path(uri)
            .headers(headers)
            .body(body)
            .build();

        final var response = out;

        final var handler = Optional.ofNullable(routes.get(request.getMethod()))
            .map(o -> o.get(request.getPath()))
            .orElse(notFoundHandler);

        try {
          handler.handle(request, response);
        } catch (Exception e) {
          internalErrorHandler.handle(request, response);
        }
      } catch (MalformedRequestException e) {
        // language=HTML
        final var html = "<h1>Mailformed request</h1>";
        out.write(
            (
                // language=HTTP
                "HTTP/1.1 400 Bad Request\r\n" +
                    "Server: nginx\r\n" +
                    "Content-Length: " + html.length() + "\r\n" +
                    "Content-Type: text/html; charset=UTF-8\r\n" +
                    "Connection: close\r\n" +
                    "\r\n" +
                    html
            ).getBytes(StandardCharsets.UTF_8)
        );
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}