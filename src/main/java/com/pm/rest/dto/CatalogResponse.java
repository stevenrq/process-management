package com.pm.rest.dto;

import java.time.Instant;

public record CatalogResponse(
    long id_catalog,
    String nombre,
    String origen,
    int n,
    Instant fecha_creacion,
    int total_procesos) {}
