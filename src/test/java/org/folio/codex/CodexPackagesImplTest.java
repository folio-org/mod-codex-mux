package org.folio.codex;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.junit.Assert.assertEquals;

import static org.folio.codex.TestHelper.readFile;
import static org.folio.codex.TestHelper.stubModules;
import static org.folio.codex.TestHelper.stubPackage;
import static org.folio.codex.TestHelper.stubPackages;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import com.github.tomakehurst.wiremock.matching.RegexPattern;
import com.github.tomakehurst.wiremock.matching.UrlPathPattern;
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
import org.skyscreamer.jsonassert.JSONAssert;

import org.folio.codex.model.Module;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.RestVerticle;
import org.folio.rest.tools.utils.NetworkUtils;

@RunWith(VertxUnitRunner.class)
public class CodexPackagesImplTest {

  private static final String TENANT = "testlib";
  private static final String CODEX_MODULE_1 = "codex-module1";
  private static final String CODEX_MODULE_2 = "codex-module2";
  private static final String PACKAGE_ID = "123";
  public static final String PACKAGE_QUERY = "?offset=0&limit=10&query=name=abc*";
  @Rule
  public WireMockRule userMockServer = new WireMockRule(
    WireMockConfiguration.wireMockConfig()
      .dynamicPort()
      .notifier(new Slf4jNotifier(true)));

  private String host = "http://localhost";
  private String url;
  private int port;
  private int portCodex = NetworkUtils.nextFreePort();

  private Vertx vertx = Vertx.vertx();

  private Header tenantHeader = new Header(XOkapiHeaders.TENANT, TENANT);
  private Header urlHeader;


  @Before
  public void setUp(TestContext context) throws JsonProcessingException {
    port = userMockServer.port();
    url = host + ":" + port;
    urlHeader = new Header(XOkapiHeaders.URL, url);
    setupMux(context);

    List<Module> modules = Arrays.asList(new Module(CODEX_MODULE_1), new Module(CODEX_MODULE_2));
    stubModules(CodexInterfaces.CODEX_PACKAGES.getValue(), new ObjectMapper().writeValueAsString(modules), 200);
  }

  @Test
  public void shouldGetPackageByIdWhenOneModuleReturnsPackageAndOtherModuleReturns404() throws IOException, URISyntaxException {
    String stubPackage = readFile("codex/responses/package.json");
    stubPackage(stubPackage, 200, CODEX_MODULE_1);
    stubPackage(null, 404, CODEX_MODULE_2);

    String response = RestAssured.given()
      .port(portCodex)
      .header(tenantHeader)
      .header(urlHeader)
      .get("/codex-packages/" + PACKAGE_ID)
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().asString();

    JSONAssert.assertEquals(response, stubPackage, true);
  }

  @Test
  public void shouldReturn404WhenAllModulesReturnAnError() {
    stubPackage(null, 404, CODEX_MODULE_1);
    stubPackage(null, 500, CODEX_MODULE_2);

    int statusCode = RestAssured.given()
      .port(portCodex)
      .header(tenantHeader)
      .header(urlHeader)
      .get("/codex-packages/" + PACKAGE_ID)
      .then()
      .log().ifValidationFails()
      .extract().statusCode();
    assertEquals(404, statusCode);
  }

  @Test
  public void shouldReturn404WhenNoModulesExist() {
    stubModules(CodexInterfaces.CODEX_PACKAGES.getValue(),"[]", 200);

    int statusCode = RestAssured.given()
      .port(portCodex)
      .header(tenantHeader)
      .header(urlHeader)
      .get("/codex-packages/" + PACKAGE_ID)
      .then()
      .log().ifValidationFails()
      .extract().statusCode();
    assertEquals(404, statusCode);
  }

  @Test
  public void shouldReturn401WhenFailsToGetListOfModules() {
    stubModules(CodexInterfaces.CODEX_PACKAGES.getValue(),null, 500);

    int statusCode = RestAssured.given()
      .port(portCodex)
      .header(tenantHeader)
      .header(urlHeader)
      .get("/codex-packages/" + PACKAGE_ID)
      .then()
      .log().ifValidationFails()
      .extract().statusCode();
    assertEquals(401, statusCode);
  }

  @Test
  public void shouldReturn500WhenConnectionToModuleFails() {
    stubFor(get(new UrlPathPattern(new EqualToPattern("/codex-packages/" + PACKAGE_ID), false))
      .willReturn(new ResponseDefinitionBuilder()
        .withFault(Fault.RANDOM_DATA_THEN_CLOSE)
        ));

    int statusCode = RestAssured.given()
      .port(portCodex)
      .header(tenantHeader)
      .header(urlHeader)
      .get("/codex-packages/" + PACKAGE_ID)
      .then()
      .log().ifValidationFails()
      .extract().statusCode();
    assertEquals(500, statusCode);
  }

  @Test
  public void shouldReturnPackagesCollection() throws IOException, URISyntaxException {
    String stubPackagesCollectionResponseFromTestModule1 = readFile("codex/responses/packages/packages-collection-from-test-module1.json");
    String stubPackagesCollectionResponse= readFile("codex/responses/packages/packages-collection.json");
    stubPackages(stubPackagesCollectionResponseFromTestModule1, 200, CODEX_MODULE_1);
    stubPackages(null, 404, CODEX_MODULE_2);

    String response = RestAssured.given()
      .port(portCodex)
      .header(tenantHeader)
      .header(urlHeader)
      .get("/codex-packages" + PACKAGE_QUERY)
      .then()
      .log().ifValidationFails()
      .statusCode(200).extract().asString();

    JSONAssert.assertEquals(response, stubPackagesCollectionResponse, true);
  }

  @Test
  public void shouldReturn400WhenQueryIndexInvalidQuery() {

    int statusCode = RestAssured.given()
      .port(portCodex)
      .header(tenantHeader)
      .header(urlHeader)
      .get("/codex-packages?query=provider=abc* sortby invalid")
      .then()
      .log().ifValidationFails()
      .extract().statusCode();
    assertEquals(400, statusCode);
  }

  @Test
  public void shouldReturn401WhenFailsToGetListOfModulesByQuery() {
    stubModules(CodexInterfaces.CODEX_PACKAGES.getValue(),null, 500);

    int statusCode = RestAssured.given()
      .port(portCodex)
      .header(tenantHeader)
      .header(urlHeader)
      .get("/codex-packages" + PACKAGE_QUERY)
      .then()
      .log().ifValidationFails()
      .extract().statusCode();
    assertEquals(401, statusCode);
  }

  @Test
  public void shouldReturn500WhenConnectionToModulesFails(){

    stubFor(get(new UrlPathPattern(new RegexPattern("/codex-packages.*"), true))
      .willReturn(new ResponseDefinitionBuilder()
        .withFault(Fault.RANDOM_DATA_THEN_CLOSE)
      ));

    int statusCode = RestAssured.given()
      .port(portCodex)
      .header(tenantHeader)
      .header(urlHeader)
      .get("/codex-packages" + PACKAGE_QUERY)
      .then()
      .log().ifValidationFails()
      .extract().statusCode();
    assertEquals(500, statusCode);
  }

  private void setupMux(TestContext context) {
    JsonObject conf = new JsonObject();
    conf.put("http.port", portCodex);
    DeploymentOptions opt = new DeploymentOptions()
      .setConfig(conf);
    vertx.deployVerticle(RestVerticle.class.getName(), opt,  context.asyncAssertSuccess());
  }

}
