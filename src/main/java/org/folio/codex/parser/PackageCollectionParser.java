package org.folio.codex.parser;

import io.vertx.core.json.Json;

import org.folio.codex.Multiplexer;
import org.folio.rest.jaxrs.model.Package;
import org.folio.rest.jaxrs.model.PackageCollection;

public class PackageCollectionParser {

  private PackageCollectionParser() {
  }

  public static Multiplexer.CollectionExtension<Package> parsePackageCollection(String jsonObject){
    PackageCollection value = Json.decodeValue(jsonObject, PackageCollection.class);
    if(value == null){
      return null;
    }
    Multiplexer.CollectionExtension<Package> collectionExt = new Multiplexer.CollectionExtension<>();
    collectionExt.setItems(value.getPackages());
    collectionExt.setResultInfo(value.getResultInfo());
    return collectionExt;
  }
}
