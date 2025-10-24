package com.pm.rest.dto;

import java.util.List;

public record CatalogExportResponse(
    CatalogDetailResponse catalogo, List<ProcessResponse> procesos) {}
