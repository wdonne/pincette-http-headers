package net.pincette.http.headers;

import static com.typesafe.config.ConfigValueFactory.fromAnyRef;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static java.net.http.HttpClient.newBuilder;
import static java.net.http.HttpHeaders.of;
import static java.net.http.HttpRequest.BodyPublishers.noBody;
import static java.net.http.HttpResponse.BodyHandlers.discarding;
import static net.pincette.netty.http.Util.simpleResponse;
import static net.pincette.rs.Util.empty;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TestHeaders {
  private static final BiPredicate<String, String> ALL = (k, v) -> true;
  private static final String PATH_HEADER = "X-Path";
  private static final String RESULT_HEADER = "X-Result";
  private static final String TEST_HEADER = "X-TestCase";
  private static final HttpClient client = getClient();
  private static final Server headers = new Server(9001, createConfig());
  private static final HttpServer server = new HttpServer(9000, requestHandler());

  private static HttpResponse addPathHeader(final HttpResponse response, final String path) {
    response.headers().set(PATH_HEADER, path);

    return response;
  }

  @AfterAll
  static void after() {
    headers.close();
    server.close();
  }

  @BeforeAll
  static void before() {
    server.run();
    headers.run();
  }

  private static HttpResponse copyTestHeaders(
      final HttpRequest request, final HttpResponse response) {
    request.headers().entries().stream()
        .filter(e -> e.getKey().startsWith("test"))
        .forEach(e -> response.headers().set(e.getKey(), e.getValue()));

    return response;
  }

  private static Config createConfig() {
    return ConfigFactory.empty()
        .withValue("plugins", fromAnyRef("test-plugin/target/plugin"))
        .withValue("forwardTo", fromAnyRef("http://localhost:9000"));
  }

  private static HttpClient getClient() {
    return newBuilder().version(Version.HTTP_1_1).followRedirects(Redirect.NORMAL).build();
  }

  private static java.net.http.HttpResponse<?> request(
      final HttpHeaders headers, final String path) {
    return tryToGetRethrow(
            () ->
                client.send(
                    setHeaders(
                            java.net.http.HttpRequest.newBuilder()
                                .uri(new URI("http://localhost:9001" + path))
                                .method("HEAD", noBody()),
                            headers)
                        .build(),
                    discarding()))
        .orElse(null);
  }

  private static RequestHandler requestHandler() {
    return (request, requestBody, response) ->
        simpleResponse(
            copyTestHeaders(request, addPathHeader(response, request.uri())), OK, empty());
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
              final HttpHeaders headers =
                  request(of(map(pair(TEST_HEADER, list("test1"))), ALL), p).headers();

              System.out.println(headers);
              assertEquals("value1", headers.map().get("test1").get(0));
              assertEquals(p, headers.map().get(PATH_HEADER).get(0));
            });
  }

  @Test
  @DisplayName("test2")
  void test2() {
    final java.net.http.HttpResponse<?> response =
        request(of(map(pair(TEST_HEADER, list("test2"))), ALL), "/");

    assertEquals("bad", response.headers().map().get(RESULT_HEADER).get(0));
    assertEquals(400, response.statusCode());
  }

  @Test
  @DisplayName("test3")
  void test3() {
    list("/", "/path")
        .forEach(
            p -> {
              final HttpHeaders headers =
                  request(
                          of(
                              map(pair(TEST_HEADER, list("test3")), pair("test3", list("value3"))),
                              ALL),
                          p)
                      .headers();

              assertEquals("value3", headers.map().get("test3").get(0));
              assertEquals("value3", headers.map().get(RESULT_HEADER).get(0));
              assertEquals(p, headers.map().get(PATH_HEADER).get(0));
            });
  }
}
