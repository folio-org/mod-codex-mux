package org.folio.codex;

import org.z3950.zing.cql.CQLDefaultNodeVisitor;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLSortNode;

public class CQLInspect extends CQLDefaultNodeVisitor {

  CQLSortNode sortNode;

  public CQLInspect() {
    sortNode = null;
  }

  @Override
  public void onSortNode(CQLSortNode cqlsn) {
    sortNode = cqlsn;
  }

  static CQLSortNode getSort(CQLNode top) {
    CQLInspect t = new CQLInspect();
    top.traverse(t);
    return t.sortNode;
  }
}
