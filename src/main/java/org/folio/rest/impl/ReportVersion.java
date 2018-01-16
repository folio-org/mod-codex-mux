package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.okapi.common.ModuleVersionReporter;
import org.folio.rest.resource.interfaces.InitAPI;

public class ReportVersion implements InitAPI {
  public void init(Vertx vertx, Context context, Handler<AsyncResult<Boolean>> resultHandler) {
    try {
      ModuleVersionReporter m = new ModuleVersionReporter("org.folio/mod-codex-mux", "git2.properties");
      m.logStart();

      resultHandler.handle(io.vertx.core.Future.succeededFuture(true));
    } catch (Exception e) {
      resultHandler.handle(io.vertx.core.Future.failedFuture(e.getMessage()));
    }
  }
}
