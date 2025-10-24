package com.pm.domain.process;

public enum ProcessSort {
  CPU_DESC("cpu_pct DESC"),
  CPU_ASC("cpu_pct ASC"),
  MEM_DESC("mem_mb DESC"),
  MEM_ASC("mem_mb ASC"),
  PRIORIDAD_DESC("prioridad DESC"),
  PRIORIDAD_ASC("prioridad ASC"),
  NOMBRE_ASC("nombre ASC"),
  NOMBRE_DESC("nombre DESC"),
  CREATED_DESC("created_at DESC"),
  CREATED_ASC("created_at ASC");

  private final String sql;

  ProcessSort(String sql) {
    this.sql = sql;
  }

  public String sql() {
    return sql;
  }

  public static ProcessSort fromRequest(String raw) {
    if (raw == null || raw.isBlank()) {
      return CREATED_DESC;
    }
    String normalized = raw.trim().toLowerCase();
    return switch (normalized) {
      case "cpu_pct,asc" -> CPU_ASC;
      case "cpu_pct,desc" -> CPU_DESC;
      case "mem_mb,asc" -> MEM_ASC;
      case "mem_mb,desc" -> MEM_DESC;
      case "prioridad,asc" -> PRIORIDAD_ASC;
      case "prioridad,desc" -> PRIORIDAD_DESC;
      case "nombre,asc" -> NOMBRE_ASC;
      case "nombre,desc" -> NOMBRE_DESC;
      case "created_at,asc" -> CREATED_ASC;
      case "created_at,desc" -> CREATED_DESC;
      default -> CREATED_DESC;
    };
  }
}
