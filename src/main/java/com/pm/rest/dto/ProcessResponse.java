package com.pm.rest.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProcessResponse(
    long id_process,
    long pid,
    String nombre,
    String usuario,
    int prioridad,
    boolean expulsivo,
    BigDecimal cpu_pct,
    BigDecimal mem_mb,
    String descripcion,
    String file_path,
    Instant created_at) {}
