package org.folio.rest.impl;

import java.util.Map;
import java.util.Optional;

import javax.ws.rs.core.Response;

import org.folio.codex.CodexInterfaces;
import org.folio.codex.OkapiClient;
import org.folio.codex.exception.GetModulesFailException;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.Package;
import org.folio.rest.jaxrs.resource.CodexPackages;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class CodexPackagesImpl implements CodexPackages {

  private static Logger logger = LoggerFactory.getLogger(CodexPackagesImpl.class);
  private OkapiClient okapiClient = new OkapiClient();

  @Override
  public void getCodexPackages(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
                               Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    asyncResultHandler.handle(Future.succeededFuture(CodexPackages.GetCodexPackagesResponse.status(Response.Status.NOT_IMPLEMENTED).build()));
  }

  @Override
  public void getCodexPackagesById(String id, String lang, Map<String, String> okapiHeaders,
                                   Handler<AsyncResult<Response>> handler, Context vertxContext) {

    logger.info("CodexPackagesImpl#getCodexPackagesById");
    okapiClient.getModuleList(vertxContext, okapiHeaders, CodexInterfaces.CODEX_PACKAGES)
      .compose(modules ->
        okapiClient.getOptionalObjects(vertxContext, okapiHeaders, modules,
          okapiHeaders.get(XOkapiHeaders.URL) + "/codex-packages/" + id, Package.class))
      .map(instances -> {
        Optional<Package> packageObject = instances.stream()
          .filter(Optional::isPresent)
          .map(Optional::get)
          .findFirst();
        if (!packageObject.isPresent()) {
          handler.handle(Future.succeededFuture(CodexPackages.GetCodexPackagesByIdResponse.respond404WithTextPlain(id)));
        } else {
          handler.handle(Future.succeededFuture(CodexPackages.GetCodexPackagesByIdResponse.respond200WithApplicationJson(
            packageObject.get())));
        }
        return null;
      })
      .otherwise(throwable -> {
        if(throwable instanceof GetModulesFailException) {
          handler.handle(Future.succeededFuture(CodexPackages.GetCodexPackagesByIdResponse.respond401WithTextPlain(
            throwable.getMessage())));
        } else{
          handler.handle(Future.succeededFuture(CodexPackages.GetCodexPackagesByIdResponse.respond500WithTextPlain(
            throwable.getMessage())));
        }
        return null;
      });
  }
}
