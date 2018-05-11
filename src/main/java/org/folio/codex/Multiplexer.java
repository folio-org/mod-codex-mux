package org.folio.codex;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.DecodeException;
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
import org.folio.okapi.common.CQLUtil;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstanceCollection;
import org.folio.rest.jaxrs.model.ResultInfo;
import org.folio.rest.jaxrs.model.Diagnostic;
import org.folio.rest.jaxrs.resource.CodexInstancesResource;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLParseException;
import org.z3950.zing.cql.CQLParser;
import org.z3950.zing.cql.CQLRelation;
import org.z3950.zing.cql.CQLSortNode;
import org.z3950.zing.cql.CQLTermNode;

@java.lang.SuppressWarnings({"squid:S1192"})
public class Multiplexer implements CodexInstancesResource {

  class MergeRequest {
    int offset;
    int limit;
    LHeaders headers;
    Context vertxContext;
    Map<String, MuxCollection> cols;
  }

  class MuxCollection {
    int statusCode;
    Buffer message;
    InstanceCollection col;
    String query;
  }

  private static Logger logger = LoggerFactory.getLogger("codex.mux");

  void getModules(LHeaders okapiHeaders, Context vertxContext,
    Handler<AsyncResult<List<String>>> fut) {

    final String okapiUrl = okapiHeaders.get(XOkapiHeaders.URL);
    if (okapiUrl == null) {
      fut.handle(Future.failedFuture("missing " + XOkapiHeaders.URL));
      return;
    }
    final String tenant = okapiHeaders.get(XOkapiHeaders.TENANT);

    HttpClient client = vertxContext.owner().createHttpClient();
    final String absUrl = okapiUrl + "/_/proxy/tenants/" + tenant + "/interfaces/codex";
    logger.info("codex.mux getModules url=" + absUrl);
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
        client.close();
        List<String> l = new LinkedList<>();
        JsonArray a = null;
        try {
          a = b.toJsonArray();
        } catch (DecodeException ex) {
          logger.warn(ex.getMessage());
          fut.handle(Future.failedFuture(ex.getMessage()));
        }
        if (a != null) {
          for (int i = 0; i < a.size(); i++) {
            JsonObject j = a.getJsonObject(i);
            String m = j.getString("id");
            if (!m.startsWith("mod-codex-mux")) { // avoid returning self
              l.add(m);
            }
          }
          fut.handle(Future.succeededFuture(l));
        }
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

  private void getUrl(String module, String url, HttpClient client,
    LHeaders okapiHeaders, Handler<AsyncResult<MuxCollection>> fut) {

    HttpClientRequest req = client.getAbs(url, res -> {
      Buffer b = Buffer.buffer();
      res.handler(b::appendBuffer);
      res.endHandler(r -> {
        client.close();
        MuxCollection mc = new MuxCollection();
        mc.message = b;
        mc.statusCode = res.statusCode();
        fut.handle(Future.succeededFuture(mc));
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

  private void getByQuery(String module, MergeRequest mq, String query,
    int offset, int limit, Handler<AsyncResult<Void>> fut) {

    HttpClient client = mq.vertxContext.owner().createHttpClient();
    String url = mq.headers.get(XOkapiHeaders.URL) + "/codex-instances?"
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
    getUrl(module, url, client, mq.headers, res -> {
      if (res.failed()) {
        logger.warn("getByQuery. getUrl failed " + res.cause());
        fut.handle(Future.failedFuture(res.cause()));
      } else {
        MuxCollection mc = res.result();
        if (mc.statusCode == 200) {
          try {
            JsonObject j = new JsonObject(mc.message.toString());
            if (j.getJsonObject("resultInfo") == null) {
              JsonObject ri = new JsonObject();
              ri.put("totalRecords", j.remove("totalRecords"));
              ri.put("facets", new JsonArray());
              ri.put("diagnostics", new JsonArray());
              j.put("resultInfo", ri);
            }
            mc.col = Json.decodeValue(j.encode(), InstanceCollection.class);
            mc.query = query;
          } catch (Exception e) {
            fut.handle(Future.failedFuture(e));
            return;
          }
        }
        mq.cols.put(module, mc);
        fut.handle(Future.succeededFuture());
      }
    });
  }

  private void mergeSort(List<String> modules, CQLNode top, MergeRequest mq,
    Comparator<Instance> comp, Handler<AsyncResult<InstanceCollection>> handler) {

    List<Future> futures = new LinkedList<>();
    for (String m : modules) {
      if (top == null) {
        Future fut = Future.future();
        getByQuery(m, mq, null, 0, mq.offset + mq.limit, fut);
        futures.add(fut);
      } else {
        CQLNode n = filterSource(m, top);
        if (n != null) {
          Future fut = Future.future();
          getByQuery(m, mq, n.toCQL(), 0, mq.offset + mq.limit, fut);
          futures.add(fut);
        }
      }
    }
    CompositeFuture.all(futures).setHandler(res2 -> {
      if (res2.failed()) {
        handler.handle(Future.failedFuture(res2.cause()));
      } else {
        InstanceCollection colR = mergeSet2(mq, comp);
        handler.handle(Future.succeededFuture(colR));
      }
    });
  }

  private ResultInfo createResultInfo(Map<String, MuxCollection> cols) {
    int totalRecords = 0;
    List<Diagnostic> diagnostics = new LinkedList<>();
    for (MuxCollection col : cols.values()) {
      if (col.col != null && col.col.getResultInfo() != null) {
        totalRecords += col.col.getResultInfo().getTotalRecords();
        diagnostics.addAll(col.col.getResultInfo().getDiagnostics());
      }
    }
    ResultInfo resultInfo = new ResultInfo().withTotalRecords(totalRecords);
    resultInfo.setDiagnostics(diagnostics);
    return resultInfo;
  }

  private InstanceCollection mergeSet2(MergeRequest mq, Comparator<Instance> comp) {

    InstanceCollection colR = new InstanceCollection();
    colR.setResultInfo(createResultInfo(mq.cols));
    int[] ptrs = new int[mq.cols.size()]; // all 0
    for (int gOffset = 0; gOffset < mq.offset + mq.limit; gOffset++) {
      Instance minInstance = null;
      int minI = -1;
      int i = 0;
      for (MuxCollection col : mq.cols.values()) {
        int idx = ptrs[i];
        if (col.col != null) {
          List<Instance> instances = col.col.getInstances();
          if (idx < instances.size()) {
            Instance instance = instances.get(idx);
            if (minInstance == null
              || (comp == null && ptrs[minI] > ptrs[i])
              || (comp != null && comp.compare(minInstance, instance) > 0)) {
              minI = i;
              minInstance = instance;
            }
          }
        }
        i++;
      }
      if (minInstance == null) {
        break;
      }
      ptrs[minI]++;
      if (gOffset >= mq.offset) {
        colR.getInstances().add(minInstance);
      }
    }
    return colR;
  }

  void analyzeResult(Map<String, MuxCollection> cols, InstanceCollection res,
    Handler<AsyncResult<Response>> handler) {

    List<Diagnostic> dl = new LinkedList<>();
    for (Map.Entry<String, MuxCollection> ent : cols.entrySet()) {
      MuxCollection mc = ent.getValue();
      Diagnostic d = new Diagnostic();
      d.setSource(ent.getKey());
      d.setCode(Integer.toString(mc.statusCode));
      if (mc.col != null) {
        d.setRecordCount(mc.col.getResultInfo().getTotalRecords());
      }
      d.setQuery(mc.query);
      if (mc.statusCode != 200) {
        d.setMessage(mc.message.toString());
        logger.warn("Module " + ent.getKey() + " returned status " + mc.statusCode);
        logger.warn(mc.message.toString());
      }
      dl.add(d);
    }
      ResultInfo ri = res.getResultInfo();
      ri.setDiagnostics(dl);
      res.setResultInfo(ri);
      handler.handle(Future.succeededFuture(
        CodexInstancesResource.GetCodexInstancesResponse.withJsonOK(res)));
  }

  private CQLNode filterSource(String mod, CQLNode top) {
    CQLRelation rel = new CQLRelation("=");
    Comparator<CQLTermNode> f1 = (CQLTermNode n1, CQLTermNode n2) -> {
      if (n1.getIndex().equals(n2.getIndex()) && !n1.getTerm().equals(n2.getTerm())) {
        return -1;
      }
      return 0;
    };
    Comparator<CQLTermNode> f2 = (CQLTermNode n1, CQLTermNode n2)
      -> n1.getIndex().equals(n2.getIndex()) ? 0 : -1;
    CQLTermNode source = null;
    if (mod.startsWith("mod-codex-ekb")) {
      source = new CQLTermNode("source", rel, "kb");
    } else if (mod.startsWith("mod-codex-inventory")) {
      source = new CQLTermNode("source", rel, "local");
    } else if (mod.startsWith("mock")) { // for Unit testing
      source = new CQLTermNode("source", rel, mod);
    }
    if (source == null) {
      return top;
    } else {
      if (!CQLUtil.eval(top, source, f1)) {
        logger.info("Filter out module " + mod);
        return null;
      }
      logger.info("Reducing query for module " + mod);
      return CQLUtil.reducer(top, source, f2);
    }
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
        CQLNode top = null;
        if (query != null) {
          CQLParser parser = new CQLParser(CQLParser.V1POINT2);
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
        MergeRequest mq = new MergeRequest();
        mq.cols = new LinkedHashMap<>();
        mq.offset = offset;
        mq.limit = limit;
        mq.vertxContext = vertxContext;
        mq.headers = h;
        mergeSort(res.result(), top, mq, comp, res2 -> {
          if (res2.failed()) {
            handler.handle(Future.succeededFuture(
              CodexInstancesResource.GetCodexInstancesResponse.withPlainInternalServerError(res2.cause().getMessage()))
            );
          } else {
            analyzeResult(mq.cols, res2.result(), handler);
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
        MuxCollection mc = res.result();
        if (mc.statusCode == 200) {
          try {
            Instance instance = Json.decodeValue(mc.message.toString(), Instance.class);
            instances.add(instance);
          } catch (Exception e) {
            fut.handle(Future.failedFuture(e));
            return;
          }
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
