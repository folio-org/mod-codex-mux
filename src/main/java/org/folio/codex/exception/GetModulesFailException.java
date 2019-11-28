package org.folio.codex.exception;

public class GetModulesFailException extends RuntimeException {
  public GetModulesFailException(String message) {
    super(message);
  }

  public GetModulesFailException(String message, Throwable cause) {
    super(message, cause);
  }

  public GetModulesFailException(Throwable cause) {
    super(cause);
  }
}
