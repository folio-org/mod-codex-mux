package org.folio.codex;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.response.Header;
import com.jayway.restassured.response.Response;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.util.HashSet;
import java.util.Set;
import org.folio.rest.RestVerticle;
import org.junit.Test;

@RunWith(VertxUnitRunner.class)
public class MuxTest {
  static {
    System.setProperty(LoggerFactory.LOGGER_DELEGATE_FACTORY_CLASS_NAME, "io.vertx.core.logging.Log4jLogDelegateFactory");
  }

  private final int portOkapi = 9030;
  private final int portCodex = 9031;
  private final Logger logger = LoggerFactory.getLogger("codex.mux");
  private Set<String> enabledModules = new HashSet<>();

  private final Header tenantHeader = new Header("X-Okapi-Tenant", "testlib");
  private final Header urlHeader = new Header("X-Okapi-Url", "http://localhost:" + portOkapi);
  private final Header urlHeaderBad = new Header("X-Okapi-Url", "http://foo.bar");

  Vertx vertx;

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();
    setupMux(context, context.async());
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
      .requestHandler(router::accept)
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
    HttpClient client = vertx.createHttpClient();
    String url = "http://localhost:" + portCodex + ctx.request().path();
    if (ctx.request().query() != null) {
      url += "?" + ctx.request().query();
    }
    logger.info("RELAY " + ctx.request().absoluteURI() + " -> " + url);
    HttpClientRequest req = client.getAbs(url, res -> {
      ctx.response().setStatusCode(res.statusCode());
      ctx.response().headers().setAll(res.headers());
      ctx.response().setChunked(true);
      res.handler(r -> {
        ctx.response().write(r);
      });
      res.endHandler(r -> ctx.response().end());
    });
    req.exceptionHandler(r -> {
      ctx.response().setStatusCode(500);
      ctx.response().end(r.getMessage());
    });
    req.headers().setAll(ctx.request().headers());
    req.setChunked(true);
    req.end();
  }

  private void handlerMiniOkapi(RoutingContext ctx) {
    final String tenant = ctx.request().getParam("tenant");
    final String interfaceId = ctx.request().getParam("interfaceId");

    ctx.request().endHandler(x -> {
      if (!"testlib".equals(tenant)) {
        org.folio.okapi.common.HttpResponse.responseError(ctx, 404, tenant);
      } else if (!"codex".equals(interfaceId)) {
        org.folio.okapi.common.HttpResponse.responseError(ctx, 404, interfaceId);
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
    vertx.close(context.asyncAssertSuccess(res -> {
      async.complete();
    }));
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
    context.assertEquals(3, j.getInteger("totalRecords"));

    RestAssured.given()
      .header("X-Okapi-Module-ID", "mock1")
      .header(tenantHeader)
      .get("/codex-instances2")
      .then()
      .log().ifValidationFails()
      .statusCode(400); // would have expected 404 for RMB

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
    b = r.getBody().asString();
    j = new JsonObject(b);
    a = j.getJsonArray("instances");
    context.assertEquals(10, a.size());
    context.assertEquals("10000000", a.getJsonObject(0).getString("id"));
    context.assertEquals("10000001", a.getJsonObject(1).getString("id"));
    context.assertEquals("10000010", a.getJsonObject(2).getString("id"));
    context.assertEquals("10000011", a.getJsonObject(3).getString("id"));
    context.assertEquals("10000017", a.getJsonObject(9).getString("id"));

    r = RestAssured.given()
      .header("X-Okapi-Module-ID", "mock2")
      .header(tenantHeader)
      .get("/codex-instances?query=a sortby title / descending")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();
    b = r.getBody().asString();
    j = new JsonObject(b);
    a = j.getJsonArray("instances");
    context.assertEquals(10, a.size());
    context.assertEquals("10000009", a.getJsonObject(0).getString("id"));
    context.assertEquals("10000008", a.getJsonObject(1).getString("id"));
    context.assertEquals("10000018", a.getJsonObject(9).getString("id"));

    r = RestAssured.given()
      .header("X-Okapi-Module-ID", "mock1")
      .header(tenantHeader)
      .get("/codex-instances?query=a sortby date / descending")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();
    b = r.getBody().asString();
    j = new JsonObject(b);
    a = j.getJsonArray("instances");
    context.assertEquals(3, a.size());
    context.assertEquals("11224466", a.getJsonObject(0).getString("id"));
    context.assertEquals("11224467", a.getJsonObject(1).getString("id"));
    context.assertEquals("73090924", a.getJsonObject(2).getString("id"));

    r = RestAssured.given()
      .header("X-Okapi-Module-ID", "mock2")
      .header(tenantHeader)
      .get("/codex-instances")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();
    b = r.getBody().asString();
    j = new JsonObject(b);
    context.assertEquals(20, j.getInteger("totalRecords"));

    RestAssured.given()
      .header("X-Okapi-Module-ID", "mock2")
      .header(tenantHeader)
      .get("/codex-instances2")
      .then()
      .log().ifValidationFails()
      .statusCode(400); // would have expected 404 for RMB

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
    JsonObject j;
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
      .statusCode(401).extract().response();

    r = RestAssured.given()
      .header(tenantHeader)
      .header(urlHeader)
      .get("/codex-instances")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();

    b = r.getBody().asString();
    j = new JsonObject(b);
    context.assertEquals(0, j.getInteger("totalRecords"));

    RestAssured.given()
      .header(tenantHeader)
      .get("/codex-instances/11224467")
      .then()
      .log().ifValidationFails()
      .statusCode(401);

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
    context.assertEquals(0, j.getInteger("totalRecords"));

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
    context.assertEquals(3, j.getInteger("totalRecords"));
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
    context.assertEquals(3, j.getInteger("totalRecords"));
    a = j.getJsonArray("instances");
    context.assertEquals(3, a.size());
    context.assertEquals("73090924", a.getJsonObject(0).getString("id"));
    context.assertEquals("11224466", a.getJsonObject(1).getString("id"));
    context.assertEquals("11224467", a.getJsonObject(2).getString("id"));

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
    context.assertEquals(23, j.getInteger("totalRecords"));
    a = j.getJsonArray("instances");
    context.assertEquals(10, a.size());
    context.assertEquals("11224466", a.getJsonObject(0).getString("id"));
    context.assertEquals("10000000", a.getJsonObject(1).getString("id"));
    context.assertEquals("11224467", a.getJsonObject(2).getString("id"));
    context.assertEquals("10000001", a.getJsonObject(3).getString("id"));

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
    context.assertEquals(23, j.getInteger("totalRecords"));
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
    context.assertEquals(23, j.getInteger("totalRecords"));
    a = j.getJsonArray("instances");
    context.assertEquals(10, a.size());
    context.assertEquals("73090924", a.getJsonObject(0).getString("id"));
    context.assertEquals("11224466", a.getJsonObject(1).getString("id"));
    context.assertEquals("11224467", a.getJsonObject(2).getString("id"));
    context.assertEquals("10000000", a.getJsonObject(3).getString("id"));
    context.assertEquals("10000001", a.getJsonObject(4).getString("id"));

    // bad X-Okapi-Url
    RestAssured.given()
      .header(tenantHeader)
      .header(urlHeaderBad)
      .get("/codex-instances?query=foo")
      .then()
      .log().ifValidationFails()
      .statusCode(401).extract().response();
  }

}
