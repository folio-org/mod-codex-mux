package org.folio.codex;

import static org.folio.codex.ResultInformation.analyzeResult;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

import javax.ws.rs.core.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLRelation;
import org.z3950.zing.cql.CQLTermNode;

import org.folio.codex.comparator.InstanceComparator;
import org.folio.codex.exception.GetModulesFailException;
import org.folio.codex.exception.QueryValidationException;
import org.folio.codex.parser.InstanceCollectionParser;
import org.folio.okapi.common.CQLUtil;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstanceCollection;
import org.folio.rest.jaxrs.model.ResultInfo;
import org.folio.rest.jaxrs.resource.CodexInstances;

@java.lang.SuppressWarnings({"squid:S1192"})
public class Multiplexer implements CodexInstances {

  private static final String TOTAL_RECORDS = "totalRecords";
  private static final String RESULT_INFO = "resultInfo";

  public static class MuxCollection<T> {
    int statusCode;
    Buffer message;
    CollectionExtension<T> colExt;
    String query;
  }

  public static class CollectionExtension<T> {
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

  @SuppressWarnings({"squid:S00107"})
  private <T> void getByQuery(String module, MergeRequest<T> mq, String query,
                                 int offset, int limit, CodexInterfaces codexInterface,
                                 Function<String, CollectionExtension<T>> parser, Handler<AsyncResult<Void>> fut) {

    HttpClient client = mq.getVertxContext().owner().createHttpClient();

    String url = mq.getHeaders().get(XOkapiHeaders.URL) + codexInterface.getQueryPath()
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
    okapiClient.<T>getUrl(module, url, client, mq.getHeaders(), res -> {
      if (res.failed()) {
        logger.warn("getByQuery. getUrl failed " + res.cause());
        fut.handle(Future.failedFuture(res.cause()));
      } else {
        MuxCollection<T> mc = getMuxCollection(query, parser, fut, res);
        if (mc == null) return;
        mq.getMuxCollectionMap().put(module, mc);
        fut.handle(Future.succeededFuture());
      }
    });
  }

  private <T> MuxCollection<T> getMuxCollection(String query, Function<String, CollectionExtension<T>> parser,
                                                Handler<AsyncResult<Void>> fut, AsyncResult<MuxCollection<T>> res) {
    MuxCollection<T> muxCollection = res.result();
    if (muxCollection.statusCode == 200) {
      try {
        JsonObject j = new JsonObject(muxCollection.message.toString());
        if (j.getJsonObject(RESULT_INFO) == null) {
          JsonObject ri = new JsonObject();
          ri.put(TOTAL_RECORDS, j.remove(TOTAL_RECORDS));
          ri.put("facets", new JsonArray());
          ri.put("diagnostics", new JsonArray());
          j.put(RESULT_INFO, ri);
        }
        muxCollection.colExt = parser.apply(j.encode());
        muxCollection.query = query;
      } catch (Exception e) {
        fut.handle(Future.failedFuture(e));
        return null;
      }
    }
    return muxCollection;
  }

  public <T> Future<CollectionExtension<T>> mergeSort(List<String> modules, CQLParameters<T> cqlParameters,
                                                      MergeRequest<T> mq, CodexInterfaces codexInterface,
                                                      Function<String, CollectionExtension<T>> parser) {

    List<Future> futures = new LinkedList<>();
    for (String module : modules) {
      final CQLNode cqlNode = cqlParameters.getCqlNode();
      if (cqlNode == null) {
        Future fut = Future.future();
        getByQuery(module, mq, null, 0, mq.getOffset() + mq.getLimit(), codexInterface, parser, fut);
        futures.add(fut);
      } else {
        CQLNode node = filterSource(module, cqlNode);
        if (node != null) {
          Future fut = Future.future();
          getByQuery(module, mq, node.toCQL(), 0, mq.getOffset() + mq.getLimit(), codexInterface, parser, fut);
          futures.add(fut);
        }
      }
    }
    Future<CollectionExtension<T>> future = Future.future();
     CompositeFuture.all(futures).setHandler(res2 -> {
      if (res2.failed()) {
        future.fail(res2.cause());
      } else {
        CollectionExtension<T> colExt = mergeSet2(mq, cqlParameters.getComparator());
        future.complete(colExt);
      }
    });
     return future;
  }

  private <T> CollectionExtension<T> mergeSet2(MergeRequest<T> mq, Comparator<T> comp) {

    CollectionExtension<T> collectionExtension = new CollectionExtension<>();
    final Map<String, MuxCollection<T>> muxCollectionMap = mq.getMuxCollectionMap();
    collectionExtension.setResultInfo(ResultInformation.createResultInfo(muxCollectionMap));
    collectionExtension.setItems(new ArrayList<>());
    int[] ptrs = new int[muxCollectionMap.size()]; // all 0
    for (int gOffset = 0; gOffset < mq.getOffset() + mq.getLimit(); gOffset++) {
      T minElement = null;
      int minI = -1;
      int i = 0;
      for (MuxCollection col : muxCollectionMap.values()) {
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
      if (gOffset >= mq.getOffset()) {
        collectionExtension.getItems().add(minElement);
      }
    }
    return collectionExtension;
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
    } else if (mod.startsWith("mod-agreements")) {
      source = new CQLTermNode("source", rel, "localkb");
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
  @Validate
  public void getCodexInstances(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
                                Handler<AsyncResult<Response>> handler, Context vertxContext) {
    logger.info("Codex.mux getCodexInstances");
    okapiClient.getModuleList(vertxContext, okapiHeaders,CodexInterfaces.CODEX)
      .compose(moduleList -> getInstanceCollectionExtension(query, offset, limit, okapiHeaders, vertxContext, moduleList))
      .map(instanceCollectionExtension -> {
        handler.handle(Future.succeededFuture(GetCodexInstancesResponse.respond200WithApplicationJson(
          new InstanceCollection()
            .withInstances(instanceCollectionExtension != null ? instanceCollectionExtension.getItems() : null)
            .withResultInfo(instanceCollectionExtension != null ? instanceCollectionExtension.getResultInfo() : null))));
          return null;
      })
      .otherwise(throwable -> {
        if (throwable instanceof GetModulesFailException){
          handler.handle(Future.succeededFuture(
            CodexInstances.GetCodexInstancesResponse.respond401WithTextPlain(throwable.getMessage())));
        } else if (throwable.getCause() instanceof QueryValidationException || throwable instanceof IllegalArgumentException){
          handler.handle(
            Future.succeededFuture(CodexInstances.GetCodexInstancesResponse.respond400WithTextPlain(throwable.getMessage())));
        } else {
          handler.handle(
            Future.succeededFuture(CodexInstances.GetCodexInstancesResponse.respond500WithTextPlain(throwable.getMessage())));
        }
        return null;
      });
  }

  private Future<CollectionExtension<Instance>> getInstanceCollectionExtension(String query, int offset, int limit,
      Map<String, String> okapiHeaders, Context vertxContext, List<String> moduleList) {
    try {

      CQLParameters<Instance> cqlParameters = new CQLParameters<>(query);
      cqlParameters.setComparator(InstanceComparator.get(cqlParameters.getCQLSortNode()));

      final MergeRequest<Instance> mergeRequest = new MergeRequest.MergeRequestBuilder<Instance>()
        .setLimit(limit)
        .setOffset(offset)
        .setHeaders(okapiHeaders)
        .setVertxContext(vertxContext)
        .setMuxCollectionMap(new LinkedHashMap<>())
        .build();

      return mergeSort(moduleList, cqlParameters, mergeRequest, CodexInterfaces.CODEX,
          InstanceCollectionParser::parseInstanceCollection).compose(instanceCollectionExtension -> {
            analyzeResult(mergeRequest.getMuxCollectionMap(), instanceCollectionExtension);
            return Future.succeededFuture(instanceCollectionExtension);
          });

    } catch (QueryValidationException e) {
      throw new CompletionException(e);
    }
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
        Optional<Instance> instance = optionalInstances
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
