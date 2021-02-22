package org.folio.rest.impl;

import static org.folio.codex.ResultInformation.analyzeResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.ws.rs.core.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.folio.codex.CQLParameters;
import org.folio.codex.CodexInterfaces;
import org.folio.codex.MergeRequest;
import org.folio.codex.Multiplexer;
import org.folio.codex.OkapiClient;
import org.folio.codex.comparator.PackageComparator;
import org.folio.codex.exception.GetModulesFailException;
import org.folio.codex.exception.QueryValidationException;
import org.folio.codex.parser.PackageCollectionParser;
import org.folio.common.OkapiParams;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Package;
import org.folio.rest.jaxrs.model.PackageCollection;
import org.folio.rest.jaxrs.resource.CodexPackages;

public class CodexPackagesImpl implements CodexPackages {

  private static Logger logger = LogManager.getLogger(CodexPackagesImpl.class);
  private OkapiClient okapiClient = new OkapiClient();
  private Multiplexer multiplexer = new Multiplexer();

  @Override
  @Validate
  public void getCodexPackages(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
                               Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    OkapiParams okapiParams;
    try{
      okapiParams = new OkapiParams(okapiHeaders);
    }
    catch (IllegalArgumentException ex){
      asyncResultHandler.handle(
        Future.succeededFuture(CodexPackages.GetCodexPackagesResponse.respond400WithTextPlain("Validation of okapi headers failed: " + ex.getMessage())));
      return;
    }

    okapiClient.getModuleList(vertxContext, okapiParams, CodexInterfaces.CODEX_PACKAGES)
      .compose(moduleList -> getPackageCollectionExtension(query, offset, limit, okapiHeaders, vertxContext, moduleList))
      .map(packageCollectionExtension -> {
        asyncResultHandler.handle(Future.succeededFuture(
          GetCodexPackagesResponse.respond200WithApplicationJson(
            new PackageCollection()
              .withPackages(packageCollectionExtension != null ? packageCollectionExtension.getItems() : null)
              .withResultInfo(packageCollectionExtension != null ? packageCollectionExtension.getResultInfo() : null))));
        return null;
      })
      .otherwise(throwable -> {
        if (throwable instanceof GetModulesFailException){
          asyncResultHandler.handle(Future.succeededFuture(
            CodexPackages.GetCodexPackagesResponse.respond401WithTextPlain(throwable.getMessage())));
        } else if (throwable instanceof QueryValidationException || throwable instanceof IllegalArgumentException){
          asyncResultHandler.handle(Future.succeededFuture(
            CodexPackages.GetCodexPackagesResponse.respond400WithTextPlain(throwable.getMessage())));
        } else{
          asyncResultHandler.handle(Future.succeededFuture(
            CodexPackages.GetCodexPackagesResponse.respond500WithTextPlain(throwable.getMessage())));
        }
        return null;
      });
  }

  private Future<Multiplexer.CollectionExtension<Package>> getPackageCollectionExtension(
    String query, int offset, int limit, Map<String, String> okapiHeaders, Context vertxContext, List<String> moduleList) {

    CQLParameters<Package> cqlParameters = new CQLParameters<>(query);
    cqlParameters.setComparator(PackageComparator.get(cqlParameters.getCQLSortNode()));

    final MergeRequest<Package> mergeRequest = new MergeRequest.MergeRequestBuilder<Package>()
      .setLimit(limit)
      .setOffset(offset)
      .setVertxContext(vertxContext)
      .setHeaders(okapiHeaders)
      .setMuxCollectionMap(new LinkedHashMap<>())
      .build();

    return multiplexer.mergeSort(moduleList, cqlParameters, mergeRequest, CodexInterfaces.CODEX_PACKAGES,
      PackageCollectionParser::parsePackageCollection).compose(packageCollectionExtension -> {
        analyzeResult(mergeRequest.getMuxCollectionMap(), packageCollectionExtension);
        return Future.succeededFuture(packageCollectionExtension);
      });
  }

  @Override
  public void getCodexPackagesById(String id, String lang, Map<String, String> okapiHeaders,
                                   Handler<AsyncResult<Response>> handler, Context vertxContext) {

    logger.info("CodexPackagesImpl#getCodexPackagesById");

    OkapiParams okapiParams;
    try{
      okapiParams = new OkapiParams(okapiHeaders);
    }
    catch (IllegalArgumentException ex){
      handler.handle(
        Future.succeededFuture(Response.status(400)
          .header("Content-Type", "text/plain")
          .entity("Validation of okapi headers failed: " + ex.getMessage())
          .build()));
      return;
    }

    okapiClient.getModuleList(vertxContext, okapiParams, CodexInterfaces.CODEX_PACKAGES)
      .compose(modules ->
        okapiClient.getOptionalObjects(vertxContext, okapiHeaders, modules,
          okapiHeaders.get(XOkapiHeaders.URL) + "/codex-packages/" + id, Package.class))
      .map(instances -> {
        Optional<Package> packageObject = instances
          .filter(Optional::isPresent)
          .map(Optional::get)
          .findFirst();
        if (packageObject.isEmpty()) {
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
