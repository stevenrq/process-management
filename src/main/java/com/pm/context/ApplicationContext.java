package com.pm.context;

import com.pm.config.AppConfig;
import com.pm.config.DatabaseManager;
import com.pm.config.DirectoryManager;
import com.pm.persistence.CatalogRepository;
import com.pm.persistence.DatabaseInitializer;
import com.pm.rest.RestServer;
import com.pm.service.CatalogService;
import com.pm.service.ExpulsivoEvaluator;
import com.pm.service.JsonCatalogService;
import com.pm.service.ProcessFileService;
import com.pm.service.capture.ProcessCaptureService;
import com.pm.service.capture.ProcessMemoryReader;

/** Contenedor liviano que inicializa los servicios principales y gestiona su ciclo de vida. */
public final class ApplicationContext implements AutoCloseable {

  private final AppConfig config;
  private final DirectoryManager directoryManager;
  private final DatabaseManager databaseManager;
  private final CatalogRepository catalogRepository;
  private final ProcessCaptureService captureService;
  private final ProcessFileService fileService;
  private final ExpulsivoEvaluator expulsivoEvaluator;
  private final CatalogService catalogService;
  private final RestServer restServer;
  private final JsonCatalogService jsonCatalogService;

  public ApplicationContext() {
    this(new AppConfig());
  }

  public ApplicationContext(AppConfig config) {
    this.config = config;
    this.directoryManager = new DirectoryManager(config);
    this.directoryManager.initialize();
    this.databaseManager = new DatabaseManager(config);
    new DatabaseInitializer(databaseManager.getDataSource()).initialize();
    ProcessMemoryReader memoryReader = new ProcessMemoryReader();
    this.captureService =
        new ProcessCaptureService(config.getCaptureSampleDuration(), memoryReader);
    this.expulsivoEvaluator =
        new ExpulsivoEvaluator(config.getExpulsiveSystemUsers(), config.getExpulsiveNamePatterns());
    this.fileService = new ProcessFileService(directoryManager.getFilesBaseDir());
    this.catalogRepository = new CatalogRepository(databaseManager.getDataSource());
    this.catalogService =
        new CatalogService(catalogRepository, captureService, expulsivoEvaluator, fileService);
    this.jsonCatalogService = new JsonCatalogService(catalogService, directoryManager);
    this.restServer = new RestServer(config, catalogService);
    config.logConfiguration();
  }

  public AppConfig getConfig() {
    return config;
  }

  public CatalogService getCatalogService() {
    return catalogService;
  }

  public ProcessCaptureService getCaptureService() {
    return captureService;
  }

  public ExpulsivoEvaluator getExpulsivoEvaluator() {
    return expulsivoEvaluator;
  }

  public ProcessFileService getFileService() {
    return fileService;
  }

  public DirectoryManager getDirectoryManager() {
    return directoryManager;
  }

  public DatabaseManager getDatabaseManager() {
    return databaseManager;
  }

  public RestServer getRestServer() {
    return restServer;
  }

  public JsonCatalogService getJsonCatalogService() {
    return jsonCatalogService;
  }

  @Override
  public void close() {
    restServer.close();
    databaseManager.close();
  }
}
