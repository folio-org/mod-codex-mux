package org.folio.codex;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import com.github.tomakehurst.wiremock.matching.UrlPathPattern;
import com.google.common.io.Files;

import org.folio.okapi.common.XOkapiHeaders;

public class TestHelper {

  private static final String PACKAGE_ID = "123";
  private static final String TENANT = "testlib";

  public static void stubModules(String codexInterfaceValue, String modulesBody, int status) {
    stubFor(get(new UrlPathPattern(new EqualToPattern("/_/proxy/tenants/" + TENANT + "/interfaces/" + codexInterfaceValue), false))
      .willReturn(new ResponseDefinitionBuilder()
        .withStatus(status)
        .withBody(modulesBody)));
  }

  public static void stubPackages(String responseBody, int status, String moduleId) {
    stubFor(get(new UrlPathPattern(new EqualToPattern("/codex-packages"), false))
      .withHeader(XOkapiHeaders.MODULE_ID, new EqualToPattern(moduleId))
      .willReturn(new ResponseDefinitionBuilder()
        .withStatus(status)
        .withBody(responseBody)));
  }
  public static void stubPackagesSources(String responseBody, int status, String moduleId) {
    stubFor(get(new UrlPathPattern(new EqualToPattern("/codex-packages-sources"), false))
      .withHeader(XOkapiHeaders.MODULE_ID, new EqualToPattern(moduleId))
      .willReturn(new ResponseDefinitionBuilder()
        .withStatus(status)
        .withBody(responseBody)));
  }


  public static void stubPackage(String responseBody, int status, String moduleId) {
    stubFor(get(new UrlPathPattern(new EqualToPattern("/codex-packages/" + PACKAGE_ID), false))
      .withHeader(XOkapiHeaders.MODULE_ID, new EqualToPattern(moduleId))
      .willReturn(new ResponseDefinitionBuilder()
        .withStatus(status)
        .withBody(responseBody)));
  }

  public static String readFile(String filename) throws IOException, URISyntaxException {
    return Files.asCharSource(getFile(filename), StandardCharsets.UTF_8).read();
  }

  /**
   * Returns File object corresponding to the file on classpath with specified filename
   */
  public static File getFile(String filename) throws URISyntaxException {
    return new File(CodexPackagesImplTest.class.getClassLoader()
      .getResource(filename).toURI());
  }

}
