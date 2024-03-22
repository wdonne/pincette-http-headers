module net.pincette.http.headers.test {
  requires net.pincette.http.headers.plugin;
  requires java.net.http;
  requires net.pincette.common;

  provides net.pincette.http.headers.plugin.Plugin with
      net.pincette.http.headers.test.HeadersPlugin;
}
