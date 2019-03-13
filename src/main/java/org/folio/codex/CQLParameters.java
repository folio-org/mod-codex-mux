package org.folio.codex;

import java.io.IOException;
import java.util.Comparator;
import java.util.Objects;

import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLParseException;
import org.z3950.zing.cql.CQLParser;
import org.z3950.zing.cql.CQLSortNode;

import org.folio.codex.exception.QueryValidationException;

public class CQLParameters<T> {

  private Comparator<T> comparator;

  public Comparator<T> getComparator() {
    return comparator;
  }

  private CQLNode cqlNode;

  public CQLNode getCqlNode() {
    return cqlNode;
  }

  public CQLSortNode getCQLSortNode() {
    return Objects.isNull(cqlNode) ? null : CQLInspect.getSort(cqlNode);
  }

  public CQLParameters(String query) throws QueryValidationException {
    if (query != null) {
      cqlNode = parseQuery(query);
    }
  }

  public void setComparator(Comparator<T> comparatorClass) {
      this.comparator = comparatorClass;
  }

  private CQLNode parseQuery(String query) throws QueryValidationException {
    final CQLParser parser = new CQLParser(CQLParser.V1POINT2);
    try {
      return parser.parse(query);
    } catch (CQLParseException | IOException e) {
      throw new QueryValidationException("Unsupported Query Format : Search query is in an unsupported format.", e);
    }

  }



}
