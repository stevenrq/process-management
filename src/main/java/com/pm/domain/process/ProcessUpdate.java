package com.pm.domain.process;

import java.util.Optional;

/** Valores opcionales recibidos al actualizar un proceso persistido. */
public record ProcessUpdate(
    Optional<String> descripcion, Optional<Integer> prioridad, Optional<Boolean> expulsivo) {

  public ProcessUpdate(String descripcion, Integer prioridad, Boolean expulsivo) {
    this(
        Optional.ofNullable(descripcion),
        Optional.ofNullable(prioridad),
        Optional.ofNullable(expulsivo));
  }
}
