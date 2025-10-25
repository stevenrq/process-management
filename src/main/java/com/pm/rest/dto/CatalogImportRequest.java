package com.pm.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Modelo utilizado para recibir la importacion de un catalogo desde el API. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogImportRequest(
    String nombre, String descripcion, String origen, int n, List<ImportProcessDto> procesos) {

  /** Representa cada proceso incluido dentro de una importacion de catalogo. */
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
