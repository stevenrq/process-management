package com.pm.rest.dto;

import java.time.Instant;

/** Resumen de un catalogo devuelto al listar o crear recursos desde el API. */
public record CatalogResponse(
    long id_catalog,
    String nombre,
    String origen,
    int n,
    Instant fecha_creacion,
    int total_procesos) {}
