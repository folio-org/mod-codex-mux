package org.folio.codex;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.Diagnostic;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstanceCollection;

@RunWith(VertxUnitRunner.class)
public class MuxTest {
  

  private final int portOkapi = 9030;
  private final int portCodex = 9031;
  private final Logger logger = LogManager.getLogger("codex.mux");
  private final Set<String> enabledModules = new HashSet<>();

  private final Header tenantHeader = new Header("X-Okapi-Tenant", "testlib");
  private final Header urlHeader = new Header("X-Okapi-Url", "http://localhost:" + portOkapi);
  private final Header urlHeaderBad = new Header("X-Okapi-Url", "http://foo.bar");

  Vertx vertx;

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();

    // Register the context exception handler to catch assertThat
    vertx.exceptionHandler(context.exceptionHandler());

    setupMux(context, context.async());
  }

  @After
  public void after(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  private void setupMux(TestContext context, Async async) {
    JsonObject conf = new JsonObject();
    conf.put("http.port", portCodex);
    DeploymentOptions opt = new DeploymentOptions()
      .setConfig(conf);
    vertx.deployVerticle(RestVerticle.class.getName(), opt,
      r -> {
        context.assertTrue(r.succeeded());
        setupMiniOkapi(context, async);
      });
  }

  private void setupMiniOkapi(TestContext context, Async async) {
    Router router = Router.router(vertx);
    router.get("/_/proxy/tenants/:tenant/interfaces/:interfaceId").handler(this::handlerMiniOkapi);
    router.getWithRegex("/codex-instances.*").handler(this::handlerProxy);

    HttpServerOptions so = new HttpServerOptions().setHandle100ContinueAutomatically(true);
    vertx.createHttpServer(so)
      .requestHandler(router)
      .listen(
        portOkapi,
        result -> {
          if (result.failed()) {
            context.fail(result.cause());
          }
          async.complete();
        }
      );
  }

  private void handlerProxy(RoutingContext ctx) {
    WebClient webClient = WebClient.create(vertx);

    String url = "http://localhost:" + portCodex + ctx.request().path();
    if (ctx.request().query() != null) {
      url += "?" + ctx.request().query();
    }
    logger.info("RELAY {} -> {}", ctx.request().absoluteURI(), url);

    HttpRequest<Buffer> req = webClient.getAbs(url);

    req.putHeaders(ctx.request().headers());

    req.send().onSuccess(res -> {
      ctx.response().setStatusCode(res.statusCode());
      ctx.response().headers().setAll(res.headers());
      ctx.response().end(res.body());
    }).onFailure(t -> {
      ctx.response().setStatusCode(500);
      ctx.response().end(t.getMessage());
    });
  }

  private void handlerMiniOkapi(RoutingContext ctx) {
    final String tenant = ctx.request().getParam("tenant");
    final String interfaceId = ctx.request().getParam("interfaceId");

    ctx.request().endHandler(x -> {
      if (!"testlib".equals(tenant)) {
        ctx.response().setStatusCode(404).end(tenant);
      } else if (!"codex".equals(interfaceId)) {
        ctx.response().setStatusCode(404).end(interfaceId);
      } else {
        org.folio.okapi.common.HttpResponse.responseJson(ctx, 200);
        JsonArray a = new JsonArray();
        for (String m : enabledModules) {
          JsonObject j = new JsonObject();
          j.put("id", m);
          a.add(j);
        }
        ctx.response().end(a.encodePrettily());
      }
    });
  }

  @After
  public void tearDown(TestContext context) {
    Async async = context.async();
    vertx.close(context.asyncAssertSuccess(res -> async.complete()));
  }

  /**
   * Assert that the response contains the instances field that
   * is an array where each array element contains the id field
   * and these match expectedIds in any order.
   * @param response - to check
   * @param expectedIds - to compare to
   */
  private void assertInstanceIds(Response response, String ... expectedIds) {
    String body = response.getBody().asString();
    JsonObject jsonObject = new JsonObject(body);
    JsonArray array = jsonObject.getJsonArray("instances");
    String [] ids = new String [array.size()];
    for (int i=0; i<ids.length; i++) {
      ids[i] = array.getJsonObject(i).getString("id");
    }
    assertThat(Arrays.asList(ids), hasItems(expectedIds));
    assertThat(ids.length, is(expectedIds.length));
  }

  /**
   * Assert that the response contains the instances field that
   * is an array where each array element contains the id field
   * and these match expectedIds in that order.
   * @param response - to check
   * @param expectedIds - to compare to
   */
  private void assertInstanceIdsSorted(Response response, String ... expectedIds) {
    String body = response.getBody().asString();
    JsonObject jsonObject = new JsonObject(body);
    JsonArray array = jsonObject.getJsonArray("instances");
    String [] ids = new String [array.size()];
    for (int i=0; i<ids.length; i++) {
      ids[i] = array.getJsonObject(i).getString("id");
    }
    assertThat(ids, is(expectedIds));
  }

  @Test
  public void testMock(TestContext context) {
    Response r;
    String b;
    JsonObject j;
    JsonArray a;

    logger.info("testMock");
    RestAssured.port = portCodex;
    r = RestAssured.given()
      .header("X-Okapi-Module-ID", "mock1")
      .header(tenantHeader)
      .get("/codex-instances")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();
    b = r.getBody().asString();
    j = new JsonObject(b);
    context.assertEquals(3, j.getJsonObject("resultInfo").getInteger("totalRecords"));

    RestAssured.given()
      .header("X-Okapi-Module-ID", "mock1")
      .header(tenantHeader)
      .get("/codex-instances2")
      .then()
      .log().ifValidationFails()
      .statusCode(404); // would have expected 404 for RMB

    r = RestAssured.given()
      .header("X-Okapi-Module-ID", "mock1")
      .header(tenantHeader)
      .get("/codex-instances/11224467")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();

    b = r.getBody().asString();
    j = new JsonObject(b);
    context.assertTrue("11224467".equals(j.getString("id")), b);

    RestAssured.given()
      .header("X-Okapi-Module-ID", "mock1")
      .header(tenantHeader)
      .get("/codex-instances/1234")
      .then()
      .log().ifValidationFails()
      .statusCode(404);

    r = RestAssured.given()
      .header("X-Okapi-Module-ID", "mock1")
      .header(tenantHeader)
      .get("/codex-instances?limit=1&offset=1")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();
    b = r.getBody().asString();
    j = new JsonObject(b);
    a = j.getJsonArray("instances");
    context.assertEquals(a.size(), 1);

    r = RestAssured.given()
      .header("X-Okapi-Module-ID", "mock1")
      .header(tenantHeader)
      .get("/codex-instances?offset=1")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();
    b = r.getBody().asString();
    j = new JsonObject(b);
    a = j.getJsonArray("instances");
    context.assertEquals(a.size(), 2);

    // provoke 400 error in mock (mock prefix in query)
    RestAssured.given()
      .header("X-Okapi-Module-ID", "mock1")
      .header(tenantHeader)
      .get("/codex-instances?query=mock")
      .then()
      .log().ifValidationFails()
      .statusCode(400);

    // provoke syntax error and diagnostic
    r = RestAssured.given()
      .header("X-Okapi-Module-ID", "mock1")
      .header(tenantHeader)
      .get("/codex-instances?query=diag")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();
    b = r.getBody().asString();
    j = new JsonObject(b);
    a = j.getJsonObject("resultInfo").getJsonArray("diagnostics");
    context.assertEquals(a.size(), 1);

    r = RestAssured.given()
      .header("X-Okapi-Module-ID", "mock2")
      .header(tenantHeader)
      .get("/codex-instances?limit=3&offset=5")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();
    b = r.getBody().asString();
    j = new JsonObject(b);
    a = j.getJsonArray("instances");
    context.assertEquals(a.size(), 3);

    r = RestAssured.given()
      .header("X-Okapi-Module-ID", "mock2")
      .header(tenantHeader)
      .get("/codex-instances?query=foo")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();
    b = r.getBody().asString();
    j = new JsonObject(b);
    a = j.getJsonArray("instances");
    context.assertEquals(10, a.size());
    context.assertEquals("10000000", a.getJsonObject(0).getString("id"));
    context.assertEquals("10000001", a.getJsonObject(1).getString("id"));
    context.assertEquals("10000009", a.getJsonObject(9).getString("id"));

    RestAssured.given()
      .header("X-Okapi-Module-ID", "mock2")
      .header(tenantHeader)
      .get("/codex-instances?query=syntaxerror(")
      .then()
      .log().ifValidationFails()
      .statusCode(400).extract().response();

    RestAssured.given()
      .header("X-Okapi-Module-ID", "mock2")
      .header(tenantHeader)
      .get("/codex-instances?query=a sortby unknown")
      .then()
      .log().ifValidationFails()
      .statusCode(400).extract().response();

    r = RestAssured.given()
      .header("X-Okapi-Module-ID", "mock2")
      .header(tenantHeader)
      .get("/codex-instances?query=a sortby title")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();
    assertInstanceIdsSorted(r, "10000000", "10000001", "10000010", "10000011", "10000012",
        "10000013", "10000014", "10000015", "10000016", "10000017");

    r = RestAssured.given()
      .header("X-Okapi-Module-ID", "mock2")
      .header(tenantHeader)
      .get("/codex-instances?query=a sortby title / descending")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();
    assertInstanceIdsSorted(r, "10000009", "10000008", "10000007", "10000006", "10000005",
        "10000004", "10000003", "10000002", "10000019", "10000018");

    r = RestAssured.given()
      .header("X-Okapi-Module-ID", "mock1")
      .header(tenantHeader)
      .get("/codex-instances?query=a sortby date / descending")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();
    assertInstanceIdsSorted(r, "11224466", "11224467", "73090924");

    r = RestAssured.given()
      .header("X-Okapi-Module-ID", "mock2")
      .header(tenantHeader)
      .get("/codex-instances")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();
    b = r.getBody().asString();
    j = new JsonObject(b);
    context.assertEquals(20, j.getJsonObject("resultInfo").getInteger("totalRecords"));

    RestAssured.given()
      .header("X-Okapi-Module-ID", "mock2")
      .header(tenantHeader)
      .get("/codex-instances2")
      .then()
      .log().ifValidationFails()
      .statusCode(404); // would have expected 404 for RMB

    r = RestAssured.given()
      .header("X-Okapi-Module-ID", "mock2")
      .header(tenantHeader)
      .get("/codex-instances/10000000")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();

    b = r.getBody().asString();
    j = new JsonObject(b);
    context.assertTrue("10000000".equals(j.getString("id")), b);

    RestAssured.given()
      .header("X-Okapi-Module-ID", "mock2")
      .header(tenantHeader)
      .get("/codex-instances/1234")
      .then()
      .log().ifValidationFails()
      .statusCode(404);
  }

  @Test
  public void testMiniOkapi(TestContext context) {
    Response r;
    String b;
    JsonArray a;

    logger.info("testMiniOkapi");
    RestAssured.port = portOkapi;

    enabledModules.clear();
    enabledModules.add("mock1");

    r = RestAssured.given()
      .header(tenantHeader)
      .get("/_/proxy/tenants/testlib/interfaces/codex")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();

    b = r.getBody().asString();
    a = new JsonArray(b);
    context.assertEquals(a.size(), 1);
    context.assertEquals(a.getJsonObject(0).getString("id"), "mock1");

    RestAssured.given()
      .header(tenantHeader)
      .get("/_/proxy/tenants/foo/interfaces/codex")
      .then()
      .log().ifValidationFails()
      .statusCode(404);

    RestAssured.given()
      .header(tenantHeader)
      .get("/_/proxy/tenants/testlib/interfaces/bar")
      .then()
      .log().ifValidationFails()
      .statusCode(404);

    RestAssured.given()
      .header(tenantHeader)
      .get("/other")
      .then()
      .log().ifValidationFails()
      .statusCode(404);

  }

  @Test
  public void testMutex(TestContext context) {
    InstanceCollection col;
    List<Diagnostic> diags;
    Response r;
    String b;
    JsonObject j;
    JsonArray a;

    logger.info("testMutex");
    RestAssured.port = portCodex;

    RestAssured.given()
      .get("/codex-instances")
      .then()
      .log().ifValidationFails()
      .statusCode(400).extract().response();

    RestAssured.given()
      .header(tenantHeader)
      .get("/codex-instances")
      .then()
      .log().ifValidationFails()
      .statusCode(400).extract().response();

    r = RestAssured.given()
      .header(tenantHeader)
      .header(urlHeader)
      .get("/codex-instances")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();

    b = r.getBody().asString();
    j = new JsonObject(b);
    context.assertEquals(0, j.getJsonObject("resultInfo").getInteger("totalRecords"));

    RestAssured.given()
      .header(tenantHeader)
      .get("/codex-instances/11224467")
      .then()
      .log().ifValidationFails()
      .statusCode(400);

    RestAssured.given()
      .header(tenantHeader)
      .header(urlHeader)
      .get("/codex-instances/11224467")
      .then()
      .log().ifValidationFails()
      .statusCode(404);

    enabledModules.clear();
    enabledModules.add("mock1");
    enabledModules.add("mod-codex-mutex-1"); // ourselves!!

    RestAssured.given()
      .header(tenantHeader)
      .header(urlHeader)
      .get("/codex-instances/11224467")
      .then()
      .log().ifValidationFails()
      .statusCode(200);

    enabledModules.add("mock2");

    RestAssured.given()
      .header(tenantHeader)
      .header(urlHeader)
      .get("/codex-instances/11224467")
      .then()
      .log().ifValidationFails()
      .statusCode(200);

    RestAssured.given()
      .header(tenantHeader)
      .header(urlHeader)
      .get("/codex-instances/10000010")
      .then()
      .log().ifValidationFails()
      .statusCode(200);

    enabledModules.clear();

    r = RestAssured.given()
      .header(tenantHeader)
      .header(urlHeader)
      .get("/codex-instances?query=foo")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();

    b = r.getBody().asString();
    j = new JsonObject(b);
    context.assertEquals(0, j.getJsonObject("resultInfo").getInteger("totalRecords"));

    RestAssured.given()
      .header(tenantHeader)
      .header(urlHeader)
      .get("/codex-instances?query=syntaxerror(")
      .then()
      .log().ifValidationFails()
      .statusCode(400).extract().response();

    enabledModules.add("mock1");

    r = RestAssured.given()
      .header(tenantHeader)
      .header(urlHeader)
      .get("/codex-instances?query=foo")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();
    b = r.getBody().asString();
    j = new JsonObject(b);
    context.assertEquals(3, j.getJsonObject("resultInfo").getInteger("totalRecords"));
    a = j.getJsonArray("instances");
    context.assertEquals(3, a.size());
    context.assertEquals("11224466", a.getJsonObject(0).getString("id"));
    context.assertEquals("11224467", a.getJsonObject(1).getString("id"));
    context.assertEquals("73090924", a.getJsonObject(2).getString("id"));

    r = RestAssured.given()
      .header(tenantHeader)
      .header(urlHeader)
      .get("/codex-instances?query=foo sortby title")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();
    b = r.getBody().asString();
    j = new JsonObject(b);
    context.assertEquals(3, j.getJsonObject("resultInfo").getInteger("totalRecords"));
    assertInstanceIdsSorted(r, "73090924", "11224466", "11224467");

    enabledModules.add("mock2");

    r = RestAssured.given()
      .header(tenantHeader)
      .header(urlHeader)
      .get("/codex-instances?query=foo")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();

    b = r.getBody().asString();
    j = new JsonObject(b);
    context.assertEquals(23, j.getJsonObject("resultInfo").getInteger("totalRecords"));
    assertInstanceIds(r, "10000000", "11224466", "10000001", "11224467", "10000002",
        "73090924", "10000003", "10000004", "10000005", "10000006");

    r = RestAssured.given()
      .header(tenantHeader)
      .header(urlHeader)
      .get("/codex-instances/11224466")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();
    b = r.getBody().asString();
    j = new JsonObject(b);
    context.assertEquals("11224466", j.getString("id"));

    r = RestAssured.given()
      .header(tenantHeader)
      .header(urlHeader)
      .get("/codex-instances/10000000")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();
    b = r.getBody().asString();
    j = new JsonObject(b);
    context.assertEquals("10000000", j.getString("id"));

    r = RestAssured.given()
      .header(tenantHeader)
      .header(urlHeader)
      .get("/codex-instances?query=foo&offset=15")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();

    b = r.getBody().asString();
    j = new JsonObject(b);
    a = j.getJsonArray("instances");
    context.assertEquals(23, j.getJsonObject("resultInfo").getInteger("totalRecords"));
    context.assertEquals(8, a.size());
    context.assertEquals("10000012", a.getJsonObject(0).getString("id"));
    context.assertEquals("10000019", a.getJsonObject(7).getString("id"));

    r = RestAssured.given()
      .header(tenantHeader)
      .header(urlHeader)
      .get("/codex-instances?query=foo sortby title")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();

    b = r.getBody().asString();
    j = new JsonObject(b);
    context.assertEquals(23, j.getJsonObject("resultInfo").getInteger("totalRecords"));
    a = j.getJsonArray("instances");
    context.assertEquals(10, a.size());
    context.assertEquals("73090924", a.getJsonObject(0).getString("id"));
    context.assertEquals("11224466", a.getJsonObject(1).getString("id"));
    context.assertEquals("11224467", a.getJsonObject(2).getString("id"));
    context.assertEquals("10000000", a.getJsonObject(3).getString("id"));
    context.assertEquals("10000001", a.getJsonObject(4).getString("id"));

    r = RestAssured.given()
      .header(tenantHeader)
      .header(urlHeader)
      .get("/codex-instances?query=foo sortby id")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();

    b = r.getBody().asString();
    col = Json.decodeValue(b, InstanceCollection.class);
    context.assertEquals(23, col.getResultInfo().getTotalRecords());
    diags = col.getResultInfo().getDiagnostics();
    context.assertEquals(2, diags.size());
    context.assertEquals(23, diags.get(0).getRecordCount() + diags.get(1).getRecordCount());

    List<Instance> instances = col.getInstances();
    context.assertEquals(10, instances.size());
    context.assertEquals("10000000", instances.get(0).getId());
    context.assertEquals("10000001", instances.get(1).getId());
    context.assertEquals("10000009", instances.get(9).getId());

    // bad X-Okapi-Url
    RestAssured.given()
      .header(tenantHeader)
      .header(urlHeaderBad)
      .get("/codex-instances?query=foo")
      .then()
      .log().ifValidationFails()
      .statusCode(401).extract().response();

    // all modules will fail
    r = RestAssured.given()
      .header(tenantHeader)
      .header(urlHeader)
      .get("/codex-instances?query=mock")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();
    b = r.getBody().asString();

    col = Json.decodeValue(b, InstanceCollection.class);
    context.assertEquals(0, col.getResultInfo().getTotalRecords());
    diags = col.getResultInfo().getDiagnostics();
    context.assertEquals(2, diags.size());

    // only mock1 will work
    RestAssured.given()
      .header(tenantHeader)
      .header(urlHeader)
      .get("/codex-instances?query=mock1")
      .then()
      .log().ifValidationFails()
      .statusCode(200);

    // only mock2 will work
    RestAssured.given()
      .header(tenantHeader)
      .header(urlHeader)
      .get("/codex-instances?query=mock2")
      .then()
      .log().ifValidationFails()
      .statusCode(200);

    // get diagnostic from both
    r = RestAssured.given()
      .header(tenantHeader)
      .header(urlHeader)
      .get("/codex-instances?query=diag")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();
    b = r.getBody().asString();

    col = Json.decodeValue(b, InstanceCollection.class);
    context.assertEquals(0, col.getResultInfo().getTotalRecords());
    diags = col.getResultInfo().getDiagnostics();
    context.assertEquals(2, diags.size());
    context.assertTrue(Arrays.asList(diags.get(0).getSource(), diags.get(1).getSource())
      .containsAll(Arrays.asList("mock1", "mock2")));
    context.assertEquals(0, diags.get(0).getRecordCount());

    r = RestAssured.given()
      .header(tenantHeader)
      .header(urlHeader)
      .get("/codex-instances?query=foo and source=mock1 sortby id")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();

    b = r.getBody().asString();

    col = Json.decodeValue(b, InstanceCollection.class);
    context.assertEquals(3, col.getResultInfo().getTotalRecords());
    diags = col.getResultInfo().getDiagnostics();
    context.assertEquals(1, diags.size());
    context.assertEquals("mock1", diags.get(0).getSource());
    context.assertEquals(3, diags.get(0).getRecordCount());
    context.assertEquals("foo sortby id", diags.get(0).getQuery());

    r = RestAssured.given()
      .header(tenantHeader)
      .header(urlHeader)
      .get("/codex-instances?query=foo and source=mock2 sortby id")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();

    b = r.getBody().asString();

    col = Json.decodeValue(b, InstanceCollection.class);
    context.assertEquals(20, col.getResultInfo().getTotalRecords());
    diags = col.getResultInfo().getDiagnostics();
    context.assertEquals(1, diags.size());
    context.assertEquals("mock2", diags.get(0).getSource());
    context.assertEquals(20, diags.get(0).getRecordCount());
    context.assertEquals("foo sortby id", diags.get(0).getQuery());

    r = RestAssured.given()
      .header(tenantHeader)
      .header(urlHeader)
      .get("/codex-instances?query=foo and source=mock3 sortby id")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();

    b = r.getBody().asString();

    col = Json.decodeValue(b, InstanceCollection.class);
    context.assertEquals(0, col.getResultInfo().getTotalRecords());
    diags = col.getResultInfo().getDiagnostics();
    context.assertEquals(0, diags.size());

    r = RestAssured.given()
      .header(tenantHeader)
      .header(urlHeader)
      .get("/codex-instances")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();
    b = r.getBody().asString();
    col = Json.decodeValue(b, InstanceCollection.class);
    context.assertEquals(23, col.getResultInfo().getTotalRecords());

  }

}
