package com.pm.domain.process;

import java.math.BigDecimal;

/** Represents an in-memory snapshot of an OS process before it is persisted. */
public record ProcessSnapshot(
    long pid,
    String nombre,
    String usuario,
    BigDecimal cpuPct,
    BigDecimal memMb,
    Integer prioridad,
    boolean systemProcess) {}
