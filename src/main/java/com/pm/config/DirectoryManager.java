package com.pm.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Ensures required directories exist for runtime artifacts. */
public final class DirectoryManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(DirectoryManager.class);

  private final Path filesBaseDir;
  private final Path exportDir;
  private final Path importDir;

  public DirectoryManager(AppConfig config) {
    this.filesBaseDir = config.getFilesBaseDir();
    this.exportDir = config.getExportDir();
    this.importDir = config.getImportDir();
  }

  public void initialize() {
    createIfNeeded(filesBaseDir);
    createIfNeeded(exportDir);
    createIfNeeded(importDir);
  }

  private void createIfNeeded(Path dir) {
    try {
      Files.createDirectories(dir);
      LOGGER.info("Directory ready: {}", dir);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to create directory " + dir, e);
    }
  }

  public Path getFilesBaseDir() {
    return filesBaseDir;
  }

  public Path getExportDir() {
    return exportDir;
  }

  public Path getImportDir() {
    return importDir;
  }
}
