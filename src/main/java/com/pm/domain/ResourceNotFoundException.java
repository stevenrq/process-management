package com.pm.domain;

/** Excepcion de dominio para indicar que un recurso no fue hallado. */
public final class ResourceNotFoundException extends RuntimeException {

  public ResourceNotFoundException(String message) {
    super(message);
  }
}
