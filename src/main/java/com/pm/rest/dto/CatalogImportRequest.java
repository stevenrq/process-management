package com.pm.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogImportRequest(
    String nombre, String descripcion, String origen, int n, List<ImportProcessDto> procesos) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ImportProcessDto(
      long pid,
      String nombre,
      String usuario,
      Integer prioridad,
      Boolean expulsivo,
      String descripcion,
      String filePath,
      java.math.BigDecimal cpuPct,
      java.math.BigDecimal memMb) {}
}
