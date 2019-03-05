package org.folio.codex;


public enum CodexInterfaces {
  CODEX("codex", "/codex-instances?"), CODEX_PACKAGES("codex-packages", "/codex-packages?");

  private String value;

  private String queryPath;

  CodexInterfaces(String value, String queryPath) {
    this.value = value;
    this.queryPath = queryPath;
  }

  public String getValue() {
    return value;
  }

  public String getQueryPath() {
    return queryPath;
  }
}
