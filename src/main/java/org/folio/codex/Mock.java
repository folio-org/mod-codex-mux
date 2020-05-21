package org.folio.codex;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLParseException;
import org.z3950.zing.cql.CQLParser;
import org.z3950.zing.cql.CQLSortNode;

import org.folio.codex.comparator.InstanceComparator;
import org.folio.rest.jaxrs.model.Contributor;
import org.folio.rest.jaxrs.model.Diagnostic;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstanceCollection;
import org.folio.rest.jaxrs.model.ResultInfo;
import org.folio.rest.jaxrs.resource.CodexInstances;

@java.lang.SuppressWarnings({"squid:S1192", "squid:S1199"})
public class Mock implements CodexInstances {

  private static Logger logger = LoggerFactory.getLogger("codex.mock");

  List<Instance> mInstances = new LinkedList<>();

  private String id;

  private Instance createHowToProgramAComputer() {
    Instance e = new Instance();
    e.setTitle("How to program a computer");
    e.setPublisher("Penguin");
    Set<Contributor> cs = new LinkedHashSet<>();
    Contributor c = new Contributor();
    c.setName("Jack Collins");
    c.setType("Personal name");
    cs.add(c);
    e.setContributor(cs);
    e.setDate("1991");
    return e;
  }
  public Mock(String id) {
    this.id = id;
    logger.info("Mock " + id + " starting");
    if (id.equals("mock1")) {
      {
        Instance e = createHowToProgramAComputer();
        e.setId("11224466");
        mInstances.add(e);
      }
      {
        Instance e = createHowToProgramAComputer();
        e.setId("11224467");
        mInstances.add(e);
      }

      {
        Instance e = new Instance();
        e.setTitle("Computer processing of dynamic images from an Anger scintillation camera");
        e.setPublisher("Society of Nuclear Medicine");
        Contributor c = new Contributor();
        Set<Contributor> cs = new LinkedHashSet<>();
        c.setName("Larson, Kenneth B.");
        c.setType("Personal name");
        cs.add(c);

        c = new Contributor();
        c.setName("Cox, Jerome R.");
        c.setType("Personal name");
        cs.add(c);

        e.setContributor(cs);
        e.setDate("1971");
        e.setId("73090924");
        mInstances.add(e);
      }
    } else if (id.equals("mock2")) {
      for (int i = 0; i < 20; i++) {
        Instance e = new Instance();
        e.setTitle("How to program a computer volume " + i);
        e.setPublisher("Penguin");
        Set<Contributor> cs = new LinkedHashSet<>();
        Contributor c = new Contributor();
        c.setName("Jack Collins");
        c.setType("Personal name");
        cs.add(c);
        e.setContributor(cs);
        e.setDate(Integer.toString(2010 - i));
        e.setId(Integer.toString(10000000 + i));
        mInstances.add(e);
      }
    }
  }

  @Override
  public void getCodexInstances(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    List<Instance> iInstances = new LinkedList<>(mInstances);
    if (query != null) {
      CQLParser parser = new CQLParser(CQLParser.V1POINT2);
      CQLNode top;
      if (query.startsWith("diag")) { // be able to return diagnostic
        Diagnostic d = new Diagnostic();
        d.setCode("unknown index");
        d.setMessage("diag");
        d.setSource(id);
        InstanceCollection coll = new InstanceCollection();
        ResultInfo resultInfo = new ResultInfo().withTotalRecords(0);
        coll.setResultInfo(resultInfo);
        resultInfo.getDiagnostics().add(d);
        asyncResultHandler.handle(
          Future.succeededFuture(CodexInstances.GetCodexInstancesResponse.respond200WithApplicationJson(coll)));
        return;
      }
      try {
        top = parser.parse(query);
      } catch (CQLParseException ex) {
        logger.warn("CQLParseException: " + ex.getMessage());
        asyncResultHandler.handle(
          Future.succeededFuture(CodexInstances.GetCodexInstancesResponse.respond400WithTextPlain(ex.getMessage())));
        return;
      } catch (IOException ex) {
        asyncResultHandler.handle(
          Future.succeededFuture(CodexInstances.GetCodexInstancesResponse.respond500WithTextPlain(ex.getMessage())));
        return;
      }
      // be able to provoke 400 for mock prefix
      if (query.startsWith("mock") && !query.equals(id)) {
        asyncResultHandler.handle(
          Future.succeededFuture(CodexInstances.GetCodexInstancesResponse.respond400WithTextPlain("provoked unsupported " + query)));
        return;
      }
      CQLSortNode sn = CQLInspect.getSort(top);
      if (sn != null) {
        try {
          iInstances.sort(InstanceComparator.get(sn));
        } catch (IllegalArgumentException ex) {
          asyncResultHandler.handle(
            Future.succeededFuture(CodexInstances.GetCodexInstancesResponse.respond400WithTextPlain(ex.getMessage())));
          return;
        }
      }
    }
    InstanceCollection coll = new InstanceCollection();
    Iterator<Instance> it = iInstances.iterator();
    for (int i = 0; i < offset && it.hasNext(); i++) {
      it.next();
    }
    List<Instance> n = new LinkedList<>();
    for (int i = 0; i < limit && it.hasNext(); i++) {
      n.add(it.next());
    }
    coll.setInstances(n);
    ResultInfo resultInfo = new ResultInfo().withTotalRecords(iInstances.size());
    coll.setResultInfo(resultInfo);

    asyncResultHandler.handle(Future.succeededFuture(CodexInstances.GetCodexInstancesResponse.respond200WithApplicationJson(coll)));
  }

  @Override
  public void getCodexInstancesById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    logger.info("codex.mock getCodexInstancesById " + id);
    for (Instance e : mInstances) {
      if (e.getId() != null && e.getId().equals(id)) {
        asyncResultHandler.handle(Future.succeededFuture(CodexInstances.GetCodexInstancesByIdResponse.respond200WithApplicationJson(e)));
        return;
      }
    }
    asyncResultHandler.handle(Future.succeededFuture(CodexInstances.GetCodexInstancesByIdResponse.respond404WithTextPlain(id)));
  }
}
