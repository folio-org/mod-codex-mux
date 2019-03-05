package org.folio.codex;

public enum CodexInterfaces {
  CODEX("codex"), CODEX_PACKAGES("codex-packages"), CODEX_PACKAGES_SOURCES("codex-packages-sources");

  private String value;

  CodexInterfaces(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
