package net.pincette.http.headers.test;

import static java.net.http.HttpHeaders.of;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static net.pincette.util.Collections.list;
import static net.pincette.util.Collections.map;
import static net.pincette.util.Collections.merge;
import static net.pincette.util.Pair.pair;

import java.net.http.HttpHeaders;
import java.util.concurrent.CompletionStage;
import java.util.function.BiPredicate;
import net.pincette.http.headers.plugin.Plugin;
import net.pincette.http.headers.plugin.RequestResult;
import net.pincette.http.headers.plugin.Response;

public class HeadersPlugin implements Plugin {
  private static final BiPredicate<String, String> ALL = (k, v) -> true;
  private static final String RESULT_HEADER = "X-Result";
  private static final String TEST_HEADER = "X-TestCase";

  public CompletionStage<RequestResult> request(final HttpHeaders headers) {
    return switch (headers.map().get(TEST_HEADER).get(0)) {
      case "test1" ->
          completedFuture(
              new RequestResult()
                  .withRequest(of(merge(headers.map(), map(pair("test1", list("value1")))), ALL)));
      case "test2" ->
          completedFuture(
              new RequestResult()
                  .withResponse(
                      new Response()
                          .withHeaders(of(map(pair(RESULT_HEADER, list("bad"))), ALL))
                          .withStatusCode(400)));
      case "test3" ->
          completedFuture(
              new RequestResult()
                  .withResponseWrapper(
                      h ->
                          completedFuture(
                              of(
                                  merge(
                                      h.map(),
                                      map(pair(RESULT_HEADER, headers.map().get("test3")))),
                                  ALL))));
      default -> completedFuture(new RequestResult());
    };
  }

  public CompletionStage<HttpHeaders> response(final HttpHeaders headers) {
    return completedFuture(of(merge(headers.map(), map(pair(RESULT_HEADER, list("value")))), ALL));
  }
}
