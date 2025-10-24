package com.pm.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pm.config.AppConfig;
import com.pm.domain.PagedResult;
import com.pm.domain.ResourceNotFoundException;
import com.pm.domain.SelectionCriterion;
import com.pm.domain.ValidationException;
import com.pm.domain.catalog.Catalog;
import com.pm.domain.catalog.CatalogMetadata;
import com.pm.domain.catalog.CatalogSort;
import com.pm.domain.process.ProcessFilter;
import com.pm.domain.process.ProcessSort;
import com.pm.domain.process.ProcessUpdate;
import com.pm.rest.dto.CatalogCreateRequest;
import com.pm.rest.dto.CatalogExportResponse;
import com.pm.rest.dto.CatalogImportRequest;
import com.pm.rest.dto.CatalogResponse;
import com.pm.rest.dto.ErrorResponse;
import com.pm.rest.dto.PagedResponse;
import com.pm.rest.dto.ProcessResponse;
import com.pm.rest.dto.ProcessUpdateRequest;
import com.pm.service.CatalogService;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.json.JavalinJackson;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RestServer implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(RestServer.class);

  private final Javalin app;
  private final CatalogService catalogService;
  private final int port;

  public RestServer(AppConfig config, CatalogService catalogService) {
    this.catalogService = catalogService;
    this.port = config.getRestPort();
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    this.app =
        Javalin.create(
            configuration -> {
              configuration.jsonMapper(new JavalinJackson(mapper));
              configuration.plugins.enableCors(
                  cors ->
                      cors.add(
                          handler -> {
                            String origin = config.getAllowedCorsOrigin();
                            if (origin == null || origin.isBlank() || "*".equals(origin)) {
                              handler.anyHost();
                            } else {
                              handler.allowHost(origin);
                            }
                          }));
            });
    registerExceptionHandlers();
    registerRoutes();
  }

  private void registerRoutes() {
    app.get("/api/health", ctx -> ctx.json(Map.of("status", "UP", "timestamp", Instant.now())));

    app.get("/api/catalogos", this::handleListCatalogs);
    app.post("/api/catalogos", this::handleCreateCatalog);
    app.get("/api/catalogos/{id}", this::handleGetCatalog);
    app.delete("/api/catalogos/{id}", this::handleDeleteCatalog);
    app.get("/api/catalogos/{id}/procesos", this::handleListProcesses);
    app.get("/api/catalogos/{id}/procesos/{idp}", this::handleGetProcess);
    app.patch("/api/catalogos/{id}/procesos/{idp}", this::handleUpdateProcess);
    app.delete("/api/catalogos/{id}/procesos/{idp}", this::handleDeleteProcess);
    app.get("/api/catalogos/{id}/export", this::handleExportCatalog);
    app.post("/api/catalogos/import", this::handleImportCatalog);
  }

  private void registerExceptionHandlers() {
    app.exception(
        ValidationException.class,
        (ex, ctx) -> {
          LOGGER.warn("Validation error: {}", ex.getMessage());
          List<String> details = ex.getErrors() == null ? List.of() : ex.getErrors();
          ctx.status(400).json(new ErrorResponse("VALIDATION_ERROR", ex.getMessage(), details));
        });
    app.exception(
        ResourceNotFoundException.class,
        (ex, ctx) ->
            ctx.status(404).json(new ErrorResponse("NOT_FOUND", ex.getMessage(), List.of())));
    app.exception(
        IllegalArgumentException.class,
        (ex, ctx) ->
            ctx.status(400).json(new ErrorResponse("BAD_REQUEST", ex.getMessage(), List.of())));
    app.exception(
        Exception.class,
        (ex, ctx) -> {
          LOGGER.error("Unexpected error", ex);
          ctx.status(500).json(new ErrorResponse("SERVER_ERROR", "Error interno", List.of()));
        });
  }

  private void handleListCatalogs(Context ctx) {
    int page = parsePositiveInt(ctx.queryParam("page"), 1);
    int size = parsePositiveInt(ctx.queryParam("size"), 20);
    CatalogSort sort = CatalogSort.fromRequest(ctx.queryParam("sort"));
    Optional<String> search = optionalQuery(ctx, "search");
    Optional<SelectionCriterion> origin =
        optionalQuery(ctx, "criterio").map(SelectionCriterion::fromString);
    PagedResult<CatalogMetadata> paged =
        catalogService.listCatalogs(search, origin, sort, page, size);
    PagedResponse<com.pm.rest.dto.CatalogDetailResponse> response =
        RestMapper.toPagedResponse(paged, RestMapper::toCatalogDetail);
    ctx.json(response);
  }

  private void handleCreateCatalog(Context ctx) {
    CatalogCreateRequest request = ctx.bodyAsClass(CatalogCreateRequest.class);
    SelectionCriterion criterion = SelectionCriterion.fromString(request.criterio());
    Catalog catalog =
        catalogService.createCatalog(
            request.nombre(), request.descripcion(), request.n(), criterion);
    CatalogResponse response = RestMapper.toCatalogResponse(catalog);
    ctx.status(201).json(response);
  }

  private void handleGetCatalog(Context ctx) {
    long id = parseLongPath(ctx, "id");
    Catalog catalog = catalogService.getCatalog(id);
    ctx.json(RestMapper.toCatalogDetail(catalog));
  }

  private void handleDeleteCatalog(Context ctx) {
    long id = parseLongPath(ctx, "id");
    catalogService.deleteCatalog(id);
    ctx.status(204);
  }

  private void handleListProcesses(Context ctx) {
    long catalogId = parseLongPath(ctx, "id");
    int page = parsePositiveInt(ctx.queryParam("page"), 1);
    int size = parsePositiveInt(ctx.queryParam("size"), 20);
    ProcessSort sort = ProcessSort.fromRequest(ctx.queryParam("sort"));
    ProcessFilter filter =
        new ProcessFilter(
            optionalQuery(ctx, "usuario"),
            optionalQuery(ctx, "expulsivo").map(Boolean::parseBoolean),
            optionalQuery(ctx, "nombre"),
            optionalQuery(ctx, "pid").map(this::parseLongStrict));
    var paged = catalogService.listProcesses(catalogId, filter, sort, page, size);
    PagedResponse<ProcessResponse> response =
        RestMapper.toPagedResponse(paged, RestMapper::toProcessResponse);
    ctx.json(response);
  }

  private void handleGetProcess(Context ctx) {
    long catalogId = parseLongPath(ctx, "id");
    long processId = parseLongPath(ctx, "idp");
    ProcessResponse response =
        RestMapper.toProcessResponse(catalogService.getProcess(catalogId, processId));
    ctx.json(response);
  }

  private void handleUpdateProcess(Context ctx) {
    long catalogId = parseLongPath(ctx, "id");
    long processId = parseLongPath(ctx, "idp");
    ProcessUpdateRequest request = ctx.bodyAsClass(ProcessUpdateRequest.class);
    ProcessUpdate update =
        new ProcessUpdate(
            Optional.ofNullable(request.descripcion()),
            Optional.ofNullable(request.prioridad()),
            Optional.ofNullable(request.expulsivo()));
    catalogService.updateProcess(catalogId, processId, update);
    ctx.status(204);
  }

  private void handleDeleteProcess(Context ctx) {
    long catalogId = parseLongPath(ctx, "id");
    long processId = parseLongPath(ctx, "idp");
    catalogService.deleteProcess(catalogId, processId);
    ctx.status(204);
  }

  private void handleExportCatalog(Context ctx) {
    long catalogId = parseLongPath(ctx, "id");
    Catalog catalog = catalogService.exportCatalog(catalogId);
    CatalogExportResponse response = RestMapper.toExportResponse(catalog);
    ctx.json(response);
  }

  private void handleImportCatalog(Context ctx) {
    CatalogImportRequest request = ctx.bodyAsClass(CatalogImportRequest.class);
    var payload = RestMapper.toImportPayload(request);
    Catalog catalog = catalogService.importCatalog(payload);
    ctx.status(201).json(RestMapper.toCatalogResponse(catalog));
  }

  private Optional<String> optionalQuery(Context ctx, String name) {
    return Optional.ofNullable(ctx.queryParam(name)).filter(value -> !value.isBlank());
  }

  private int parsePositiveInt(String raw, int defaultValue) {
    if (raw == null || raw.isBlank()) {
      return defaultValue;
    }
    try {
      int value = Integer.parseInt(raw);
      return value > 0 ? value : defaultValue;
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("Parametro numerico invalido");
    }
  }

  private long parseLongPath(Context ctx, String name) {
    return parseLongStrict(ctx.pathParam(name));
  }

  private long parseLongStrict(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("Valor numerico requerido");
    }
    try {
      return Long.parseLong(raw);
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("Valor numerico invalido: " + raw);
    }
  }

  public void start() {
    app.start(port);
    LOGGER.info("REST server iniciado en puerto {}", port);
  }

  public void stop() {
    app.stop();
  }

  @Override
  public void close() {
    stop();
  }
}
