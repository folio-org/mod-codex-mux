package org.folio.codex;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import static org.folio.codex.TestHelper.readFile;
import static org.folio.codex.TestHelper.stubModules;
import static org.folio.codex.TestHelper.stubPackagesSources;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import com.github.tomakehurst.wiremock.matching.UrlPathPattern;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;
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
import org.folio.rest.jaxrs.model.Source;
import org.folio.rest.jaxrs.model.SourceCollection;
import org.folio.rest.tools.utils.NetworkUtils;

@RunWith(VertxUnitRunner.class)
public class CodexPackagesSourcesImplTest {

  private final String TENANT = "testlib";
  private final String CODEX_PACKAGES_SOURCES_MODULE_1 = "codex-packages-sources1";
  private final String CODEX_PACKAGES_SOURCES_MODULE_2 = "codex-packages-sources2";
  private final String CODEX_PACKAGES_SOURCES_PATH = "/codex-packages-sources";
  private final String CODEX_PACKAGES_PATH = "/codex-packages";

  @Rule
  public WireMockRule userMockServer = new WireMockRule(WireMockConfiguration.wireMockConfig().dynamicPort().notifier(
      new Slf4jNotifier(true)));

  private int portCodex = NetworkUtils.nextFreePort();

  private Vertx vertx = Vertx.vertx();

  private Header tenantHeader = new Header(XOkapiHeaders.TENANT, TENANT);
  private Header urlHeader;

  @Before
  public void setUp(TestContext context) throws JsonProcessingException {
    urlHeader = new Header(XOkapiHeaders.URL, getUrl());
    setupMux(context);

    List<Module> modules = Arrays.asList(new Module(CODEX_PACKAGES_SOURCES_MODULE_1), new Module(CODEX_PACKAGES_SOURCES_MODULE_2));
    stubModules(CodexInterfaces.CODEX_PACKAGES.getValue(), new ObjectMapper().writeValueAsString(modules), 200);
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
  public void shouldReturnListOfPackagesSources() throws IOException, URISyntaxException {
    String stubCodexPackageSource1 = readFile("codex/responses/codex-packages-sources.json");
    String stubCodexPackageSource2 = readFile("codex/responses/codex-packages-sources2.json");
    stubPackagesSources(stubCodexPackageSource1, 200, CODEX_PACKAGES_SOURCES_MODULE_1);
    stubPackagesSources(stubCodexPackageSource2, 200, CODEX_PACKAGES_SOURCES_MODULE_2);


    ObjectMapper mapper = new ObjectMapper();
    final SourceCollection codexPackagesSources1 = mapper.readValue(stubCodexPackageSource1, SourceCollection.class);
    final SourceCollection codexPackagesSources2 = mapper.readValue(stubCodexPackageSource2, SourceCollection.class);
    final List<Source> combinedSources = Stream.concat(codexPackagesSources1.getSources().stream(), codexPackagesSources2
        .getSources().stream()).collect(Collectors.toList());


    SourceCollection response = RestAssured.given()
      .port(portCodex)
      .header(tenantHeader)
      .header(urlHeader)
      .get(CODEX_PACKAGES_SOURCES_PATH)
      .then()
      .contentType(ContentType.JSON)
      .log()
      .ifValidationFails()
      .statusCode(200)
      .extract()
      .as(SourceCollection.class);

    JSONAssert.assertEquals(mapper.writeValueAsString(response.getSources()), mapper.writeValueAsString(combinedSources), false);

  }

  @Test
  public void shouldReturnEmptyCodexPackagesListWhenNoModulesImplementInterface() {
    stubModules(CodexInterfaces.CODEX_PACKAGES.getValue(), "[]", 200);

    final SourceCollection sourceCollection = RestAssured
      .given()
      .port(portCodex)
      .header(tenantHeader)
      .header(urlHeader)
      .get(CODEX_PACKAGES_SOURCES_PATH)
      .then()
      .log()
      .ifValidationFails()
      .statusCode(200)
      .extract()
      .as(SourceCollection.class);

    assertTrue(sourceCollection.getSources().isEmpty());
  }

  @Test
  public void shouldReturn400WhenFailsToGetListOfModules() {
    stubModules(CodexInterfaces.CODEX_PACKAGES.getValue(), null, 500);

    int statusCode = RestAssured
      .given()
      .port(portCodex)
      .header(tenantHeader)
      .header(urlHeader)
      .get(CODEX_PACKAGES_SOURCES_PATH)
      .then()
      .log()
      .ifValidationFails()
      .extract()
      .statusCode();

    assertEquals(400, statusCode);
  }

  @Test
  public void shouldReturn500WhenConnectionToModuleFails() {
    stubFor(get(new UrlPathPattern(new EqualToPattern(CODEX_PACKAGES_SOURCES_PATH), false))
      .willReturn(new ResponseDefinitionBuilder().withFault(Fault.RANDOM_DATA_THEN_CLOSE)));

    int statusCode = RestAssured
      .given()
      .port(portCodex)
      .header(tenantHeader)
      .header(urlHeader)
      .get(CODEX_PACKAGES_SOURCES_PATH)
      .then()
      .log()
      .ifValidationFails()
      .extract()
      .statusCode();

    assertEquals(500, statusCode);
  }

}
