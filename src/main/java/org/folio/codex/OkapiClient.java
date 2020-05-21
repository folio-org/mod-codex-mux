package org.folio.codex;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.folio.codex.exception.GetModulesFailException;
import org.folio.common.OkapiParams;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.util.FutureUtils;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
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
    WebClient client = WebClient.create(vertxContext.owner());
    logger.info("getObject url=" + url);
    getUrl(module, url, client, okapiHeaders).onComplete(res -> {
      client.close();
      if (res.failed()) {
        logger.warn("getObject. getUrl failed " + res.cause());
        promise.handle(Future.failedFuture(res.cause()));
      } else {
        Multiplexer.MuxCollection<?> mc = res.result();
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

  public <T> Future<Multiplexer.MuxCollection<T>> getUrl(String module, String url, WebClient client,
                      Map<String, String> okapiHeaders) {
    Promise<HttpResponse<Buffer>> responsePromise = Promise.promise();
    HttpRequest<Buffer> request = client.getAbs(url);
    okapiHeaders.forEach(request::putHeader);
    request
      .putHeader(XOkapiHeaders.MODULE_ID, module)
      .putHeader("Accept", "application/json")
      .send(responsePromise);
    return responsePromise.future()
      .map(
        res -> {
          Multiplexer.MuxCollection<T> mc = new Multiplexer.MuxCollection<>();
          mc.message = res.body() != null ? res.body() : Buffer.buffer();
          mc.statusCode = res.statusCode();
          return mc;
        });
  }

  /**
   * Method to return a future with module names instead of using handler.
   */

  public Future<List<String>> getModuleList(Context vertxContext, OkapiParams okapiParams,
                                            final CodexInterfaces supportedInterface) {
    WebClient client = WebClient.create(vertxContext.owner());
    String requestURI = "/_/proxy/tenants/" + okapiParams.getTenant() + "/interfaces/" + supportedInterface.getValue();
    logger.info("codex.mux getModuleList uri=" + requestURI + " with parameters " + okapiParams);
    HttpRequest<Buffer> request = client.get(okapiParams.getPort(), okapiParams.getHost(),
      requestURI);
    okapiParams.getHeaders().forEach(request::putHeader);
    Promise<HttpResponse<Buffer>> responsePromise = Promise.promise();
    request
      .send(responsePromise);
    Promise<List<String>> promise = Promise.promise();
    responsePromise
      .future()
      .map(response -> {
        if (response.statusCode() != 200) {
          throw new GetModulesFailException("Get " + requestURI + " with parameters " + okapiParams + " returned status " + response.statusCode());
        }
        Buffer buffer = response.body() != null ? response.body() : Buffer.buffer();
        logger.info("codex.mux getModuleList got " + buffer.toString());
        List<String> moduleList = new LinkedList<>();
        JsonArray moduleArray = readJsonArray(buffer);
        for (int i = 0; i < moduleArray.size(); i++) {
          JsonObject j = moduleArray.getJsonObject(i);
          String m = j.getString("id");
          if (!m.startsWith("mod-codex-mux")) { // avoid returning self
            moduleList.add(m);
          }
        }
        return moduleList;
      })
      .onComplete(result -> {
        client.close();
        completePromiseWithResult(promise, result);
      });
    return FutureUtils.wrapExceptions(promise.future(), GetModulesFailException.class);
  }

  private void completePromiseWithResult(Promise<List<String>> promise, AsyncResult<List<String>> result) {
    if(result.succeeded()){
      promise.complete(result.result());
    }
    else{
      promise.fail(result.cause());
    }
  }

  private JsonArray readJsonArray(Buffer b) {
    JsonArray a;
    try {
      a = b.toJsonArray();
    } catch (DecodeException ex) {
      logger.warn(ex.getMessage());
      throw new GetModulesFailException(ex.getMessage(), ex);
    }
    return a;
  }


}
