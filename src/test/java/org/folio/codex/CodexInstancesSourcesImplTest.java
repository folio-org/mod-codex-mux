package org.folio.codex;

import static org.junit.Assert.assertEquals;

import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.response.Header;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.RestVerticle;
import org.folio.rest.tools.utils.NetworkUtils;

@RunWith(VertxUnitRunner.class)
public class CodexInstancesSourcesImplTest {

  private static final String CODEX_INSTANCES_SOURCES_PATH = "/codex-instances-sources";
  private final String TENANT = "testlib";
  @Rule
  public WireMockRule userMockServer = new WireMockRule(WireMockConfiguration.wireMockConfig().dynamicPort().notifier(
      new Slf4jNotifier(true)));

  private int portCodex = NetworkUtils.nextFreePort();

  private Vertx vertx = Vertx.vertx();

  private Header tenantHeader = new Header(XOkapiHeaders.TENANT, TENANT);
  private Header urlHeader;

  @Before
  public void setUp(TestContext context) {
    urlHeader = new Header(XOkapiHeaders.URL, getUrl());
    setupMux(context);
  }

  private String getUrl() {
    int port = userMockServer.port();
    return "http://localhost:" + port;
  }

  private void setupMux(TestContext context) {
    JsonObject conf = new JsonObject();
    conf.put("http.port", portCodex);
    DeploymentOptions opt = new DeploymentOptions().setConfig(conf);
    vertx.deployVerticle(RestVerticle.class.getName(), opt, context.asyncAssertSuccess());
  }

  @Test
  public void shouldReturn501WhenEndpointNotImplemented() {

    int statusCode = RestAssured.given()
      .port(portCodex)
      .header(tenantHeader)
      .header(urlHeader)
      .get(CODEX_INSTANCES_SOURCES_PATH)
      .then()
      .log()
      .ifValidationFails()
      .extract().statusCode();

    assertEquals(501, statusCode);
  }
}
