package org.folio.codex;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class LHeaders {

  private final Map<String, String> m;

  public LHeaders(Map<String, String> okapiHeaders) {
    m = okapiHeaders;
  }

  public String get(String k) {
    return m.get(k.toLowerCase());
  }

  public Set<Entry<String, String>> entrySet() {
    return m.entrySet();
  }
}
