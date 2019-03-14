package org.folio.codex.parser;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import static org.folio.codex.TestHelper.readFile;

import java.io.IOException;
import java.net.URISyntaxException;

import org.junit.Test;

import org.folio.codex.Multiplexer;
import org.folio.rest.jaxrs.model.Package;

public class PackageCollectionParserTest {

  @Test
  public void shouldReturnConvertedCollection() throws IOException, URISyntaxException {
    String stubInstances = readFile("codex/responses/packages/packages-collection.json");
    final Multiplexer.CollectionExtension<Package> packageCollection = PackageCollectionParser.parsePackageCollection(stubInstances);
    assertThat(packageCollection.getResultInfo().getTotalRecords(), equalTo(2));
  }

  @Test
  public void shouldReturnNullWhenPackageCollectionInvalid() {
    final Multiplexer.CollectionExtension<Package> instanceCollection = PackageCollectionParser.parsePackageCollection("null");
    assertNull(instanceCollection);
  }
}
