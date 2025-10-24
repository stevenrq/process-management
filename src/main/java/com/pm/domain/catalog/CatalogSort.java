package com.pm.domain.catalog;

public enum CatalogSort {
  FECHA_CREACION_DESC("fecha_creacion DESC"),
  FECHA_CREACION_ASC("fecha_creacion ASC"),
  N_DESC("n DESC"),
  N_ASC("n ASC"),
  NOMBRE_ASC("nombre ASC"),
  NOMBRE_DESC("nombre DESC");

  private final String sql;

  CatalogSort(String sql) {
    this.sql = sql;
  }

  public String sql() {
    return sql;
  }

  public static CatalogSort fromRequest(String sort) {
    if (sort == null || sort.isBlank()) {
      return FECHA_CREACION_DESC;
    }
    return switch (sort.trim().toLowerCase()) {
      case "fecha_creacion,asc" -> FECHA_CREACION_ASC;
      case "fecha_creacion,desc" -> FECHA_CREACION_DESC;
      case "n,asc" -> N_ASC;
      case "n,desc" -> N_DESC;
      case "nombre,asc" -> NOMBRE_ASC;
      case "nombre,desc" -> NOMBRE_DESC;
      default -> FECHA_CREACION_DESC;
    };
  }
}
