package com.pm.domain;

public enum SelectionCriterion {
  CPU,
  MEMORY;

  public static SelectionCriterion fromString(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("criterio requerido (CPU|MEMORY)");
    }
    return switch (raw.trim().toUpperCase()) {
      case "CPU" -> CPU;
      case "MEM", "MEMORY", "RAM" -> MEMORY;
      default -> throw new IllegalArgumentException("criterio desconocido: " + raw);
    };
  }
}
