package org.folio.rest.impl;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.folio.codex.CodexInterfaces;
import org.folio.codex.OkapiClient;
import org.folio.codex.exception.GetModulesFailException;
import org.folio.common.OkapiParams;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.Source;
import org.folio.rest.jaxrs.model.SourceCollection;
import org.folio.rest.jaxrs.resource.CodexPackagesSources;

public class CodexPackagesSourcesImpl implements CodexPackagesSources {

  private static Logger logger = LogManager.getLogger(CodexPackagesSourcesImpl.class);
  @Override
  public void getCodexPackagesSources(String lang, Map<String, String> okapiHeaders,
                                      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    logger.info("CodexPackagesSourcesImpl#getCodexPackagesSources");
    OkapiParams okapiParams;
    try{
      okapiParams = new OkapiParams(okapiHeaders);
    }
    catch (IllegalArgumentException ex){
      asyncResultHandler.handle(
        Future.succeededFuture(CodexPackagesSources.GetCodexPackagesSourcesResponse.respond400WithTextPlain("Validation of okapi headers failed: " + ex.getMessage())));
      return;
    }

    OkapiClient okapiClient = new OkapiClient();
    okapiClient.getModuleList(vertxContext, okapiParams, CodexInterfaces.CODEX_PACKAGES)
      .compose(modules ->
      okapiClient.getOptionalObjects(vertxContext, okapiHeaders, modules,
        okapiHeaders.get(XOkapiHeaders.URL) + "/" + CodexInterfaces.CODEX_PACKAGES_SOURCES.getValue(),
        SourceCollection.class))
      .map(packages -> {
        final List<Source> sourceList = packages
          .filter(Optional::isPresent)
          .map(Optional::get)
          .flatMap(sourceCollection -> sourceCollection.getSources().stream())
          .collect(Collectors.toList());
          asyncResultHandler.handle(Future.succeededFuture(
            CodexPackagesSources.GetCodexPackagesSourcesResponse.respond200WithApplicationJson(
              new SourceCollection().withSources(sourceList))));
        return null;
      })
      .otherwise(throwable -> {
        if(throwable instanceof GetModulesFailException) {
          asyncResultHandler.handle(Future.succeededFuture(
            CodexPackagesSources.GetCodexPackagesSourcesResponse.respond400WithTextPlain(throwable.getMessage())));
        } else{
          asyncResultHandler.handle(Future.succeededFuture(
            CodexPackagesSources.GetCodexPackagesSourcesResponse.respond500WithTextPlain(throwable.getMessage())));
        }
        return null;
      });
  }
}
