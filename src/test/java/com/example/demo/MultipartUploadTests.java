package com.example.demo;

import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.http.*;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

/** Gửi HTTP thật qua bộ phân tích multipart của Tomcat; MockMvc không kiểm tra các giới hạn này. */
class MultipartUploadTests {
 @TempDir Path directory;
 Tomcat server;
 URI endpoint;
 @BeforeEach void start() throws Exception {
  var properties = new Properties();
  try (var input = Files.newInputStream(Path.of("src/main/resources/application.properties"))) {
   properties.load(input);
  }
  server = new Tomcat();
  server.setBaseDir(directory.toString());
  server.setPort(0);
  var connector = server.getConnector();
  connector.setMaxPartCount(Integer.parseInt(properties.getProperty("server.tomcat.max-part-count", "50")));
  connector.setMaxPartHeaderSize(Math.toIntExact(DataSize.parse(
    properties.getProperty("server.tomcat.max-part-header-size", "512B")).toBytes()));
  var context = server.addContext("", directory.toString());
  var servlet = Tomcat.addServlet(context, "upload", new HttpServlet() {
   @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    try {
     var parts = request.getParts();
     response.getWriter().print(parts.size());
    } catch (Exception exception) {
     response.setStatus(413);
    }
   }
  });
  servlet.setMultipartConfigElement(new MultipartConfigElement(directory.toString(),
    DataSize.parse(properties.getProperty("spring.servlet.multipart.max-file-size")).toBytes(),
    DataSize.parse(properties.getProperty("spring.servlet.multipart.max-request-size")).toBytes(), 0));
  context.addServletMappingDecoded("/upload", "upload");
  server.start();
  endpoint = URI.create("http://localhost:" + connector.getLocalPort() + "/upload");
 }
 @AfterEach void stop() throws Exception {
  if (server != null) { server.stop(); server.destroy(); }
 }
 private void part(ByteArrayOutputStream body, String name, String filename, byte[] content) throws IOException {
  String header = "--upload-test\r\nContent-Disposition: form-data; name=\"" + name + "\""
    + (filename == null ? "" : "; filename=\"" + filename + "\"\r\nContent-Type: image/png") + "\r\n\r\n";
  body.write(header.getBytes(StandardCharsets.UTF_8));
  body.write(content);
  body.write("\r\n".getBytes(StandardCharsets.UTF_8));
 }
 private HttpResponse<String> send(ByteArrayOutputStream body) throws Exception {
  body.write("--upload-test--\r\n".getBytes(StandardCharsets.UTF_8));
  var client = HttpClient.newHttpClient();
  return client.send(HttpRequest.newBuilder(endpoint)
     .header("Content-Type", "multipart/form-data; boundary=upload-test")
     .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
 }
 @Test void sixImagesWithLongVietnameseNamesAndManyFieldsAreAccepted() throws Exception {
  var body = new ByteArrayOutputStream();
  // Các ô chữ, ô ảnh trống và CSRF cũng được tính là một phần của multipart.
  for (int i = 0; i < 46; i++) part(body, "field" + i, null, "Nội dung".getBytes(StandardCharsets.UTF_8));
  for (int i = 0; i < 6; i++) part(body, "checkinFiles", "Ảnh cảnh đẹp Việt phục ".repeat(25) + i + ".png", new byte[1024 * 1024]);
  var response = send(body);
  assertEquals(200, response.statusCode());
  assertEquals("52", response.body());
 }
 @Test void oversizedImageIsStillRejected() throws Exception {
  var body = new ByteArrayOutputStream();
  part(body, "checkinFiles", "large.png", new byte[10 * 1024 * 1024 + 1]);
  assertEquals(413, send(body).statusCode());
 }
 @Test void sixShortNamedImagesNearTenMegabytesAreAccepted() throws Exception {
  var body = new ByteArrayOutputStream();
  // Tái hiện dung lượng người dùng: mỗi ảnh 9,5 MiB, tổng sáu ảnh 57 MiB.
  for (int i = 0; i < 30; i++) part(body, "field" + i, null, "Nội dung".getBytes(StandardCharsets.UTF_8));
  for (int i = 0; i < 5; i++) part(body, "imageFiles", "", new byte[0]);
  var image = new byte[9 * 1024 * 1024 + 512 * 1024];
  for (int i = 1; i <= 6; i++) part(body, "checkinFiles", "g" + i + ".png", image);
  var response = send(body);
  assertEquals(200, response.statusCode());
  assertEquals("41", response.body());
 }
}
