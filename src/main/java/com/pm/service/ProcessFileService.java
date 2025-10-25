package com.pm.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maneja la creacion de archivos de descripcion asociados a los procesos capturados o importados.
 */
public final class ProcessFileService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessFileService.class);
  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

  private final Path baseDir;

  public ProcessFileService(Path baseDir) {
    this.baseDir = baseDir;
  }

  public String writeDescriptionFile(String processName, long pid, String descripcion) {
    String sanitized =
        (processName == null || processName.isBlank() ? "process" : processName)
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9\\-_.]", "_");
    String fileName =
        sanitized + "-" + pid + "-" + UUID.randomUUID().toString().substring(0, 8) + ".txt";
    Path path = baseDir.resolve(fileName);
    String content =
        descripcion == null || descripcion.isBlank()
            ? "Proceso capturado " + FORMATTER.format(java.time.OffsetDateTime.now())
            : descripcion;
    try {
      Files.createDirectories(baseDir);
      Files.writeString(path, content, StandardCharsets.UTF_8);
      LOGGER.debug("Archivo de proceso creado: {}", path);
      return path.toString();
    } catch (IOException ex) {
      LOGGER.warn("No se pudo escribir archivo {}: {}", path, ex.getMessage());
      return null;
    }
  }
}
