package org.folio.codex;

import java.util.Map;
import java.util.Optional;

import org.folio.okapi.common.XOkapiHeaders;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class HttpHelper {
  private static Logger logger = LoggerFactory.getLogger(HttpHelper.class);

  public <T> Future<Optional<T>> getById(String module, Context vertxContext, LHeaders okapiHeaders, String url,
                                          Class<T> responseClass) {
    Future<Optional<T>> future = Future.future();
    HttpClient client = vertxContext.owner().createHttpClient();
    logger.info("getById url=" + url);
    getUrl(module, url, client, okapiHeaders, res -> {
      if (res.failed()) {
        logger.warn("getById. getUrl failed " + res.cause());
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

  public void getUrl(String module, String url, HttpClient client,
                      LHeaders okapiHeaders, Handler<AsyncResult<Multiplexer.MuxCollection>> fut) {

    HttpClientRequest req = client.getAbs(url, res -> {
      Buffer b = Buffer.buffer();
      res.handler(b::appendBuffer);
      res.endHandler(r -> {
        client.close();
        Multiplexer.MuxCollection mc = new Multiplexer.MuxCollection();
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
}
