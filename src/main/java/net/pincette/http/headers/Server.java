package net.pincette.http.headers;

import static java.net.http.HttpClient.newBuilder;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static net.pincette.netty.http.Util.wrapTracing;
import static net.pincette.rs.Util.empty;
import static net.pincette.util.Collections.map;
import static net.pincette.util.Collections.reverse;
import static net.pincette.util.Or.tryWith;
import static net.pincette.util.Pair.pair;
import static net.pincette.util.Plugins.loadPlugins;
import static net.pincette.util.StreamUtil.stream;
import static net.pincette.util.Util.tryToGetRethrow;
import static net.pincette.util.Util.tryToGetSilent;

import com.typesafe.config.Config;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow.Publisher;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Stream;
import net.pincette.http.headers.plugin.Plugin;
import net.pincette.http.headers.plugin.RequestResult;
import net.pincette.netty.http.Forwarder;
import net.pincette.netty.http.HttpServer;
import net.pincette.netty.http.RequestHandler;

/**
 * @author Werner Donné
 */
public class Server {
  private static final String FORWARD_TO = "forwardTo";
  private static final Logger LOGGER = getLogger("net.pincette.http.headers");
  private static final String PLUGINS = "plugins";

  private final HttpServer httpServer;

  public Server(final int port, final Config config) {
    httpServer = new HttpServer(port, wrapTracing(handler(config, getClient()), LOGGER));
  }

  private static java.net.http.HttpHeaders convertHeaders(
      final io.netty.handler.codec.http.HttpHeaders headers) {
    return java.net.http.HttpHeaders.of(
        map(headers.names().stream().map(n -> pair(n, headers.getAll(n)))), (k, v) -> true);
  }

  private static RequestHandler devNull() {
    return (request, requestBody, response) -> completedFuture(empty());
  }

  private static HttpClient getClient() {
    return newBuilder().version(Version.HTTP_1_1).followRedirects(Redirect.NORMAL).build();
  }

  private static RequestHandler forwarder(final Config config, final HttpClient client) {
    return tryToGetSilent(() -> config.getString(FORWARD_TO))
        .flatMap(uri -> tryToGetRethrow(() -> new URI(uri)))
        .map(uri -> Forwarder.forwarder(uri, client))
        .orElseGet(Server::devNull);
  }

  private static RequestHandler handler(final Config config, final HttpClient client) {
    final List<Plugin> plugins = plugins(config).toList();

    plugins.forEach(p -> LOGGER.info(() -> "Loaded plugin " + p));

    if (plugins.isEmpty()) {
      LOGGER.log(WARNING, "No plugins are loaded.");
    }

    return stream(reverse(plugins))
        .reduce(forwarder(config, client), Server::handler, (n1, n2) -> n1);
  }

  private static RequestHandler handler(final RequestHandler next, final Plugin plugin) {
    return (request, requestBody, response) ->
        plugin
            .request(headers(request))
            .thenComposeAsync(
                result ->
                    tryWith(
                            () ->
                                ofNullable(result.request)
                                    .map(
                                        req ->
                                            next.apply(
                                                setHeaders(request, req), requestBody, response)))
                        .or(() -> returnImmediately(result, response))
                        .get()
                        .orElseGet(() -> next.apply(request, requestBody, response))
                        .thenComposeAsync(
                            body ->
                                result.response == null
                                    ? response(response, plugin, result.responseWrapper)
                                        .thenApply(r -> body)
                                    : completedFuture(body)));
  }

  private static java.net.http.HttpHeaders headers(final HttpRequest request) {
    return convertHeaders(request.headers());
  }

  private static Stream<Plugin> plugins(final Config config) {
    return tryToGetSilent(() -> config.getString(PLUGINS))
        .map(Paths::get)
        .map(directory -> loadPlugins(directory, layer -> ServiceLoader.load(layer, Plugin.class)))
        .orElseGet(Stream::empty);
  }

  private static CompletionStage<HttpResponse> response(
      final HttpResponse target,
      final Plugin plugin,
      final Function<java.net.http.HttpHeaders, CompletionStage<java.net.http.HttpHeaders>>
          responseWrapper) {
    return plugin
        .response(convertHeaders(target.headers()))
        .thenComposeAsync(
            h -> responseWrapper != null ? responseWrapper.apply(h) : completedFuture(h))
        .thenApply(h -> setHeaders(target, h));
  }

  private static Optional<CompletionStage<Publisher<ByteBuf>>> returnImmediately(
      final RequestResult result, final HttpResponse response) {
    return ofNullable(result.response)
        .map(res -> setResponse(response, res.headers, res.statusCode))
        .map(h -> completedFuture(empty()));
  }

  private static HttpRequest setHeaders(
      final HttpRequest request, final java.net.http.HttpHeaders headers) {
    setHeaders(request.headers(), headers);

    return request;
  }

  private static void setHeaders(final HttpHeaders target, final java.net.http.HttpHeaders source) {
    target.clear();
    source.map().forEach(target::set);
  }

  private static HttpResponse setHeaders(
      final HttpResponse response, final java.net.http.HttpHeaders headers) {
    setHeaders(response.headers(), headers);

    return response;
  }

  private static HttpResponse setResponse(
      final HttpResponse response, final java.net.http.HttpHeaders headers, final int statusCode) {
    setHeaders(response, headers);

    if (statusCode != -1) {
      response.setStatus(HttpResponseStatus.valueOf(statusCode));
    }

    return response;
  }

  public void close() {
    httpServer.close();
  }

  public CompletionStage<Boolean> run() {
    return httpServer.run();
  }

  public void start() {
    httpServer.start();
  }
}
