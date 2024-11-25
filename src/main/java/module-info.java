module application {
  requires net.pincette.http.headers.plugin;
  requires net.pincette.common;
  requires typesafe.config;
  requires java.net.http;
  requires net.pincette.rs;
  requires io.netty.buffer;
  requires io.netty.codec.http;
  requires net.pincette.netty.http;
  requires java.logging;
  requires jdk.unsupported; // For the MongoDB driver.
  requires net.pincette.config;

  uses net.pincette.http.headers.plugin.Plugin;

  opens net.pincette.http.headers;

  exports net.pincette.http.headers;
}
