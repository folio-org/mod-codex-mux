package org.folio.codex.parser;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import static org.folio.codex.TestHelper.readFile;

import java.io.IOException;
import java.net.URISyntaxException;

import org.junit.Test;

import org.folio.codex.Multiplexer;
import org.folio.rest.jaxrs.model.Instance;

public class InstanceCollectionParserTest {

  @Test
  public void shouldReturnConvertedCollection() throws IOException, URISyntaxException {
    String stubInstances = readFile("codex/responses/instance/instance-collection.json");
    final Multiplexer.CollectionExtension<Instance> instanceCollection =
      InstanceCollectionParser.parseInstanceCollection(stubInstances);

    assertNotNull(instanceCollection);
    assertThat(instanceCollection.getResultInfo().getTotalRecords(), equalTo(2));
  }

  @Test
  public void shouldReturnNullWhenInstanceCollectionInvalid() {
    final Multiplexer.CollectionExtension<Instance> instanceCollection =
      InstanceCollectionParser.parseInstanceCollection("null");
    assertNull(instanceCollection);
  }
}
