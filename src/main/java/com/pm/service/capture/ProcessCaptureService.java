package com.pm.service.capture;

import com.pm.domain.SelectionCriterion;
import com.pm.domain.process.ProcessSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Captura procesos del sistema operativo y calcula metricas para construir fotografias segun un
 * criterio de seleccion.
 */
public final class ProcessCaptureService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessCaptureService.class);
  private final Duration sampleDuration;
  private final ProcessMemoryReader memoryReader;
  private final int availableProcessors;
  private final boolean isWindows;
  private final boolean isLinux;
  private final WindowsProcessInfoProvider windowsInfoProvider;
  private final LinuxProcessInfoProvider linuxInfoProvider;

  public ProcessCaptureService(Duration sampleDuration, ProcessMemoryReader memoryReader) {
    this.sampleDuration = sampleDuration;
    this.memoryReader = memoryReader;
    this.availableProcessors = Math.max(Runtime.getRuntime().availableProcessors(), 1);
    String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    this.isWindows = osName.contains("win");
    this.isLinux = osName.contains("linux");
    this.windowsInfoProvider = isWindows ? new WindowsProcessInfoProvider() : null;
    this.linuxInfoProvider = isLinux ? new LinuxProcessInfoProvider() : null;
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

    Map<Long, ProcessExtraInfo> extras;
    if (isWindows && windowsInfoProvider != null) {
      extras = windowsInfoProvider.fetch(baselines.keySet());
    } else if (isLinux && linuxInfoProvider != null) {
      extras = linuxInfoProvider.fetch(baselines.keySet());
    } else {
      extras = Map.of();
    }

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

    snapshots = aggregateByName(snapshots);

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
      // Evita propagar listas vacias cuando el muestreo no devuelve procesos.
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

  private List<ProcessSnapshot> aggregateByName(List<ProcessSnapshot> snapshots) {
    if (snapshots.isEmpty()) {
      return snapshots;
    }
    Map<String, AggregatedProcess> aggregated = new LinkedHashMap<>();
    for (ProcessSnapshot snapshot : snapshots) {
      String nombre = snapshot.nombre() == null ? "unknown" : snapshot.nombre();
      String key = isWindows ? nombre.toLowerCase(Locale.ROOT) : nombre;
      aggregated.compute(
          key,
          (k, current) -> {
            if (current == null) {
              return new AggregatedProcess(nombre, snapshot);
            }
            current.merge(nombre, snapshot);
            return current;
          });
    }
    if (aggregated.size() != snapshots.size() && LOGGER.isDebugEnabled()) {
      LOGGER.debug(
          "Agrupacion por nombre redujo {} procesos a {} entradas unicas",
          snapshots.size(),
          aggregated.size());
    }
    return aggregated.values().stream().map(AggregatedProcess::toSnapshot).toList();
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

  private static final class AggregatedProcess {
    private String displayName;
    private long representativePid;
    private String usuario;
    private BigDecimal cpuPctSum;
    private BigDecimal memMbSum;
    private Integer prioridad;
    private boolean systemProcess;

    private AggregatedProcess(String nombre, ProcessSnapshot snapshot) {
      this.displayName = nombre;
      this.representativePid = snapshot.pid();
      this.usuario = sanitizeUser(snapshot.usuario());
      this.cpuPctSum = snapshot.cpuPct();
      this.memMbSum = snapshot.memMb();
      this.prioridad = snapshot.prioridad();
      this.systemProcess = snapshot.systemProcess();
    }

    private void merge(String nombre, ProcessSnapshot snapshot) {
      if (isUnknown(displayName) && !isUnknown(nombre)) {
        this.displayName = nombre;
      }
      this.representativePid = Math.min(this.representativePid, snapshot.pid());
      String candidateUser = sanitizeUser(snapshot.usuario());
      if (isUnknown(this.usuario) && !isUnknown(candidateUser)) {
        this.usuario = candidateUser;
      }
      this.cpuPctSum = sumMetric(this.cpuPctSum, snapshot.cpuPct());
      this.memMbSum = sumMetric(this.memMbSum, snapshot.memMb());
      if (snapshot.prioridad() != null) {
        this.prioridad =
            this.prioridad == null
                ? snapshot.prioridad()
                : Math.max(this.prioridad, snapshot.prioridad());
      }
      this.systemProcess = this.systemProcess || snapshot.systemProcess();
    }

    private ProcessSnapshot toSnapshot() {
      String nombre = isUnknown(displayName) ? "unknown" : displayName;
      String user = isUnknown(usuario) ? "unknown" : usuario;
      return new ProcessSnapshot(
          representativePid, nombre, user, cpuPctSum, memMbSum, prioridad, systemProcess);
    }

    private static BigDecimal sumMetric(BigDecimal base, BigDecimal extra) {
      if (base == null) {
        return extra;
      }
      if (extra == null) {
        return base;
      }
      return base.add(extra);
    }

    private static String sanitizeUser(String user) {
      if (user == null) {
        return null;
      }
      String trimmed = user.trim();
      return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isUnknown(String value) {
      return value == null || value.isBlank() || "unknown".equalsIgnoreCase(value);
    }
  }
}
