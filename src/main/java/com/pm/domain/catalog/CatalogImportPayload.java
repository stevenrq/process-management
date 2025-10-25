package com.pm.domain.catalog;

import com.pm.domain.SelectionCriterion;
import com.pm.domain.process.ProcessImport;
import java.util.List;

/** Objeto de dominio usado para validar la importacion de catalogos externos. */
public record CatalogImportPayload(
    String nombre,
    String descripcion,
    SelectionCriterion origen,
    int n,
    List<ProcessImport> procesos) {}
