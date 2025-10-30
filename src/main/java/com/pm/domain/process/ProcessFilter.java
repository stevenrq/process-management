package com.pm.domain.process;

import java.util.Optional;

/** Filtros opcionales que se aplican al paginar procesos de un catalogo. */
public record ProcessFilter(
    Optional<String> usuario,
    Optional<Boolean> expulsivo,
    Optional<String> nombreLike,
    Optional<Long> pid) {

  public ProcessFilter(String usuario, Boolean expulsivo, String nombreLike, Long pid) {
    this(
        Optional.ofNullable(usuario),
        Optional.ofNullable(expulsivo),
        Optional.ofNullable(nombreLike),
        Optional.ofNullable(pid));
  }
}
