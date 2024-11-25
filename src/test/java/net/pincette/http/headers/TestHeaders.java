package net.pincette.http.headers;

import static com.typesafe.config.ConfigValueFactory.fromAnyRef;
import static com.typesafe.config.ConfigValueFactory.fromIterable;
import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static java.net.http.HttpClient.newBuilder;
import static java.net.http.HttpHeaders.of;
import static java.net.http.HttpRequest.BodyPublishers.noBody;
import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static java.nio.charset.StandardCharsets.UTF_8;
import static net.pincette.netty.http.Util.simpleResponse;
import static net.pincette.util.Collections.list;
import static net.pincette.util.Collections.map;
import static net.pincette.util.Pair.pair;
import static net.pincette.util.Util.tryToGetRethrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.util.function.BiPredicate;
import net.pincette.netty.http.HttpServer;
import net.pincette.netty.http.RequestHandler;
import net.pincette.rs.Source;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TestHeaders {
  private static final BiPredicate<String, String> ALL = (k, v) -> true;
  private static final String CONTENT_TYPE_HEADER = "Content-Type";
  private static final String PATH_HEADER = "X-Path";
  private static final String RESULT_HEADER = "X-Result";
  private static final String RESULT_HEADER_2 = "X-Result2";
  private static final String SERVER_HEADER = "X-Server";
  private static final String TEST_HEADER = "X-TestCase";
  private static final HttpClient client = getClient();
  private static final Server headers1 = new Server(9000, createConfigForward());
  private static final Server headers2 = new Server(9001, createConfigRoutes());
  private static final HttpServer server1 = new HttpServer(9002, requestHandler("server1"));
  private static final HttpServer server2 = new HttpServer(9003, requestHandler("server2"));

  private static HttpResponse addContentTypeHeader(
      final HttpResponse response, final String mimeType) {
    response.headers().set(CONTENT_TYPE_HEADER, mimeType);

    return response;
  }

  private static HttpResponse addHeader(
      final HttpResponse response, final String name, final String value) {
    response.headers().set(name, value);

    return response;
  }

  private static HttpResponse addPathHeader(final HttpResponse response, final String path) {
    return addHeader(response, PATH_HEADER, path);
  }

  private static HttpResponse addServerHeader(final HttpResponse response, final String name) {
    return addHeader(response, SERVER_HEADER, name);
  }

  @AfterAll
  static void after() {
    headers1.close();
    headers2.close();
    server1.close();
    server2.close();
  }

  @BeforeAll
  static void before() {
    server1.run();
    server2.run();
    headers1.run();
    headers2.run();
  }

  private static HttpResponse copyTestHeaders(
      final HttpRequest request, final HttpResponse response) {
    request.headers().entries().stream()
        .filter(e -> e.getKey().startsWith("test"))
        .forEach(e -> response.headers().set(e.getKey(), e.getValue()));

    return response;
  }

  private static Config createConfigForward() {
    return ConfigFactory.empty()
        .withValue("plugins", fromAnyRef("test-plugin/target/plugin"))
        .withValue("forwardTo", fromAnyRef("http://localhost:9002"));
  }

  private static Config createConfigRoutes() {
    return ConfigFactory.empty()
        .withValue("plugins", fromAnyRef("test-plugin/target/plugin"))
        .withValue(
            "routes",
            fromIterable(
                list(
                    map(pair("pathPrefix", "/path1"), pair("endPoint", "http://localhost:9002")),
                    map(pair("pathPrefix", "/path2"), pair("endPoint", "http://localhost:9003")))));
  }

  private static HttpClient getClient() {
    return newBuilder().version(Version.HTTP_1_1).followRedirects(Redirect.NORMAL).build();
  }

  private static java.net.http.HttpResponse<String> request(
      final HttpHeaders headers, final String path, final int port) {
    return tryToGetRethrow(
            () ->
                client.send(
                    setHeaders(
                            java.net.http.HttpRequest.newBuilder()
                                .uri(new URI("http://localhost:" + port + path))
                                .method("GET", noBody()),
                            headers)
                        .build(),
                    ofString(UTF_8)))
        .orElse(null);
  }

  private static java.net.http.HttpResponse<String> requestForward(
      final HttpHeaders headers, final String path) {
    return request(headers, path, 9000);
  }

  private static RequestHandler requestHandler(final String server) {
    return (request, requestBody, response) ->
        simpleResponse(
            copyTestHeaders(
                request,
                addServerHeader(
                    addContentTypeHeader(addPathHeader(response, request.uri()), "text" + "/plain"),
                    server)),
            OK,
            Source.of(copiedBuffer("test", UTF_8)));
  }

  private static java.net.http.HttpResponse<String> requestRoute(
      final HttpHeaders headers, final String path) {
    return request(headers, path, 9001);
  }

  private static java.net.http.HttpRequest.Builder setHeaders(
      final java.net.http.HttpRequest.Builder builder, final HttpHeaders headers) {
    return headers.map().entrySet().stream()
        .flatMap(e -> e.getValue().stream().map(v -> pair(e.getKey(), v)))
        .reduce(builder, (b, p) -> b.setHeader(p.first, p.second), (b1, b2) -> b1);
  }

  @Test
  @DisplayName("test1")
  void test1() {
    list("/", "/path")
        .forEach(
            p -> {
              final java.net.http.HttpResponse<String> response =
                  requestForward(of(map(pair(TEST_HEADER, list("test1"))), ALL), p);

              assertEquals("value1", response.headers().map().get("test1").get(0));
              assertEquals(p, response.headers().map().get(PATH_HEADER).get(0));
              assertEquals("value", response.headers().map().get(RESULT_HEADER_2).get(0));
              assertEquals("test", response.body());
            });
  }

  @Test
  @DisplayName("test2")
  void test2() {
    final java.net.http.HttpResponse<String> response =
        requestForward(of(map(pair(TEST_HEADER, list("test2"))), ALL), "/");

    assertEquals("bad", response.headers().map().get(RESULT_HEADER).get(0));
    assertEquals(400, response.statusCode());
  }

  @Test
  @DisplayName("test3")
  void test3() {
    list("/", "/path")
        .forEach(
            p -> {
              final java.net.http.HttpResponse<String> response =
                  requestForward(
                      of(map(pair(TEST_HEADER, list("test3")), pair("test3", list("value3"))), ALL),
                      p);

              assertEquals("value3", response.headers().map().get("test3").get(0));
              assertEquals("value3", response.headers().map().get(RESULT_HEADER).get(0));
              assertEquals("value", response.headers().map().get(RESULT_HEADER_2).get(0));
              assertEquals(p, response.headers().map().get(PATH_HEADER).get(0));
              assertEquals("test", response.body());
            });
  }

  @Test
  @DisplayName("test4")
  void test4() {
    list(pair("/path1", "server1"), pair("/path2", "server2"))
        .forEach(
            p -> {
              final java.net.http.HttpResponse<String> response =
                  requestRoute(of(map(pair(TEST_HEADER, list("test1"))), ALL), p.first);

              assertEquals("value1", response.headers().map().get("test1").get(0));
              assertEquals(p.first, response.headers().map().get(PATH_HEADER).get(0));
              assertEquals("value", response.headers().map().get(RESULT_HEADER_2).get(0));
              assertEquals(p.second, response.headers().map().get(SERVER_HEADER).get(0));
              assertEquals("test", response.body());
            });
  }
}
