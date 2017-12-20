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
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstanceCollection;
import org.folio.rest.jaxrs.resource.CodexInstancesResource;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLParseException;
import org.z3950.zing.cql.CQLParser;
import org.z3950.zing.cql.CQLSortNode;

public class Multiplexer implements CodexInstancesResource {

  private static Logger logger = LoggerFactory.getLogger("codex.mux");

  void getModules(LHeaders okapiHeaders, Context vertxContext,
    Handler<AsyncResult<List<String>>> fut) {

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
          String m = j.getString("id");
          if (!m.startsWith("mod-codex-mux")) { // avoid returning self
            l.add(m);
          }
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

  private void getUrl(String module, String url, HttpClient client, LHeaders okapiHeaders, Handler<AsyncResult<Buffer>> fut) {
    HttpClientRequest req = client.getAbs(url, res -> {
      Buffer b = Buffer.buffer();
      res.handler(b::appendBuffer);
      res.endHandler(r -> {
        client.close();
        if (res.statusCode() == 200) {
          fut.handle(Future.succeededFuture(b));
        } else if (res.statusCode() == 404) {
          fut.handle(Future.succeededFuture(Buffer.buffer())); // empty buffer
        } else {
          fut.handle(Future.failedFuture("Get url " + url + " returned " + res.statusCode()));
        }
      });
    });
    req.setChunked(true);
    for (Map.Entry<String, String> e : okapiHeaders.entrySet()) {
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

  private void getByQuery(String module, Context vertxContext, String query,
    int offset, int limit, LHeaders okapiHeaders, Map<String, InstanceCollection> cols,
    Handler<AsyncResult<Void>> fut) {

    HttpClient client = vertxContext.owner().createHttpClient();
    String url = okapiHeaders.get(XOkapiHeaders.URL) + "/codex-instances?"
      + "offset=" + offset + "&limit=" + limit;
    try {
      if (query != null) {
        url += "&query=" + URLEncoder.encode(query, "UTF-8");
      }
    } catch (UnsupportedEncodingException ex) {
      fut.handle(Future.failedFuture(ex.getMessage()));
      return;
    }
    logger.info("getByQuery url=" + url);
    getUrl(module, url, client, okapiHeaders, res -> {
      if (res.failed()) {
        logger.warn("getByQuery. getUrl failed " + res.cause());
        fut.handle(Future.failedFuture(res.cause()));
      } else {
        Buffer b = res.result();
        if (b.length() > 0) {
          InstanceCollection col = null;
          try {
            col = Json.decodeValue(b.toString(), InstanceCollection.class);
            cols.put(module, col);
          } catch (Exception e) {
            fut.handle(Future.failedFuture(e));
            return;
          }
        }
        fut.handle(Future.succeededFuture());
      }
    });
  }

  private void mergeSort(List<String> modules, String query, int offset, int limit, Comparator<Instance> comp,
    LHeaders h, Context vertxContext, Handler<AsyncResult<InstanceCollection>> handler) {

    List<Future> futures = new LinkedList<>();
    Map<String, InstanceCollection> cols = new LinkedHashMap<>();
    for (String m : modules) {
      Future fut = Future.future();
      getByQuery(m, vertxContext, query, 0, offset + limit, h, cols, fut);
      futures.add(fut);
    }
    CompositeFuture.all(futures).setHandler(res2 -> {
      if (res2.failed()) {
        handler.handle(Future.failedFuture(res2.cause()));
      } else {
        InstanceCollection colR = mergeSet2(cols, offset, limit, comp);
        handler.handle(Future.succeededFuture(colR));
      }
    });
  }

  private InstanceCollection mergeSet2(Map<String, InstanceCollection> cols,
    int offset, int limit, Comparator<Instance> comp) {

    int[] ptrs = new int[cols.size()]; // all 0
    int totalRecords = 0;
    for (InstanceCollection col : cols.values()) {
      totalRecords += col.getTotalRecords();
    }
    InstanceCollection colR = new InstanceCollection();
    colR.setTotalRecords(totalRecords);
    int gOffset = 0;
    while (gOffset < offset + limit) {
      Instance minInstance = null;
      int minI = -1;
      int i = 0;
      for (InstanceCollection col : cols.values()) {
        int idx = ptrs[i];
        List<Instance> instances = col.getInstances();
        if (idx < instances.size()) {
          if (comp == null) { // round-robin
            if (minI == -1 || ptrs[minI] > ptrs[i]) {
              Instance instance = instances.get(idx);
              minI = i;
              minInstance = instance;
            }
          } else {
            Instance instance = instances.get(idx);
            if (minInstance == null
              || comp.compare(minInstance, instance) > 0) {
              minI = i;
              minInstance = instance;
            }
          }
        }
        i++;
      }
      if (minI == -1) {
        break;
      } else {
        ptrs[minI]++;
        if (gOffset >= offset) {
          colR.getInstances().add(minInstance);
        }
        gOffset++;
      }
    }
    return colR;
  }

  @Override
  public void getCodexInstances(String query, int offset, int limit, String lang,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> handler,
    Context vertxContext) throws Exception {

    LHeaders h = new LHeaders(okapiHeaders);
    getModules(h, vertxContext, res -> {
      if (res.failed()) {
        handler.handle(Future.succeededFuture(
          CodexInstancesResource.GetCodexInstancesResponse.withPlainUnauthorized(res.cause().getMessage())));
      } else {
        Comparator<Instance> comp = null;
        if (query != null) {
          CQLParser parser = new CQLParser(CQLParser.V1POINT2);
          CQLNode top = null;
          try {
            top = parser.parse(query);
          } catch (CQLParseException ex) {
            logger.warn("CQLParseException: " + ex.getMessage());
            handler.handle(
              Future.succeededFuture(CodexInstancesResource.GetCodexInstancesResponse.withPlainBadRequest(ex.getMessage())));
            return;
          } catch (IOException ex) {
            handler.handle(
              Future.succeededFuture(CodexInstancesResource.GetCodexInstancesResponse.withPlainInternalServerError(ex.getMessage())));
            return;
          }
          CQLSortNode sn = CQLInspect.getSort(top);
          if (sn != null) {
            try {
              comp = InstanceComparator.get(sn);
            } catch (IllegalArgumentException ex) {
              handler.handle(
                Future.succeededFuture(
                  CodexInstancesResource.GetCodexInstancesResponse.withPlainBadRequest(ex.getMessage())));
              return;
            }
          }
        }
        mergeSort(res.result(), query, offset, limit, comp, h, vertxContext, res2 -> {
          if (res2.failed()) {
            handler.handle(Future.succeededFuture(
              CodexInstancesResource.GetCodexInstancesResponse.withPlainInternalServerError(res2.cause().getMessage()))
            );
          } else {
            handler.handle(Future.succeededFuture(
              CodexInstancesResource.GetCodexInstancesResponse.withJsonOK(res2.result())));
          }
        });
      }
    });
  }

  private void getById(String id, String module, Context vertxContext, LHeaders okapiHeaders,
    List<Instance> instances, Handler<AsyncResult<Void>> fut) {

    HttpClient client = vertxContext.owner().createHttpClient();
    final String url = okapiHeaders.get(XOkapiHeaders.URL) + "/codex-instances/" + id;
    logger.info("getById url=" + url);
    getUrl(module, url, client, okapiHeaders, res -> {
      if (res.failed()) {
        logger.warn("getById. getUrl failed " + res.cause());
        fut.handle(Future.failedFuture(res.cause()));
      } else {
        try {
          if (res.result().length() > 0) {
            Instance instance = null;
            instance = Json.decodeValue(res.result().toString(), Instance.class);
            instances.add(instance);
          }
        } catch (Exception e) {
          fut.handle(Future.failedFuture(e));
          return;
        }
        fut.handle(Future.succeededFuture());
      }
    });
  }

  @Override
  public void getCodexInstancesById(String id, String lang,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> handler,
    Context vertxContext) throws Exception {

    logger.info("Codex.mux getCodexInstancesById");
    LHeaders h = new LHeaders(okapiHeaders);
    getModules(h, vertxContext, res1 -> {
      if (res1.failed()) {
        handler.handle(Future.succeededFuture(
          CodexInstancesResource.GetCodexInstancesByIdResponse.withPlainUnauthorized(res1.cause().getMessage())));
      } else {
        List<Instance> instances = new LinkedList<>();
        List<Future> futures = new LinkedList<>();
        for (String m : res1.result()) {
          Future fut = Future.future();
          getById(id, m, vertxContext, h, instances, fut);
          futures.add(fut);
        }
        CompositeFuture.all(futures).setHandler(res2 -> {
          if (res2.failed()) {
            handler.handle(Future.succeededFuture(
              CodexInstancesResource.GetCodexInstancesByIdResponse.withPlainInternalServerError(res2.cause().getMessage()))
          );
          } else {
            if (instances.isEmpty()) {
              handler.handle(Future.succeededFuture(
                CodexInstancesResource.GetCodexInstancesByIdResponse.withPlainNotFound(id)));
            } else {
              handler.handle(Future.succeededFuture(
                CodexInstancesResource.GetCodexInstancesByIdResponse.withJsonOK(instances.get(0))));
            }
          }
        });
      }
    });
  }

}
