package org.example.http.app.handler;

import org.example.http.framework.HttpMethods;
import org.example.http.framework.Request;
import org.example.http.framework.annotation.RequestHeader;
import org.example.http.framework.annotation.RequestMapping;
import org.example.http.framework.exception.RequestHandleException;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class CoursesHandler {
  @RequestMapping(method = HttpMethods.GET, path = "/courses")
  public void getCourses(
      OutputStream response,
      @RequestHeader("Username") String username
  ) {
    final var body = "{\"courses\": []}";
    try {
      response.write(
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
  }
}
