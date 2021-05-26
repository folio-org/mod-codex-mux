package org.folio.codex.comparator;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.ArrayMatching.arrayContaining;
import static org.junit.Assert.assertNull;

import java.util.Arrays;

import org.junit.Test;

import org.folio.codex.CQLParameters;
import org.folio.codex.exception.QueryValidationException;
import org.folio.rest.jaxrs.model.Package;

public class PackagesComparatorTest {

  private Package a = new Package().withName("test a");
  private Package b = new Package().withName("test b");
  private Package c = new Package().withName("test c");

  private Package [] sort(String cql, Package ... packages) {

    CQLParameters<Package> cqlParameters = new CQLParameters<>(cql);
    cqlParameters.setComparator(PackageComparator.get(cqlParameters.getCQLSortNode()));

    Arrays.sort(packages, cqlParameters.getComparator());
    return packages;
  }

  @Test
  public void sortByNameAscending() {
    assertThat(sort("cql.allRecords=1 sortBy name", c, a, b), is(arrayContaining(a, b, c)));
  }

  @Test
  public void sortByNameDescending()  {
    assertThat(sort("cql.allRecords=1 sortBy name/desc", c, a, b), is(arrayContaining(c, b, a)));
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldThrowExceptionWhenNotSupportedIndex() {
    sort("cql.allRecords=1 sortBy invalid");
  }

  @Test(expected = QueryValidationException.class)
  public void shouldThrowExceptionWhenInvalidQuery()  {
    sort("(cql.allRecords=1) sort By invalid");
  }

  @Test
  public void shouldNotReturnComparatorWhenQueryIsNull() {
    assertNull(new CQLParameters<>("cql.allRecords=1").getCQLSortNode());
  }

}
