package org.folio.codex.comparator;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.ArrayMatching.arrayContaining;
import static org.junit.Assert.assertNull;

import java.util.Arrays;

import org.junit.Test;

import org.folio.codex.CQLParameters;
import org.folio.codex.exception.QueryValidationException;
import org.folio.rest.jaxrs.model.Instance;

public class InstancesComparatorTest {
  private Instance a = new Instance().withTitle("a").withId("3").withDate("2007");
  private Instance b = new Instance().withTitle("b").withId("1").withDate("2008");
  private Instance c = new Instance().withTitle("c").withId("2").withDate("2006");

  private Instance [] sort(String cql, Instance ... instances) {

      CQLParameters<Instance> cqlParameters = new CQLParameters<>(cql);
      cqlParameters.setComparator(InstanceComparator.get(cqlParameters.getCQLSortNode()));

      Arrays.sort(instances, cqlParameters.getComparator());
      return instances;
  }

  @Test
  public void sortTitleAscending() {
    assertThat(sort("cql.allRecords=1 sortBy title", c, a, b), is(arrayContaining(a, b, c)));
  }

  @Test
  public void sortIdAscending() {
    assertThat(sort("cql.allRecords=1 sortBy id", c, a, b), is(arrayContaining(b, c, a)));
  }

  @Test
  public void sortDateAscending() {
    assertThat(sort("cql.allRecords=1 sortBy date", c, a, b), is(arrayContaining(c, a, b)));
  }

  @Test
  public void sortTitleDescending() {
    assertThat(sort("cql.allRecords=1 sortBy title/desc", c, a, b), is(arrayContaining(c, b, a)));
  }

  @Test
  public void sortIdDescending() {
    assertThat(sort("cql.allRecords=1 sortBy id/descending", c, a, b), is(arrayContaining(a, c, b)));
  }

  @Test
  public void sortDateDescending() {
    assertThat(sort("cql.allRecords=1 sortBy date/descending", c, a, b), is(arrayContaining(b, a, c)));
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldThrowExceptionWhenNotSupportedIndex() {
    sort("cql.allRecords=1 sortBy invalid");
  }

  @Test(expected = QueryValidationException.class)
  public void shouldThrowExceptionWhenInvalidQuery() {
    sort("(cql.allRecords=1) sort By invalid");
  }

  @Test
  public void shouldNotReturnComparatorWhenQueryIsNull() {
    assertNull(new CQLParameters<>("cql.allRecords=1").getCQLSortNode());
  }

}
