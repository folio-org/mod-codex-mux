package org.folio.codex.comparator;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.z3950.zing.cql.CQLSortNode;
import org.z3950.zing.cql.Modifier;
import org.z3950.zing.cql.ModifierSet;

import org.folio.rest.jaxrs.model.Instance;

public class InstanceComparator {

  private InstanceComparator() {
    throw new IllegalStateException("InstanceComparator");
  }

  private static int compare(String comparisonString1, String comparisonString2) {
    final String s1 = comparisonString1 != null ? comparisonString1 : "";
    final String s2 = comparisonString2 != null ? comparisonString2 : "";
    return s1.compareToIgnoreCase(s2);
  }

  public static Comparator<Instance> get(CQLSortNode sortNode) {

    if (sortNode == null) {
      return null;
    }

    Comparator<Instance> instanceComparator = null;
    Iterator<ModifierSet> iterator = sortNode.getSortIndexes().iterator();

    if (iterator.hasNext()) {
      ModifierSet modifierSet = iterator.next();

      final String index = modifierSet.getBase();

      if ("title".equals(index)) {
        instanceComparator = (Instance i1, Instance i2) -> compare(i1.getTitle(), i2.getTitle());
      } else if ("date".equals(index)) {
        instanceComparator = (Instance i1, Instance i2) -> compare(i1.getDate(), i2.getDate());
      } else if ("id".equals(index)) {
        instanceComparator = (Instance i1, Instance i2) -> compare(i1.getId(), i2.getId());
      } else {
        throw (new IllegalArgumentException("unsupported sort index " + index));
      }
      if(isReverse(modifierSet.getModifiers())){
        instanceComparator = instanceComparator.reversed();
      }
    }
    return instanceComparator;
  }

  private static boolean isReverse(List<Modifier> mods) {
    return mods.stream().anyMatch(modifier -> modifier.getType().startsWith("desc"));
  }
}
