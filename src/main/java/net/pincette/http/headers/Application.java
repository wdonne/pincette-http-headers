package net.pincette.http.headers;

import static com.typesafe.config.ConfigFactory.defaultApplication;
import static com.typesafe.config.ConfigFactory.defaultOverrides;
import static java.lang.Integer.parseInt;
import static java.lang.System.exit;
import static java.util.logging.Logger.getLogger;
import static net.pincette.util.Util.initLogging;
import static net.pincette.util.Util.isInteger;

import java.util.logging.Logger;

/**
 * @author Werner Donné
 */
public class Application {
  static final Logger LOGGER = getLogger("net.pincette.http.headers");
  private static final String VERSION = "1.1.0";

  @SuppressWarnings("java:S106") // Not logging. Its just a CLI.
  public static void main(final String[] args) {
    if (args.length != 1 || !isInteger(args[0])) {
      System.err.println("Usage: net.pincette.http.headers.Application port");
      exit(1);
    }

    initLogging();
    LOGGER.info(() -> "Version " + VERSION);
    new Server(parseInt(args[0]), defaultOverrides().withFallback(defaultApplication())).start();
  }
}
