package org.folio.codex;

import static org.hamcrest.collection.IsArrayContainingInOrder.arrayContaining;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.Arrays;
import java.util.Comparator;

import org.folio.rest.jaxrs.model.Instance;
import org.junit.Test;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLParser;
import org.z3950.zing.cql.CQLSortNode;

public class InstanceComparatorTest {
  Instance a = new Instance().withTitle("a");
  Instance b = new Instance().withTitle("b");
  Instance c = new Instance().withTitle("c");

  private Instance [] sort(String cql, Instance ... instances) {
    try {
      CQLParser parser = new CQLParser();
      CQLNode top = parser.parse(cql);
      CQLSortNode sortNode = CQLInspect.getSort(top);
      Comparator<Instance> comparator = InstanceComparator.get(sortNode);
      Arrays.sort(instances, comparator);
      return instances;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void sortAscending() {
    assertThat(sort("cql.allRecords=1 sortBy title", c, a, b), is(arrayContaining(a, b, c)));
  }

  @Test
  public void sortDescription() {
    assertThat(sort("cql.allRecords=1 sortBy title/description", c, a, b), is(arrayContaining(a, b, c)));
  }

  @Test
  public void sortDescending() {
    assertThat(sort("cql.allRecords=1 sortBy title/sort.descending", c, a, b), is(arrayContaining(c, b, a)));
  }
}
