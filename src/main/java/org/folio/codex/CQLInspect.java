package org.folio.codex;

import org.z3950.zing.cql.CQLBooleanNode;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLNodeVisitor;
import org.z3950.zing.cql.CQLPrefixNode;
import org.z3950.zing.cql.CQLRelation;
import org.z3950.zing.cql.CQLSortNode;
import org.z3950.zing.cql.CQLTermNode;

public class CQLInspect implements CQLNodeVisitor {

  CQLSortNode sortNode;

  public CQLInspect() {
    sortNode = null;
  }

  @Override
  public void onSortNode(CQLSortNode cqlsn) {
    sortNode = cqlsn;
  }

  @Override
  public void onPrefixNode(CQLPrefixNode cqlpn) {
  }

  @Override
  public void onBooleanNodeStart(CQLBooleanNode cqlbn) {
  }

  @Override
  public void onBooleanNodeOp(CQLBooleanNode cqlbn) {
  }

  @Override
  public void onBooleanNodeEnd(CQLBooleanNode cqlbn) {
  }

  @Override
  public void onTermNode(CQLTermNode cqltn) {
  }

  @Override
  public void onRelation(CQLRelation cqlr) {
  }

  static CQLSortNode getSort(CQLNode top) {
    CQLInspect t = new CQLInspect();
    top.traverse(t);
    return t.sortNode;
  }

}
