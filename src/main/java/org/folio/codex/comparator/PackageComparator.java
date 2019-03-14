package org.folio.codex.comparator;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.z3950.zing.cql.CQLSortNode;
import org.z3950.zing.cql.Modifier;
import org.z3950.zing.cql.ModifierSet;

import org.folio.rest.jaxrs.model.Package;

public class PackageComparator {

  private PackageComparator() {
    throw new IllegalStateException("PackageComparator");
  }

  private static int compare(String comparisonString1, String comparisonString2) {
    final String s1 = comparisonString1 != null ? comparisonString1 : "";
    final String s2 = comparisonString2 != null ? comparisonString2 : "";
    return s1.compareToIgnoreCase(s2);
  }

  public static Comparator<Package> get(CQLSortNode sortNode) {

    if (sortNode == null) {
      return null;
    }

    Comparator<Package> packageComparator = null;
    Iterator<ModifierSet> iterator = sortNode.getSortIndexes().iterator();

    if (iterator.hasNext()) {
      ModifierSet modifierSet = iterator.next();

      final String index = modifierSet.getBase();

      if ("name".equals(index)) {
        packageComparator = (Package package1, Package package2) -> compare(package1.getName(), package2.getName());
      } else {
        throw (new IllegalArgumentException("unsupported sort index " + index));
      }
      if(isReverse(modifierSet.getModifiers())){
        packageComparator = packageComparator.reversed();
      }
    }
    return packageComparator;
  }

  private static boolean isReverse(List<Modifier> modifierList) {
    return modifierList.stream().anyMatch(modifier -> modifier.getType().startsWith("desc"));
  }
}
