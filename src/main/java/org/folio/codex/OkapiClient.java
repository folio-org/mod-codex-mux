package org.folio.codex;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.folio.codex.exception.GetModulesFailException;
import org.folio.okapi.common.XOkapiHeaders;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

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
    Promise<Optional<T>> promise = Promise.promise();
    WebClient client = WebClient.wrap(vertxContext.owner().createHttpClient());
    logger.info("getObject url=" + url);
    getUrl(module, url, client, okapiHeaders, res -> {
      if (res.failed()) {
        logger.warn("getObject. getUrl failed " + res.cause());
        promise.handle(Future.failedFuture(res.cause()));
      } else {
        Multiplexer.MuxCollection mc = res.result();
        if (mc.statusCode == 200) {
          try {
            T instance = Json.decodeValue(mc.message.toString(), responseClass);
            promise.handle(Future.succeededFuture(Optional.of(instance)));
          } catch (Exception e) {
            promise.handle(Future.failedFuture(e));
          }
        }
        else {
          promise.handle(Future.succeededFuture(Optional.empty()));
        }
      }
    });
    return promise.future();
  }

  public <T> void getUrl(String module, String url, WebClient client,
                      Map<String, String> okapiHeaders, Handler<AsyncResult<Multiplexer.MuxCollection<T>>> fut) {
    Promise<HttpResponse<Buffer>> responsePromise = Promise.promise();
    HttpRequest<Buffer> request = client.getAbs(url);
    okapiHeaders.forEach(request::putHeader);
    request
      .putHeader(XOkapiHeaders.MODULE_ID, module)
      .putHeader("Accept", "application/json")
      .send(responsePromise);
    responsePromise.future()
      .map(
        res -> {
          Buffer b = res.body() != null ? res.body() : Buffer.buffer();

          client.close();
          Multiplexer.MuxCollection<T> mc = new Multiplexer.MuxCollection<>();
          mc.message = b;
          mc.statusCode = res.statusCode();
          fut.handle(Future.succeededFuture(mc));
          return null;
        })
      .otherwise(r -> {
        client.close();
        fut.handle(Future.failedFuture(r.getMessage()));
        return null;
      });
  }

  /**
   * Method to return a future with module names instead of using handler.
   */

  public Future<List<String>> getModuleList(Context vertxContext, Map<String, String> okapiHeaders,
                                            final CodexInterfaces supportedInterface) {
    Promise<List<String>> promise = Promise.promise();

    final String okapiUrl = okapiHeaders.get(XOkapiHeaders.URL);
    if (okapiUrl == null) {
      promise.fail(new GetModulesFailException("missing " + XOkapiHeaders.URL));
    }
    final String tenant = okapiHeaders.get(XOkapiHeaders.TENANT);

    WebClient client = WebClient.wrap(vertxContext.owner().createHttpClient());
    final String absUrl = okapiUrl + "/_/proxy/tenants/" + tenant + "/interfaces/" + supportedInterface.getValue();
    logger.info("codex.mux getModuleList url=" + absUrl);
    HttpRequest<Buffer> request = client.getAbs(absUrl);
    okapiHeaders.forEach(request::putHeader);
    Promise<HttpResponse<Buffer>> responsePromise = Promise.promise();
    request
      .send(responsePromise);
    responsePromise
      .future()
      .map(response -> {
        if (response.statusCode() != 200) {
          client.close();
          promise.fail(new GetModulesFailException("Get " + absUrl + " returned status " + response.statusCode()));
        }
        Buffer b = response.body() != null ? response.body() : Buffer.buffer();
        logger.info("codex.mux getModuleList got " + b.toString());
        client.close();
        List<String> l = new LinkedList<>();
        JsonArray a = null;
        try {
          a = b.toJsonArray();
        } catch (DecodeException ex) {
          logger.warn(ex.getMessage());
          promise.fail(new GetModulesFailException(ex.getMessage()));
        }
        if (a != null) {
          for (int i = 0; i < a.size(); i++) {
            JsonObject j = a.getJsonObject(i);
            String m = j.getString("id");
            if (!m.startsWith("mod-codex-mux")) { // avoid returning self
              l.add(m);
            }
          }
          promise.complete(l);
        }
        return null;
      })
      .otherwise(r -> {
        promise.fail(new GetModulesFailException("Get " + absUrl + " returned exception " + r.getMessage()));
        client.close();
        return null;
      });
    return promise.future();
  }
}
