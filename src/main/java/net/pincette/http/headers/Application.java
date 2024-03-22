package net.pincette.http.headers;

import static com.typesafe.config.ConfigFactory.defaultApplication;
import static com.typesafe.config.ConfigFactory.defaultOverrides;
import static java.lang.Integer.parseInt;
import static java.lang.System.exit;
import static net.pincette.util.Util.initLogging;
import static net.pincette.util.Util.isInteger;

/**
 * @author Werner Donné
 */
public class Application {
  @SuppressWarnings("java:S106") // Not logging. Its just a CLI.
  public static void main(final String[] args) {
    if (args.length != 1 || !isInteger(args[0])) {
      System.err.println("Usage: net.pincette.http.headers.Application port");
      exit(1);
    }

    initLogging();
    new Server(parseInt(args[0]), defaultOverrides().withFallback(defaultApplication())).start();
  }
}
