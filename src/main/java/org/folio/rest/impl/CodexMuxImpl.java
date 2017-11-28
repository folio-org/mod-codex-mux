package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.codex.Mock;
import org.folio.codex.Multiplexer;
import org.folio.rest.jaxrs.resource.CodexInstancesResource;

public class CodexMuxImpl implements CodexInstancesResource {

  private static CodexInstancesResource mux;
  private static CodexInstancesResource mock;

  private CodexInstancesResource get(Context vertxContext) {
    JsonObject conf = vertxContext.config();
    String mode = conf.getString("codex.mode");
    if (mode == null || mode.equals("mux")) {
      if (mux == null) {
        mux = new Multiplexer();
      }
      return mux;
    } else if (mode.equals("mock")) {
      if (mock == null) {
        mock = new Mock();
      }
      return mock;
    } else {
      return null;
    }
  }

  @Override
  public void getCodexInstances(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    get(vertxContext).getCodexInstances(query, offset, limit, lang, okapiHeaders, asyncResultHandler, vertxContext);
  }

  @Override
  public void getCodexInstancesById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    get(vertxContext).getCodexInstancesById(id, lang, okapiHeaders, asyncResultHandler, vertxContext);
  }

}
