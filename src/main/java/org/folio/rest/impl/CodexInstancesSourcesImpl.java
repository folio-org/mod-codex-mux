package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;

import org.folio.rest.jaxrs.resource.CodexInstancesSources;

public class CodexInstancesSourcesImpl implements CodexInstancesSources {
  @Override
  public void getCodexInstancesSources(String lang, Map<String, String> okapiHeaders,
                                       Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    asyncResultHandler.handle(Future.succeededFuture(
      Response.status(Response.Status.NOT_IMPLEMENTED).build()));
  }
}
