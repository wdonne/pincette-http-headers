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

public class ImHerePlugin implements Plugin {
  private static final BiPredicate<String, String> ALL = (k, v) -> true;
  private static final String RESULT_HEADER = "X-Result2";

  public CompletionStage<RequestResult> request(final HttpHeaders headers) {
    return completedFuture(new RequestResult().withRequest(headers));
  }

  public CompletionStage<HttpHeaders> response(final HttpHeaders headers) {
    return completedFuture(of(merge(headers.map(), map(pair(RESULT_HEADER, list("value")))), ALL));
  }
}
