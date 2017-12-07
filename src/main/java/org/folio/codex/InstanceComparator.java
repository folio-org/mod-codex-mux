package org.folio.codex;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import org.folio.rest.jaxrs.model.Instance;
import org.z3950.zing.cql.CQLSortNode;
import org.z3950.zing.cql.Modifier;
import org.z3950.zing.cql.ModifierSet;

public class InstanceComparator {

  static Comparator<Instance> get(CQLSortNode sn) throws IllegalArgumentException {
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
        comp = new Comparator<Instance>() {
          @Override
          public int compare(Instance i1, Instance i2) {
            String s1 = i1.getTitle() != null ? i1.getTitle() : "";
            String s2 = i1.getTitle() != null ? i2.getTitle() : "";
            int r = s1.compareToIgnoreCase(s2);
            return fReverse ? -r : r;
          }
        };
      } else if ("date".equals(index)) {
        comp = new Comparator<Instance>() {
          @Override
          public int compare(Instance i1, Instance i2) {
            String s1 = i1.getDate() != null ? i1.getDate() : "";
            String s2 = i1.getDate() != null ? i2.getDate() : "";
            int r = s1.compareToIgnoreCase(s2);
            return fReverse ? -r : r;
          }
        };
      } else if ("id".equals(index)) {
        comp = new Comparator<Instance>() {
          @Override
          public int compare(Instance i1, Instance i2) {
            String s1 = i1.getId() != null ? i1.getId() : "";
            String s2 = i1.getId() != null ? i2.getId() : "";
            int r = s1.compareToIgnoreCase(s2);
            return fReverse ? -r : r;
          }
        };
      } else {
        throw (new IllegalArgumentException("unsupported sort index " + index));
      }
    }
    return comp;
  }
}
