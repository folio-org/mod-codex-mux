package org.folio.codex.parser;

import io.vertx.core.json.Json;

import org.folio.codex.Multiplexer;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstanceCollection;

public class InstanceCollectionParser {

  private InstanceCollectionParser() {
  }

  public static Multiplexer.CollectionExtension<Instance> parseInstanceCollection(String jsonObject){
    InstanceCollection value = Json.decodeValue(jsonObject, InstanceCollection.class);
    if(value == null){
      return null;
    }
    Multiplexer.CollectionExtension<Instance> collectionExt = new Multiplexer.CollectionExtension<>();
    collectionExt.setItems(value.getInstances());
    collectionExt.setResultInfo(value.getResultInfo());
    return collectionExt;
  }
}
