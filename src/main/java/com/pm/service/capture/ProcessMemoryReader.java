package com.pm.service.capture;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Obtiene el consumo de memoria de procesos consultando los archivos expuestos por Linux en
 * {@code /proc}.
 */
public final class ProcessMemoryReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessMemoryReader.class);
  private static final long DEFAULT_PAGE_SIZE = 4096L;
  private final boolean isLinux;

  public ProcessMemoryReader() {
    this.isLinux = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux");
  }

  public Optional<BigDecimal> readMemoryMb(long pid) {
    if (isLinux) {
      return readLinuxMemory(pid);
    }
    return Optional.empty();
  }

  private Optional<BigDecimal> readLinuxMemory(long pid) {
    Path statm = Path.of("/proc", Long.toString(pid), "statm");
    if (!Files.exists(statm)) {
      return Optional.empty();
    }
    try {
      String content = Files.readString(statm);
      if (content == null || content.isBlank()) {
        return Optional.empty();
      }
      String[] parts = content.trim().split("\\s+");
      if (parts.length == 0) {
        return Optional.empty();
      }
      long pages = Long.parseLong(parts[0]);
      long bytes = pages * DEFAULT_PAGE_SIZE;
      BigDecimal mb =
          BigDecimal.valueOf(bytes)
              .divide(BigDecimal.valueOf(1024 * 1024), 2, RoundingMode.HALF_UP);
      return Optional.of(mb);
    } catch (IOException | NumberFormatException ex) {
      LOGGER.debug("No se pudo obtener memoria para pid {}: {}", pid, ex.getMessage());
      return Optional.empty();
    }
  }
}
