# HTTP Headers

This HTTP server can be placed in front of a real HTTP server, to which it forwards all requests. The request and response headers may be manipulated by chained plugins, which are Java 9 modules that implement the [plugin interface](https://www.javadoc.io/doc/net.pincette/pincette-http-headers-plugin/latest/net.pincette.http.headers.plugin/module-summary.html). The plugins are picked up from subfolders of a configured folder.

## Configuration

The configuration is managed by the [Lightbend Config package](https://github.com/lightbend/config). By default it will try to load `conf/application.conf`. An alternative configuration may be loaded by adding `-Dconfig.resource=myconfig.conf`, where the file is also supposed to be in the `conf` directory, or `-Dconfig.file=/conf/myconfig.conf`. If no configuration file is available it will load a default one from the resources. The following entries are available:

|Entry|Mandatory|Description|
|---|---|---|
|forwardTo|No|The URL to which all requests are forwarded with the same path, query and fragment. If the field is not provided and there is not forwarding plugin, then the status code 501 is returned.|
|plugins|Yes|The folder in which the plugins are placed. Each subfolder will be loaded as a Java 9 module in its own module layer.|

The configuration is also available to the plugins because they can load it from the same place. Of course, they could load whatever they want.

## Building and Running

First you should go to the `test-plugin` subfolder and run `mvn clean package`. This is for the unit tests. Then, in the parent folder, you can build the tool with `mvn clean package`. You can launch it with `java --module-path target/modules --module application 9000`, followed by a port number.

## Docker

Docker images can be found at [https://hub.docker.com/repository/docker/wdonne/pincette-http-headers](https://hub.docker.com/repository/docker/wdonne/pincette-http-headers). They expose port 9000.

## Kubernetes

You can mount the configuration in a `ConfigMap` and `Secret` combination. The `ConfigMap` should be mounted at `/conf/application.conf`. You then include the secret in the configuration from where you have mounted it. See also [https://github.com/lightbend/config/blob/main/HOCON.md#include-syntax](https://github.com/lightbend/config/blob/main/HOCON.md#include-syntax).
