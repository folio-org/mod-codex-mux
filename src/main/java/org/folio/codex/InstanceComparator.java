package org.folio.codex;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import org.folio.rest.jaxrs.model.Instance;
import org.z3950.zing.cql.CQLSortNode;
import org.z3950.zing.cql.Modifier;
import org.z3950.zing.cql.ModifierSet;

public class InstanceComparator {

  private InstanceComparator() {
    throw new IllegalStateException("InstanceComparator");
  }

  private static int cmp(String i1, String i2, boolean fReverse) {
    final String s1 = i1 != null ? i1 : "";
    final String s2 = i2 != null ? i2 : "";
    final int r = s1.compareToIgnoreCase(s2);
    return fReverse ? -r : r;
  }

  static Comparator<Instance> get(CQLSortNode sn) {
    Comparator<Instance> comp = null;
    Iterator<ModifierSet> it = sn.getSortIndexes().iterator();
    if (it.hasNext()) {
      ModifierSet s = it.next();
      List<Modifier> mods = s.getModifiers();
      boolean reverse = false;
      for (Modifier mod : mods) {
        if (mod.getType().startsWith("desc")) {
          reverse = true;
        }
      }
      final boolean fReverse = reverse;
      final String index = s.getBase();

      if ("title".equals(index)) {
        comp = (Instance i1, Instance i2) -> {
          return cmp(i1.getTitle(), i2.getTitle(), fReverse);
        };
      } else if ("date".equals(index)) {
        comp = (Instance i1, Instance i2) -> {
          return cmp(i1.getDate(), i2.getDate(), fReverse);
        };
      } else if ("id".equals(index)) {
        comp = (Instance i1, Instance i2) -> {
          return cmp(i1.getId(), i2.getId(), fReverse);
        };
      } else {
        throw (new IllegalArgumentException("unsupported sort index " + index));
      }
    }
    return comp;
  }
}
