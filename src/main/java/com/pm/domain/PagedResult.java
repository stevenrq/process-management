package com.pm.domain;

import java.util.Collections;
import java.util.List;

/** Representa una pagina de resultados del dominio con metadatos basicos de paginacion. */
public record PagedResult<T>(List<T> content, int page, int size, long total) {

  public PagedResult {
    content = List.copyOf(content == null ? Collections.emptyList() : content);
    if (page < 1) {
      throw new IllegalArgumentException("page debe iniciar en 1");
    }
    if (size < 1) {
      throw new IllegalArgumentException("size debe ser > 0");
    }
  }
}
