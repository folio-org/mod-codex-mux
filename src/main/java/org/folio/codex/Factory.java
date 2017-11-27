package org.folio.codex;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import org.folio.rest.jaxrs.resource.CodexInstancesResource;

public class Factory {

  public static CodexInstancesResource create() {
    Logger logger = LoggerFactory.getLogger("codex.mux");

    String mode = System.getProperty("codex.mode");
    logger.info("mode = " + mode);
    if (null == mode) {
      return new Multiplexer();
    } else {
      switch (mode) {
        case "mux":
          return new Multiplexer();
        case "mock":
          return new Mock();
        default:
          throw new UnsupportedOperationException("Unsupported mode: " + mode);
      }
    }
  }
}
