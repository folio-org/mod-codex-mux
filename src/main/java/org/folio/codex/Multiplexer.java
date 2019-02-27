package org.folio.codex;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.ws.rs.core.Response;

import org.folio.codex.exception.GetModulesFailException;
import org.folio.okapi.common.CQLUtil;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.Diagnostic;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstanceCollection;
import org.folio.rest.jaxrs.model.ResultInfo;
import org.folio.rest.jaxrs.resource.CodexInstances;
import org.folio.rest.jaxrs.resource.CodexPackages;
import org.folio.rest.jaxrs.resource.CodexPackagesSources;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLParseException;
import org.z3950.zing.cql.CQLParser;
import org.z3950.zing.cql.CQLRelation;
import org.z3950.zing.cql.CQLSortNode;
import org.z3950.zing.cql.CQLTermNode;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

@java.lang.SuppressWarnings({"squid:S1192"})
public class Multiplexer implements CodexInstances {

  static class MergeRequest {
    int offset;
    int limit;
    LHeaders headers;
    Context vertxContext;
    Map<String, MuxCollection> cols;
  }

  public static class MuxCollection {
    int statusCode;
    Buffer message;
    InstanceCollection col;
    String query;
  }

  private static Logger logger = LoggerFactory.getLogger("codex.mux");

  private OkapiClient okapiClient = new OkapiClient();

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
    okapiClient.getUrl(module, url, client, mq.headers, res -> {
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
    for (String module : modules) {
      if (top == null) {
        Future fut = Future.future();
        getByQuery(module, mq, null, 0, mq.offset + mq.limit, fut);
        futures.add(fut);
      } else {
        CQLNode node = filterSource(module, top);
        if (node != null) {
          Future fut = Future.future();
          getByQuery(module, mq, node.toCQL(), 0, mq.offset + mq.limit, fut);
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
        CodexInstances.GetCodexInstancesResponse.respond200WithApplicationJson(res)));
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
    Context vertxContext) {

    LHeaders h = new LHeaders(okapiHeaders);
    okapiClient.getModules(h, vertxContext,CodexInterfaces.CODEX, res -> {
      if (res.failed()) {
        handler.handle(Future.succeededFuture(
          CodexInstances.GetCodexInstancesResponse.respond401WithTextPlain(res.cause().getMessage())));
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
              Future.succeededFuture(CodexInstances.GetCodexInstancesResponse.respond400WithTextPlain(ex.getMessage())));
            return;
          } catch (IOException ex) {
            handler.handle(
              Future.succeededFuture(CodexInstances.GetCodexInstancesResponse.respond500WithTextPlain(ex.getMessage())));
            return;
          }
          CQLSortNode sn = CQLInspect.getSort(top);
          if (sn != null) {
            try {
              comp = InstanceComparator.get(sn);
            } catch (IllegalArgumentException ex) {
              handler.handle(
                Future.succeededFuture(
                  CodexInstances.GetCodexInstancesResponse.respond400WithTextPlain(ex.getMessage())));
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
              CodexInstances.GetCodexInstancesResponse.respond500WithTextPlain(res2.cause().getMessage()))
            );
          } else {
            analyzeResult(mq.cols, res2.result(), handler);
          }
        });
      }
    });
  }

  @Override
  public void getCodexInstancesById(String id, String lang,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> handler,
    Context vertxContext) {
    logger.info("Codex.mux getCodexInstancesById");
    LHeaders headers = new LHeaders(okapiHeaders);
    okapiClient.getModuleList(vertxContext, headers, CodexInterfaces.CODEX)
      .compose(modules -> okapiClient.getOptionalObjects(vertxContext, headers, modules,
        headers.get(XOkapiHeaders.URL) + "/codex-instances/" + id , Instance.class))
      .map(optionalInstances -> {
        Optional<Instance> instance = optionalInstances.stream()
          .filter(Optional::isPresent)
          .map(Optional::get)
          .findFirst();
        if (!instance.isPresent()) {
          handler.handle(Future.succeededFuture(CodexInstances.GetCodexInstancesByIdResponse.respond404WithTextPlain(id)));
        } else {
          handler.handle(Future.succeededFuture(CodexInstances.GetCodexInstancesByIdResponse.respond200WithApplicationJson(
            instance.get())));
        }
        return null;
      })
      .otherwise(throwable -> {
        if(throwable instanceof GetModulesFailException) {
          handler.handle(Future.succeededFuture(CodexInstances.GetCodexInstancesByIdResponse.respond401WithTextPlain(
            throwable.getMessage())));
        } else{
          handler.handle(Future.succeededFuture(CodexInstances.GetCodexInstancesByIdResponse.respond500WithTextPlain(
            throwable.getMessage())));
        }
        return null;
      });
  }

}
