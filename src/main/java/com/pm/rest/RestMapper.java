package com.pm.rest;

import com.pm.domain.PagedResult;
import com.pm.domain.SelectionCriterion;
import com.pm.domain.catalog.Catalog;
import com.pm.domain.catalog.CatalogImportPayload;
import com.pm.domain.catalog.CatalogMetadata;
import com.pm.domain.process.ProcessImport;
import com.pm.domain.process.ProcessRecord;
import com.pm.rest.dto.CatalogDetailResponse;
import com.pm.rest.dto.CatalogExportResponse;
import com.pm.rest.dto.CatalogImportRequest;
import com.pm.rest.dto.CatalogResponse;
import com.pm.rest.dto.PagedResponse;
import com.pm.rest.dto.ProcessResponse;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Convierte entre las entidades de dominio y los objetos de transporte expuestos por el API REST.
 */
public final class RestMapper {

  private RestMapper() {}

  public static CatalogResponse toCatalogResponse(Catalog catalog) {
    return new CatalogResponse(
        catalog.getId(),
        catalog.getNombre(),
        catalog.getOrigen().name(),
        catalog.getN(),
        catalog.getFechaCreacion(),
        catalog.getProcesos().size());
  }

  public static CatalogDetailResponse toCatalogDetail(Catalog catalog) {
    return new CatalogDetailResponse(
        catalog.getId(),
        catalog.getNombre(),
        catalog.getDescripcion(),
        catalog.getOrigen().name(),
        catalog.getN(),
        catalog.getFechaCreacion());
  }

  public static CatalogDetailResponse toCatalogDetail(CatalogMetadata metadata) {
    return new CatalogDetailResponse(
        metadata.id(),
        metadata.nombre(),
        metadata.descripcion(),
        metadata.origen().name(),
        metadata.n(),
        metadata.fechaCreacion());
  }

  public static CatalogExportResponse toExportResponse(Catalog catalog) {
    CatalogDetailResponse detail = toCatalogDetail(catalog);
    List<ProcessResponse> processes =
        catalog.getProcesos().stream()
            .map(RestMapper::toProcessResponse)
            .collect(Collectors.toList());
    return new CatalogExportResponse(detail, processes);
  }

  public static ProcessResponse toProcessResponse(ProcessRecord record) {
    return new ProcessResponse(
        record.getId(),
        record.getPid(),
        record.getNombre(),
        record.getUsuario(),
        record.getPrioridad(),
        record.isExpulsivo(),
        record.getCpuPct(),
        record.getMemMb(),
        record.getDescripcion(),
        record.getFilePath(),
        record.getCreatedAt());
  }

  public static <T, R> PagedResponse<R> toPagedResponse(
      PagedResult<T> paged, Function<T, R> mapper) {
    List<R> content = paged.content().stream().map(mapper).collect(Collectors.toList());
    return new PagedResponse<>(content, paged.page(), paged.size(), paged.total());
  }

  public static CatalogImportPayload toImportPayload(CatalogImportRequest request) {
    SelectionCriterion origin =
        request.origen() == null ? null : SelectionCriterion.fromString(request.origen());
    List<ProcessImport> processes =
        request.procesos() == null
            ? List.of()
            : request.procesos().stream()
                .filter(Objects::nonNull)
                .map(
                    dto ->
                        new ProcessImport(
                            dto.pid(),
                            dto.nombre(),
                            dto.usuario(),
                            dto.prioridad(),
                            dto.expulsivo(),
                            dto.descripcion(),
                            dto.cpuPct(),
                            dto.memMb()))
                .collect(Collectors.toList());
    return new CatalogImportPayload(
        request.nombre(), request.descripcion(), origin, request.n(), processes);
  }

  public static CatalogImportPayload toImportPayload(CatalogExportResponse response) {
    CatalogDetailResponse detail = response.catalogo();
    SelectionCriterion origin =
        detail == null ? SelectionCriterion.CPU : SelectionCriterion.fromString(detail.origen());
    List<ProcessImport> processes =
        response.procesos() == null
            ? List.of()
            : response.procesos().stream()
                .filter(Objects::nonNull)
                .map(
                    dto ->
                        new ProcessImport(
                            dto.pid(),
                            dto.nombre(),
                            dto.usuario(),
                            dto.prioridad(),
                            dto.expulsivo(),
                            dto.descripcion(),
                            dto.cpu_pct(),
                            dto.mem_mb()))
                .collect(Collectors.toList());
    return new CatalogImportPayload(
        detail == null ? null : detail.nombre(),
        detail == null ? null : detail.descripcion(),
        origin,
        detail == null ? 0 : detail.n(),
        processes);
  }
}
