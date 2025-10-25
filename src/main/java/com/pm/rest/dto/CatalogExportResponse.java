package com.pm.rest.dto;

import java.util.List;

/** Envuelve la informacion exportada de un catalogo junto con su lista de procesos. */
public record CatalogExportResponse(
    CatalogDetailResponse catalogo, List<ProcessResponse> procesos) {}
