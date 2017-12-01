package org.folio.codex;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstanceCollection;
import org.folio.rest.jaxrs.resource.CodexInstancesResource;

public class Multiplexer implements CodexInstancesResource {
  Logger logger = LoggerFactory.getLogger("codex.mux");

  void getModules(LHeaders okapiHeaders, Context vertxContext, Handler<AsyncResult<List<String>>> fut) {
    logger.info("codex.mux getModules");
    final String okapiUrl = okapiHeaders.get(XOkapiHeaders.URL);
    if (okapiUrl == null) {
      fut.handle(Future.failedFuture("missing " + XOkapiHeaders.URL));
      return;
    }
    final String tenant = okapiHeaders.get(XOkapiHeaders.TENANT);

    HttpClient client = vertxContext.owner().createHttpClient();
    final String absUrl = okapiUrl + "/_/proxy/tenants/" + tenant + "/interfaces/codex";
    HttpClientRequest req = client.getAbs(absUrl, res -> {
      if (res.statusCode() != 200) {
        client.close();
        fut.handle(Future.failedFuture("Get " + absUrl + " returned status " + res.statusCode()));
        return;
      }
      Buffer b = Buffer.buffer();
      res.handler(b::appendBuffer);
      res.endHandler(r -> {
        logger.info("codex.mux getModules got " + b.toString());
        List<String> l = new LinkedList<>();

        JsonArray a = b.toJsonArray();
        for (int i = 0; i < a.size(); i++) {
          JsonObject j = a.getJsonObject(i);
          l.add(j.getString("id"));
        }
        client.close();
        fut.handle(Future.succeededFuture(l));
      });
    });
    for (Map.Entry<String, String> e : okapiHeaders.entrySet()) {
      req.putHeader(e.getKey(), e.getValue());
    }
    req.exceptionHandler(r -> {
      fut.handle(Future.failedFuture("Get " + absUrl + " returned exception " + r.getMessage()));
      client.close();
    });
    req.end();
  }


  @Override
  public void getCodexInstances(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    LHeaders h = new LHeaders(okapiHeaders);
    getModules(h, vertxContext, res -> {
      if (res.failed()) {
        asyncResultHandler.handle(Future.succeededFuture(
          CodexInstancesResource.GetCodexInstancesResponse.withPlainUnauthorized(res.cause().getMessage())));
      } else {
        InstanceCollection coll = new InstanceCollection();
        coll.setTotalRecords(0);
        asyncResultHandler.handle(Future.succeededFuture(CodexInstancesResource.GetCodexInstancesResponse.withJsonOK(coll)));
      }
    });
  }

  private void getById(String id, String module, Context vertxContext, LHeaders okapiHeaders,
    List<Instance> instances, Handler<AsyncResult<Void>> fut) {
    HttpClient client = vertxContext.owner().createHttpClient();
    final String url = okapiHeaders.get(XOkapiHeaders.URL) + "/codex-instances/" + id;
    logger.info("getById url=" + url);
    HttpClientRequest req = client.getAbs(url, res -> {
      Buffer b = Buffer.buffer();
      res.handler(r -> {
        b.appendBuffer(r);
        logger.info("getById buf=" + r.toString());
      });
      res.endHandler(r -> {
        if (res.statusCode() == 200) {
          logger.info("getById returned status 200");
          logger.info("Response: " + b.toString());
          try {
            Instance instance = Json.decodeValue(b.toString(), Instance.class);
            instances.add(instance);
          } catch (Exception e) {
            client.close();
            fut.handle(Future.failedFuture(e));
            return;
          }
        }
        client.close();
        fut.handle(Future.succeededFuture());
      });
    });
    req.setChunked(true);
    logger.info("-----------------------");
    for (Map.Entry<String, String> e : okapiHeaders.entrySet()) {
      logger.info("h " + e.getKey() + "=" + e.getValue());
      req.putHeader(e.getKey(), e.getValue());
    }
    req.putHeader(XOkapiHeaders.MODULE_ID, module);
    req.putHeader("Accept", "application/json");
    req.exceptionHandler(r -> {
      client.close();
      fut.handle(Future.failedFuture(r.getMessage()));
    });
    req.end();

  }

  @Override
  public void getCodexInstancesById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    logger.info("Codex.mux getCodexInstancesById");
    LHeaders h = new LHeaders(okapiHeaders);
    getModules(h, vertxContext, res1 -> {
      if (res1.failed()) {
        asyncResultHandler.handle(Future.succeededFuture(
          CodexInstancesResource.GetCodexInstancesByIdResponse.withPlainUnauthorized(res1.cause().getMessage())));
      } else {
        List<Instance> instances = new LinkedList<>();
        List<Future> futures = new LinkedList<>();
        for (String m : res1.result()) {
          logger.info("Calling module " + m);
          Future fut = Future.future();
          getById(id, m, vertxContext, h, instances, fut);
          futures.add(fut);
        }
        CompositeFuture.all(futures).setHandler(res2 -> {
          if (res2.failed()) {
            asyncResultHandler.handle(Future.succeededFuture(
              CodexInstancesResource.GetCodexInstancesByIdResponse.withPlainInternalServerError(res2.cause().getMessage()))
          );
          } else {
            if (instances.isEmpty()) {
              asyncResultHandler.handle(Future.succeededFuture(
                CodexInstancesResource.GetCodexInstancesByIdResponse.withPlainNotFound(id)));
            } else {
              asyncResultHandler.handle(Future.succeededFuture(
                CodexInstancesResource.GetCodexInstancesByIdResponse.withJsonOK(instances.get(0))));
            }
          }
        });
      }
    });
  }

}
