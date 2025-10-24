package com.pm.domain.process;

import java.util.Optional;

public record ProcessFilter(
    Optional<String> usuario,
    Optional<Boolean> expulsivo,
    Optional<String> nombreLike,
    Optional<Long> pid) {

  public ProcessFilter {
    usuario = usuario == null ? Optional.empty() : usuario;
    expulsivo = expulsivo == null ? Optional.empty() : expulsivo;
    nombreLike = nombreLike == null ? Optional.empty() : nombreLike;
    pid = pid == null ? Optional.empty() : pid;
  }
}
