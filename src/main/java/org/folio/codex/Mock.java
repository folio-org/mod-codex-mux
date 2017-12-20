package org.folio.codex;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
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
import org.folio.rest.jaxrs.model.Contributor;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstanceCollection;
import org.folio.rest.jaxrs.resource.CodexInstancesResource;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLParseException;
import org.z3950.zing.cql.CQLParser;
import org.z3950.zing.cql.CQLSortNode;

@java.lang.SuppressWarnings({"squid:S1192", "squid:S1199"})
public class Mock implements CodexInstancesResource {

  private static Logger logger = LoggerFactory.getLogger("codex.mock");

  List<Instance> mInstances = new LinkedList<>();

  private String id;

  public Mock(String id) {
    this.id = id;
    logger.info("Mock " + id + " starting");
    if (id.equals("mock1")) {
      {
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
        e.setId("11224466");
        mInstances.add(e);
      }
      {
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
        e.setTitle("How to program a computer volume " + Integer.toString(i));
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
  public void getCodexInstances(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    List<Instance> iInstances = new LinkedList<>();
    iInstances.addAll(mInstances);
    if (query != null) {
      CQLParser parser = new CQLParser(CQLParser.V1POINT2);
      CQLNode top = null;
      try {
        top = parser.parse(query);
      } catch (CQLParseException ex) {
        logger.warn("CQLParseException: " + ex.getMessage());
        asyncResultHandler.handle(
          Future.succeededFuture(CodexInstancesResource.GetCodexInstancesResponse.withPlainBadRequest(ex.getMessage())));
        return;
      } catch (IOException ex) {
        asyncResultHandler.handle(
          Future.succeededFuture(CodexInstancesResource.GetCodexInstancesResponse.withPlainInternalServerError(ex.getMessage())));
        return;
      }
      // be able to provoke 400 for mock prefix
      if (query.startsWith("mock") && !query.equals(id)) {
        asyncResultHandler.handle(
          Future.succeededFuture(CodexInstancesResource.GetCodexInstancesResponse.withPlainBadRequest("provoked unsupported " + query)));
        return;
      }
      CQLSortNode sn = CQLInspect.getSort(top);
      if (sn != null) {
        try {
          Comparator<Instance> comp = InstanceComparator.get(sn);
          Collections.sort(iInstances, comp);
        } catch (IllegalArgumentException ex) {
          asyncResultHandler.handle(
            Future.succeededFuture(CodexInstancesResource.GetCodexInstancesResponse.withPlainBadRequest(ex.getMessage())));
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
    coll.setTotalRecords(iInstances.size());

    asyncResultHandler.handle(Future.succeededFuture(CodexInstancesResource.GetCodexInstancesResponse.withJsonOK(coll)));
  }

  @Override
  public void getCodexInstancesById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    logger.info("codex.mock getCodexInstancesById " + id);
    for (Instance e : mInstances) {
      if (e.getId() != null && e.getId().equals(id)) {
        asyncResultHandler.handle(Future.succeededFuture(CodexInstancesResource.GetCodexInstancesByIdResponse.withJsonOK(e)));
        return;
      }
    }
    asyncResultHandler.handle(Future.succeededFuture(CodexInstancesResource.GetCodexInstancesByIdResponse.withPlainNotFound(id)));
  }

}
