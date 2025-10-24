package com.pm.domain.process;

import java.util.Optional;

public record ProcessUpdate(
    Optional<String> descripcion, Optional<Integer> prioridad, Optional<Boolean> expulsivo) {

  public ProcessUpdate {
    descripcion = descripcion == null ? Optional.empty() : descripcion;
    prioridad = prioridad == null ? Optional.empty() : prioridad;
    expulsivo = expulsivo == null ? Optional.empty() : expulsivo;
  }
}
