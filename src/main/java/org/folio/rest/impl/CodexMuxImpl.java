package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.codex.Mock;
import org.folio.codex.Multiplexer;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.resource.CodexInstancesResource;

public class CodexMuxImpl implements CodexInstancesResource {

  private static CodexInstancesResource mux;
  private static Map<String, CodexInstancesResource> mock = new HashMap<>();
  Logger logger = LoggerFactory.getLogger("codex.mux");


  private CodexInstancesResource get(Map<String, String> okapiHeaders) {
    final String module = okapiHeaders.get(XOkapiHeaders.MODULE_ID.toLowerCase());
    logger.info("Impl get module = " + module);
    if (module == null) {
      if (mux == null) {
        mux = new Multiplexer();
      }
      return mux;
    } else {
      if (!mock.containsKey(module)) {
        mock.put(module, new Mock(module));
      }
      return mock.get(module);
    }
  }

  @Override
  public void getCodexInstances(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    get(okapiHeaders).getCodexInstances(query, offset, limit, lang, okapiHeaders, asyncResultHandler, vertxContext);
  }

  @Override
  public void getCodexInstancesById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    get(okapiHeaders).getCodexInstancesById(id, lang, okapiHeaders, asyncResultHandler, vertxContext);
  }

}
