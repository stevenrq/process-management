package com.pm.domain.process;

import java.math.BigDecimal;

public record ProcessImport(
    long pid,
    String nombre,
    String usuario,
    Integer prioridad,
    Boolean expulsivo,
    String descripcion,
    BigDecimal cpuPct,
    BigDecimal memMb) {}
