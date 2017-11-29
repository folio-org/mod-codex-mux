package org.folio.codex;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.response.Header;
import com.jayway.restassured.response.Response;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
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

  private final Header tenantHeader = new Header("X-Okapi-Tenant", "testlib");
  private final int portOkapi = 9030;
  private final int portMux = 9031;
  private final int portMock1 = 9032;
  private final int portMock2 = 9033;
  private final Logger logger = LoggerFactory.getLogger("codex.mux");
  private Set<String> enabledModules = new HashSet<>();

  Vertx vertx;

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();
    setupMock1(context, context.async());
  }

  private void setupMock1(TestContext context, Async async) {
    JsonObject conf = new JsonObject();
    conf.put("http.port", portMock1);
    conf.put("codex.mode", "mock1");
    DeploymentOptions opt = new DeploymentOptions()
      .setConfig(conf);
    vertx.deployVerticle(RestVerticle.class.getName(), opt,
      r -> setupMock2(context, async));
  }

  private void setupMock2(TestContext context, Async async) {
    JsonObject conf = new JsonObject();
    conf.put("http.port", portMock2);
    conf.put("codex.mode", "mock2");
    DeploymentOptions opt = new DeploymentOptions()
      .setConfig(conf);
    vertx.deployVerticle(RestVerticle.class.getName(), opt,
      r -> setupMux(context, async));
  }

  private void setupMux(TestContext context, Async async) {
    JsonObject conf = new JsonObject();
    conf.put("http.port", portMux);
    conf.put("codex.mode", "mux");
    // conf.put("codex.mux.tenant", new JsonObject().put("tenant", "test-lib").put("urls", new JsonArray(
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
        if (enabledModules.contains("mock1")) {
          JsonObject j = new JsonObject();
          j.put("id", "mock1");
          j.put("url", "http://localhost:" + Integer.toString(portMock1));
          a.add(j);
        }
        if (enabledModules.contains("mock2")) {
          JsonObject j = new JsonObject();
          j.put("id", "mock2");
          j.put("url", "http://localhost:" + Integer.toString(portMock2));
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

    logger.info("testMock");
    RestAssured.port = portMock1;
    r = RestAssured.given()
      .header(tenantHeader)
      .get("/codex-instances")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();
    b = r.getBody().asString();
    j = new JsonObject(b);
    context.assertEquals(3, j.getInteger("totalRecords"));

    RestAssured.given()
      .header(tenantHeader)
      .get("/codex-instances2")
      .then()
      .log().ifValidationFails()
      .statusCode(400); // would have expected 404 for RMB

    r = RestAssured.given()
      .header(tenantHeader)
      .get("/codex-instances/11224467")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();

    b = r.getBody().asString();
    j = new JsonObject(b);
    context.assertTrue("11224467".equals(j.getString("id")), b);

    RestAssured.given()
      .header(tenantHeader)
      .get("/codex-instances/1234")
      .then()
      .log().ifValidationFails()
      .statusCode(404);

    RestAssured.port = portMock2;
    r = RestAssured.given()
      .header(tenantHeader)
      .get("/codex-instances")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();
    b = r.getBody().asString();
    j = new JsonObject(b);
    context.assertEquals(20, j.getInteger("totalRecords"));

    RestAssured.given()
      .header(tenantHeader)
      .get("/codex-instances2")
      .then()
      .log().ifValidationFails()
      .statusCode(400); // would have expected 404 for RMB

    r = RestAssured.given()
      .header(tenantHeader)
      .get("/codex-instances/10000000")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();

    b = r.getBody().asString();
    j = new JsonObject(b);
    context.assertTrue("10000000".equals(j.getString("id")), b);

    RestAssured.given()
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

    logger.info("testMutex");
    RestAssured.port = portMux;

    r = RestAssured.given()
      .header(tenantHeader)
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
      .statusCode(404);
  }

}
