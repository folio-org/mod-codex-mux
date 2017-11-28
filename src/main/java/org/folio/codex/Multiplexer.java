package org.folio.codex;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.InstanceCollection;
import org.folio.rest.jaxrs.resource.CodexInstancesResource;

public class Multiplexer implements CodexInstancesResource {

  @Override
  public void getCodexInstances(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    InstanceCollection coll = new InstanceCollection();
    coll.setTotalRecords(0);
    asyncResultHandler.handle(Future.succeededFuture(CodexInstancesResource.GetCodexInstancesResponse.withJsonOK(coll)));
  }

  @Override
  public void getCodexInstancesById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    asyncResultHandler.handle(Future.succeededFuture(CodexInstancesResource.GetCodexInstancesByIdResponse.withPlainNotFound(id)));
  }

}
