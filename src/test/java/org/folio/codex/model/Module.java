package org.folio.codex.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Module {

  public Module() {
  }

  public Module(String id) {
    this.id = id;
  }

  @JsonProperty("id")
  String id;
}
