package com.pm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pm.config.DirectoryManager;
import com.pm.domain.catalog.Catalog;
import com.pm.rest.RestMapper;
import com.pm.rest.dto.CatalogDetailResponse;
import com.pm.rest.dto.CatalogExportResponse;
import com.pm.rest.dto.CatalogImportRequest;
import com.pm.rest.dto.ProcessResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JsonCatalogService {

  private static final Logger LOGGER = LoggerFactory.getLogger(JsonCatalogService.class);
  private static final DateTimeFormatter FILE_FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

  private final CatalogService catalogService;
  private final DirectoryManager directoryManager;
  private final ObjectMapper mapper;

  public JsonCatalogService(CatalogService catalogService, DirectoryManager directoryManager) {
    this.catalogService = catalogService;
    this.directoryManager = directoryManager;
    this.mapper = new ObjectMapper();
    this.mapper.registerModule(new JavaTimeModule());
    this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  public Path exportCatalog(long catalogId) {
    Catalog catalog = catalogService.exportCatalog(catalogId);
    CatalogExportResponse dto = RestMapper.toExportResponse(catalog);
    Path exportDir = directoryManager.getExportDir();
    String asyncTimestamp =
        FILE_FORMAT.format(
            catalog.getFechaCreacion().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
    String fileName = "catalogo-" + catalog.getId() + "-" + asyncTimestamp + ".json";
    Path target = exportDir.resolve(fileName);
    try {
      mapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), dto);
      LOGGER.info("Catálogo {} exportado a {}", catalogId, target);
      return target;
    } catch (IOException ex) {
      throw new IllegalStateException("No se pudo exportar catálogo a JSON", ex);
    }
  }

  public Catalog importCatalog(Path file) {
    try {
      byte[] bytes = Files.readAllBytes(file);
      JsonNode root = mapper.readTree(bytes);
      var payload =
          root.has("catalogo")
              ? RestMapper.toImportPayload(
                  new CatalogExportResponse(
                      mapper.treeToValue(root.get("catalogo"), CatalogDetailResponse.class),
                      root.has("procesos")
                          ? mapper.convertValue(
                              root.get("procesos"), new TypeReference<List<ProcessResponse>>() {})
                          : List.of()))
              : RestMapper.toImportPayload(mapper.treeToValue(root, CatalogImportRequest.class));
      Catalog catalog = catalogService.importCatalog(payload);
      LOGGER.info("Catálogo importado desde {}", file);
      return catalog;
    } catch (IOException ex) {
      throw new IllegalStateException("No se pudo importar catálogo desde JSON", ex);
    }
  }
}
