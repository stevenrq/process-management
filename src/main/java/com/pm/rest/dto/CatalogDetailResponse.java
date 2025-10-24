package com.pm.rest.dto;

import java.time.Instant;

public record CatalogDetailResponse(
    long id_catalog,
    String nombre,
    String descripcion,
    String origen,
    int n,
    Instant fecha_creacion) {}
