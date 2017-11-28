package org.folio.codex;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.response.Header;
import com.jayway.restassured.response.Response;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.rest.RestVerticle;
import org.junit.Test;

@RunWith(VertxUnitRunner.class)
public class MuxTest {

  private final Header tenantHeader = new Header("X-Okapi-Tenant", "testlib");
  private final int portMux = Integer.parseInt(System.getProperty("port", "9031"));
  private final int portMock = Integer.parseInt(System.getProperty("port", "9030"));
  private final Logger logger = LoggerFactory.getLogger("codex.mux");

  Vertx vertx;

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();

    Async async = context.async();
    JsonObject conf = new JsonObject();
    conf.put("http.port", portMock);
    conf.put("codex.mode", "mock");
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
        async.complete();
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
    RestAssured.port = portMock;
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

    r = RestAssured.given().port(portMux)
      .header(tenantHeader)
      .get("/codex-instances")
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().response();

    b = r.getBody().asString();
    j = new JsonObject(b);
    context.assertEquals(0, j.getInteger("totalRecords"));

    RestAssured.given().port(portMux)
      .header(tenantHeader)
      .get("/codex-instances/11224467")
      .then()
      .log().ifValidationFails()
      .statusCode(404);
  }
}
