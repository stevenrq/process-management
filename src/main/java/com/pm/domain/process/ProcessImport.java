package com.pm.domain.process;

import java.math.BigDecimal;

/** Representa un proceso declarado en un archivo de importacion de catalogos. */
public record ProcessImport(
    long pid,
    String nombre,
    String usuario,
    Integer prioridad,
    Boolean expulsivo,
    String descripcion,
    BigDecimal cpuPct,
    BigDecimal memMb) {}
