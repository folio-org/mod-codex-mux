package org.folio.codex;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

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

import org.folio.codex.exception.GetModulesFailException;
import org.folio.okapi.common.XOkapiHeaders;

public class OkapiClient {
  private static Logger logger = LoggerFactory.getLogger(OkapiClient.class);

  /**
   * Sends a request to each module from modules list, if request is successful then returned object is parsed as
   * a responseClass and wrapped in Optional, if request fails then Optional.empty() is returned for this module
   * @param vertxContext vertxContext
   * @param headers headers that will be sent to modules
   * @param modules list of modules to which requests are sent
   * @param url url of requests
   * @param responseClass class of retrieved objects
   */
  public <T> Future<Stream<Optional<T>>> getOptionalObjects(Context vertxContext, Map<String, String> headers,
                                                            List<String> modules, String url, Class<T> responseClass) {
    List<Future<Optional<T>>> futures = new ArrayList<>();
    for (String module : modules) {
      Future<Optional<T>> future = getObject(module, vertxContext, headers,
        url, responseClass);
      futures.add(future);
    }
    return CompositeFuture.all(new ArrayList<>(futures))
      .map(compositeFuture -> futures.stream().map(Future::result));
  }

  private <T> Future<Optional<T>> getObject(String module, Context vertxContext, Map<String, String> okapiHeaders,
                                            String url, Class<T> responseClass) {
    Future<Optional<T>> future = Future.future();
    HttpClient client = vertxContext.owner().createHttpClient();
    logger.info("getObject url=" + url);
    getUrl(module, url, client, okapiHeaders, res -> {
      if (res.failed()) {
        logger.warn("getObject. getUrl failed " + res.cause());
        future.handle(Future.failedFuture(res.cause()));
      } else {
        Multiplexer.MuxCollection mc = res.result();
        if (mc.statusCode == 200) {
          try {
            T instance = Json.decodeValue(mc.message.toString(), responseClass);
            future.handle(Future.succeededFuture(Optional.of(instance)));
          } catch (Exception e) {
            future.handle(Future.failedFuture(e));
          }
        }
        else {
          future.handle(Future.succeededFuture(Optional.empty()));
        }
      }
    });
    return future;
  }

  public <T> void getUrl(String module, String url, HttpClient client,
                      Map<String, String> okapiHeaders, Handler<AsyncResult<Multiplexer.MuxCollection<T>>> fut) {

    HttpClientRequest req = client.getAbs(url, res -> {
      Buffer b = Buffer.buffer();
      res.handler(b::appendBuffer);
      res.endHandler(r -> {
        client.close();
        Multiplexer.MuxCollection<T> mc = new Multiplexer.MuxCollection<>();
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

  /**
   * Method to return a future with module names instead of using handler.
   */

  public Future<List<String>> getModuleList(Context vertxContext, Map<String, String> okapiHeaders,
                                            final CodexInterfaces supportedInterface) {
    Future<List<String>> future = Future.future();

    final String okapiUrl = okapiHeaders.get(XOkapiHeaders.URL);
    if (okapiUrl == null) {
      future.fail(new GetModulesFailException("missing " + XOkapiHeaders.URL));
    }
    final String tenant = okapiHeaders.get(XOkapiHeaders.TENANT);

    HttpClient client = vertxContext.owner().createHttpClient();
    final String absUrl = okapiUrl + "/_/proxy/tenants/" + tenant + "/interfaces/" + supportedInterface.getValue();
    logger.info("codex.mux getModuleList url=" + absUrl);
    HttpClientRequest req = client.getAbs(absUrl, res -> {
      if (res.statusCode() != 200) {
        client.close();
        future.fail(new GetModulesFailException("Get " + absUrl + " returned status " + res.statusCode()));
      }
      Buffer b = Buffer.buffer();
      res.handler(b::appendBuffer);
      res.endHandler(r -> {
        logger.info("codex.mux getModuleList got " + b.toString());
        client.close();
        List<String> l = new LinkedList<>();
        JsonArray a = null;
        try {
          a = b.toJsonArray();
        } catch (DecodeException ex) {
          logger.warn(ex.getMessage());
          future.fail(new GetModulesFailException(ex.getMessage()));
        }
        if (a != null) {
          for (int i = 0; i < a.size(); i++) {
            JsonObject j = a.getJsonObject(i);
            String m = j.getString("id");
            if (!m.startsWith("mod-codex-mux")) { // avoid returning self
              l.add(m);
            }
          }
          future.complete(l);
        }
      });
    });
    for (Map.Entry<String, String> e : okapiHeaders.entrySet()) {
      req.putHeader(e.getKey(), e.getValue());
    }
    req.exceptionHandler(r -> {
      future.fail(new GetModulesFailException("Get " + absUrl + " returned exception " + r.getMessage()));
      client.close();
    });
    req.end();
    return future;
  }
}
