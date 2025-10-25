package com.pm.rest.dto;

import java.time.Instant;

/** DTO con la informacion detallada que se devuelve al consultar un catalogo. */
public record CatalogDetailResponse(
    long id_catalog,
    String nombre,
    String descripcion,
    String origen,
    int n,
    Instant fecha_creacion) {}
