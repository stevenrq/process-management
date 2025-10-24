package com.pm.domain.catalog;

import com.pm.domain.SelectionCriterion;
import java.time.Instant;

public record CatalogMetadata(
    long id,
    String nombre,
    String descripcion,
    SelectionCriterion origen,
    int n,
    Instant fechaCreacion) {}
