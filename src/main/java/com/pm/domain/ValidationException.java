package com.pm.domain;

import java.util.List;

/** Excepcion de dominio que transporta los errores de validacion encontrados. */
public final class ValidationException extends RuntimeException {

  private final transient List<String> errors;

  public ValidationException(List<String> errors) {
    super(errors == null ? "Validation error" : String.join("; ", errors));
    this.errors = errors;
  }

  public List<String> getErrors() {
    return errors;
  }
}
