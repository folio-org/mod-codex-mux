package org.folio.codex;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import javax.ws.rs.core.Response;

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
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLParseException;
import org.z3950.zing.cql.CQLParser;
import org.z3950.zing.cql.CQLRelation;
import org.z3950.zing.cql.CQLSortNode;
import org.z3950.zing.cql.CQLTermNode;

import org.folio.codex.exception.GetModulesFailException;
import org.folio.okapi.common.CQLUtil;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.Diagnostic;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstanceCollection;
import org.folio.rest.jaxrs.model.ResultInfo;
import org.folio.rest.jaxrs.resource.CodexInstances;

@java.lang.SuppressWarnings({"squid:S1192"})
public class Multiplexer implements CodexInstances {

  private static final String CODEX_INSTANCES_QUERY = "/codex-instances?";
  private static final String CODEX_PACKAGES_QUERY = "/codex-packages?";

  static class MergeRequest<T> {
    int offset;
    int limit;
    Map<String, String> headers;
    Context vertxContext;
    Map<String, MuxCollection<T>> cols;
  }

  public static class MuxCollection<T> {
    int statusCode;
    Buffer message;
    CollectionExtension<T> colExt;
    String query;
  }


  static class CollectionExtension<T> {
    private ResultInfo resultInfo;
    private List<T> items;

    public ResultInfo getResultInfo() {
      return resultInfo;
    }

    public void setResultInfo(ResultInfo resultInfo) {
      this.resultInfo = resultInfo;
    }

    public List<T> getItems() {
      return items;
    }

    public void setItems(List<T> items) {
      this.items = items;
    }
  }

  private static Logger logger = LoggerFactory.getLogger("codex.mux");

  private OkapiClient okapiClient = new OkapiClient();

  @SuppressWarnings({"squid:MaximumInheritanceDepth", "squid:S00107"})
  private <T> void getByQuery(String module, MergeRequest<T> mq, String query,
                                 int offset, int limit, CodexInterfaces codexInterface,
                                 Function<String, CollectionExtension<T>> parser, Handler<AsyncResult<Void>> fut) {

    HttpClient client = mq.vertxContext.owner().createHttpClient();
    String queryPath = codexInterface.equals(CodexInterfaces.CODEX) ? CODEX_INSTANCES_QUERY : CODEX_PACKAGES_QUERY;

    String url = mq.headers.get(XOkapiHeaders.URL) + queryPath
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
    okapiClient.<T>getUrl(module, url, client, mq.headers, res -> {
      if (res.failed()) {
        logger.warn("getByQuery. getUrl failed " + res.cause());
        fut.handle(Future.failedFuture(res.cause()));
      } else {
        MuxCollection<T> mc = res.result();
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
            mc.colExt = parser.apply(j.encode());
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

  private <T> void mergeSort(List<String> modules, CQLNode top, MergeRequest<T> mq,
    Comparator<T> comp, CodexInterfaces codexInterface, Function<String, CollectionExtension<T>> parser,
                                Handler<AsyncResult<CollectionExtension<T>>> handler) {

    List<Future> futures = new LinkedList<>();
    for (String module : modules) {
      if (top == null) {
        Future fut = Future.future();
        getByQuery(module, mq, null, 0, mq.offset + mq.limit, codexInterface, parser, fut);
        futures.add(fut);
      } else {
        CQLNode node = filterSource(module, top);
        if (node != null) {
          Future fut = Future.future();
          getByQuery(module, mq, node.toCQL(), 0, mq.offset + mq.limit, codexInterface, parser, fut);
          futures.add(fut);
        }
      }
    }
    CompositeFuture.all(futures).setHandler(res2 -> {
      if (res2.failed()) {
        handler.handle(Future.failedFuture(res2.cause()));
      } else {
        CollectionExtension colExt = mergeSet2(mq, comp);
        handler.handle(Future.succeededFuture(colExt));
      }
    });
  }

  private <T> ResultInfo createResultInfo(Map<String, MuxCollection<T>> cols) {
    int totalRecords = 0;
    List<Diagnostic> diagnostics = new LinkedList<>();
    for (MuxCollection col : cols.values()) {
      if (col.colExt != null && col.colExt.getResultInfo() != null) {
        totalRecords += col.colExt.getResultInfo().getTotalRecords();
        diagnostics.addAll(col.colExt.getResultInfo().getDiagnostics());
      }
    }
    ResultInfo resultInfo = new ResultInfo().withTotalRecords(totalRecords);
    resultInfo.setDiagnostics(diagnostics);
    return resultInfo;
  }

  private <T> CollectionExtension<T> mergeSet2(MergeRequest<T> mq, Comparator<T> comp) {

    CollectionExtension<T> collectionExtension = new CollectionExtension<>();
    collectionExtension.setResultInfo(createResultInfo(mq.cols));
    collectionExtension.setItems(new ArrayList<>());
    int[] ptrs = new int[mq.cols.size()]; // all 0
    for (int gOffset = 0; gOffset < mq.offset + mq.limit; gOffset++) {
      T minElement = null;
      int minI = -1;
      int i = 0;
      for (MuxCollection col : mq.cols.values()) {
        int idx = ptrs[i];
        if (col.colExt != null) {
          List<T> elements = col.colExt.getItems();
          if (idx < elements.size()) {
            T element = elements.get(idx);
            if (minElement == null
              || (comp == null && ptrs[minI] > ptrs[i])
              || (comp != null && comp.compare(minElement, element) > 0)) {
              minI = i;
              minElement = element;
            }
          }
        }
        i++;
      }
      if (minElement == null) {
        break;
      }
      ptrs[minI]++;
      if (gOffset >= mq.offset) {
        collectionExtension.getItems().add(minElement);
      }
    }
    return collectionExtension;
  }

  private <T> void analyzeResult(Map<String, MuxCollection<T>> cols, CollectionExtension<T> res) {

    List<Diagnostic> dl = new LinkedList<>();
    for (Map.Entry<String, MuxCollection<T>> ent : cols.entrySet()) {
      MuxCollection mc = ent.getValue();
      Diagnostic d = new Diagnostic();
      d.setSource(ent.getKey());
      d.setCode(Integer.toString(mc.statusCode));
      if (mc.colExt != null) {
        d.setRecordCount(mc.colExt.getResultInfo().getTotalRecords());
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

    okapiClient.getModules(okapiHeaders, vertxContext,CodexInterfaces.CODEX, res -> {
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
        MergeRequest<Instance> mq = new MergeRequest<>();
        mq.cols = new LinkedHashMap<>();
        mq.offset = offset;
        mq.limit = limit;
        mq.vertxContext = vertxContext;
         mq.headers = okapiHeaders;
        mergeSort(res.result(), top, mq, comp, CodexInterfaces.CODEX, this::parseInstanceCollection, res2 -> {
          if (res2.failed()) {
            handler.handle(Future.succeededFuture(
              CodexInstances.GetCodexInstancesResponse.respond500WithTextPlain(res2.cause().getMessage()))
            );
          } else {
            analyzeResult(mq.cols, res2.result());
            CollectionExtension<Instance> result = res2.result();
            handler.handle(Future.succeededFuture(
              CodexInstances.GetCodexInstancesResponse.respond200WithApplicationJson(
                new InstanceCollection()
                .withInstances(result != null ? result.getItems() : null)
                .withResultInfo(result != null ? result.getResultInfo() : null))));
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
    okapiClient.getModuleList(vertxContext, okapiHeaders, CodexInterfaces.CODEX)
      .compose(modules -> okapiClient.getOptionalObjects(vertxContext, okapiHeaders, modules,
        okapiHeaders.get(XOkapiHeaders.URL) + "/codex-instances/" + id , Instance.class))
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

  private CollectionExtension<Instance> parseInstanceCollection(String jsonObject){
    InstanceCollection value = Json.decodeValue(jsonObject, InstanceCollection.class);
    if(value == null){
      return null;
    }
    CollectionExtension<Instance> collectionExt = new CollectionExtension<>();
    collectionExt.setItems(value.getInstances());
    collectionExt.setResultInfo(value.getResultInfo());
    return collectionExt;
  }
}
