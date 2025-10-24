package com.pm.service.capture;

import com.pm.domain.SelectionCriterion;
import com.pm.domain.process.ProcessSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProcessCaptureService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessCaptureService.class);
  private final Duration sampleDuration;
  private final ProcessMemoryReader memoryReader;
  private final int availableProcessors;
  private final boolean isWindows;
  private final WindowsProcessInfoProvider windowsInfoProvider;

  public ProcessCaptureService(Duration sampleDuration, ProcessMemoryReader memoryReader) {
    this.sampleDuration = sampleDuration;
    this.memoryReader = memoryReader;
    this.availableProcessors = Math.max(Runtime.getRuntime().availableProcessors(), 1);
    this.isWindows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    this.windowsInfoProvider = isWindows ? new WindowsProcessInfoProvider() : null;
  }

  private <T> T safeCall(SupplierWithException<T> supplier, T fallback) {
    try {
      return supplier.get();
    } catch (Throwable ex) {
      LOGGER.debug("Dato de proceso no disponible: {}", ex.getMessage());
      return fallback;
    }
  }

  @FunctionalInterface
  private interface SupplierWithException<T> {
    T get() throws Exception;
  }

  public List<ProcessSnapshot> captureTopN(SelectionCriterion criterion, int n) {
    if (n <= 0) {
      throw new IllegalArgumentException("N debe ser mayor a 0");
    }
    Map<Long, Baseline> baselines = new ConcurrentHashMap<>();
    ProcessHandle.allProcesses()
        .forEach(
            handle -> {
              java.time.Duration cpuDuration =
                  safeCall(
                      () -> handle.info().totalCpuDuration().orElse(java.time.Duration.ZERO),
                      java.time.Duration.ZERO);
              String command =
                  safeCall(
                      () ->
                          handle
                              .info()
                              .command()
                              .orElseGet(() -> handle.info().commandLine().orElse("")),
                      "");
              String nombre = extractName(command);
              String usuario = safeCall(() -> handle.info().user().orElse(null), null);
              baselines.put(
                  handle.pid(),
                  new Baseline(
                      handle, normalize(nombre, 120), normalize(usuario, 80), cpuDuration));
            });
    if (baselines.isEmpty()) {
      LOGGER.warn("No se capturaron procesos del sistema operativo");
      return List.of();
    }
    LOGGER.debug("Procesos baseline capturados: {}", baselines.size());
    try {
      Thread.sleep(sampleDuration.toMillis());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }

    Map<Long, ProcessExtraInfo> extras =
        isWindows && windowsInfoProvider != null
            ? windowsInfoProvider.fetch(baselines.keySet())
            : Map.of();

    List<ProcessSnapshot> snapshots = new ArrayList<>();
    for (Baseline baseline : baselines.values()) {
      ProcessHandle handle = baseline.handle();
      if (!handle.isAlive()) {
        continue;
      }
      ProcessHandle.Info info = handle.info();
      Optional<java.time.Duration> afterCpu = info.totalCpuDuration();
      BigDecimal cpuPct = null;
      if (afterCpu.isPresent()) {
        long deltaNanos = afterCpu.get().toNanos() - baseline.cpuDuration().toNanos();
        if (deltaNanos < 0) {
          deltaNanos = 0;
        }
        double elapsedNanos = sampleDuration.toNanos() * (double) availableProcessors;
        if (elapsedNanos > 0) {
          double ratio = (deltaNanos / elapsedNanos) * 100.0;
          cpuPct = BigDecimal.valueOf(ratio).setScale(2, RoundingMode.HALF_UP);
        }
      }
      Optional<BigDecimal> memOpt = memoryReader.readMemoryMb(handle.pid());
      ProcessExtraInfo extra = extras.get(handle.pid());
      BigDecimal mem = extra != null && extra.memMb() != null ? extra.memMb() : memOpt.orElse(null);
      Integer priority = extra != null ? extra.priority() : null;
      boolean systemProcess = extra != null && extra.systemProcess();
      snapshots.add(
          new ProcessSnapshot(
              handle.pid(),
              baseline.nombre(),
              baseline.usuario(),
              cpuPct,
              mem,
              priority,
              systemProcess));
    }

    Comparator<ProcessSnapshot> comparator =
        switch (criterion) {
          case CPU ->
              Comparator.comparing(
                      (ProcessSnapshot ps) -> ps.cpuPct() == null ? BigDecimal.ZERO : ps.cpuPct(),
                      Comparator.naturalOrder())
                  .reversed();
          case MEMORY ->
              Comparator.comparing(
                      (ProcessSnapshot ps) -> ps.memMb() == null ? BigDecimal.ZERO : ps.memMb(),
                      Comparator.naturalOrder())
                  .reversed();
        };

    if (snapshots.isEmpty()) {
      LOGGER.warn(
          "No se obtuvieron procesos tras el muestreo, se agregará el proceso actual como"
              + " respaldo");
      snapshots.add(
          new ProcessSnapshot(
              ProcessHandle.current().pid(),
              normalize(extractName("java"), 120),
              System.getProperty("user.name"),
              BigDecimal.ZERO,
              null,
              null,
              false));
    }

    List<ProcessSnapshot> top = snapshots.stream().sorted(comparator).limit(n).toList();
    if (LOGGER.isDebugEnabled()) {
      top.stream()
          .limit(5)
          .forEach(
              ps ->
                  LOGGER.debug(
                      "Proceso capturado pid={}, nombre={}, usuario={}, cpu={}, mem={},"
                          + " prioridad={}, system={}",
                      ps.pid(),
                      ps.nombre(),
                      ps.usuario(),
                      ps.cpuPct(),
                      ps.memMb(),
                      ps.prioridad(),
                      ps.systemProcess()));
    }
    LOGGER.debug("Procesos tras ordenamiento: {}", top.size());
    return top;
  }

  private String extractName(String command) {
    if (command == null || command.isBlank()) {
      return "unknown";
    }
    try {
      return Path.of(command).getFileName().toString();
    } catch (Exception ex) {
      return command;
    }
  }

  private String normalize(String value, int maxLen) {
    if (value == null) {
      return "unknown";
    }
    String trimmed = value.trim();
    String sanitized = trimmed.replaceAll("[^\\p{Alnum}._\\- ]", "");
    if (sanitized.length() > maxLen) {
      sanitized = sanitized.substring(0, maxLen);
    }
    if (sanitized.isEmpty()) {
      return "unknown";
    }
    return sanitized;
  }

  private record Baseline(
      ProcessHandle handle, String nombre, String usuario, java.time.Duration cpuDuration) {}
}
