package com.pm.domain.process;

import java.math.BigDecimal;

/** Representa una fotografia en memoria de un proceso antes de persistirlo. */
public record ProcessSnapshot(
    long pid,
    String nombre,
    String usuario,
    BigDecimal cpuPct,
    BigDecimal memMb,
    Integer prioridad,
    boolean systemProcess) {}
