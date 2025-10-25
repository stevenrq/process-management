package com.pm.service;

import com.pm.domain.PagedResult;
import com.pm.domain.ResourceNotFoundException;
import com.pm.domain.SelectionCriterion;
import com.pm.domain.ValidationException;
import com.pm.domain.catalog.Catalog;
import com.pm.domain.catalog.CatalogImportPayload;
import com.pm.domain.catalog.CatalogMetadata;
import com.pm.domain.catalog.CatalogSort;
import com.pm.domain.process.ProcessFilter;
import com.pm.domain.process.ProcessImport;
import com.pm.domain.process.ProcessRecord;
import com.pm.domain.process.ProcessSnapshot;
import com.pm.domain.process.ProcessSort;
import com.pm.domain.process.ProcessUpdate;
import com.pm.persistence.CatalogRepository;
import com.pm.service.capture.ProcessCaptureService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orquesta la captura de procesos y la administracion completa de los catalogos persistidos.
 */
public final class CatalogService {

  private static final Logger LOGGER = LoggerFactory.getLogger(CatalogService.class);
  private static final int MAX_NAME = 120;
  private static final int MAX_USER = 80;
  private static final int MAX_DESCRIPTION = 5000;

  private final CatalogRepository repository;
  private final ProcessCaptureService captureService;
  private final ExpulsivoEvaluator expulsivoEvaluator;
  private final ProcessFileService fileService;

  public CatalogService(
      CatalogRepository repository,
      ProcessCaptureService captureService,
      ExpulsivoEvaluator expulsivoEvaluator,
      ProcessFileService fileService) {
    this.repository = repository;
    this.captureService = captureService;
    this.expulsivoEvaluator = expulsivoEvaluator;
    this.fileService = fileService;
  }

  public Catalog createCatalog(
      String nombre, String descripcion, int n, SelectionCriterion criterio) {
    validateCatalogInputs(nombre, n, criterio);
    List<ProcessSnapshot> captured = captureService.captureTopN(criterio, n);
    LOGGER.info(
        "Captura solicitada (criterio={}, n={}) -> {} procesos", criterio, n, captured.size());
    if (captured.isEmpty()) {
      throw new IllegalStateException("No se pudieron capturar procesos del sistema operativo");
    }
    List<ProcessRecord> records = new ArrayList<>();
    for (ProcessSnapshot snapshot : captured) {
      long pid = snapshot.pid();
      String processName = ensureLength(snapshot.nombre(), MAX_NAME);
      String user = ensureLength(snapshot.usuario(), MAX_USER);
      boolean systemProcess = snapshot.systemProcess();
      BigDecimal cpu = sanitizeMetric(snapshot.cpuPct());
      BigDecimal mem = sanitizeMetric(snapshot.memMb());
      int prioridad = snapshot.prioridad() == null ? 0 : Math.max(0, snapshot.prioridad());
      boolean expulsivo = systemProcess ? false : expulsivoEvaluator.isExpulsivo(processName, user);
      String processDescription =
          buildProcessDescription(nombre, descripcion, processName, pid, cpu, mem);
      String filePath = fileService.writeDescriptionFile(processName, pid, processDescription);
      ProcessRecord record =
          ProcessRecord.builder()
              .setPid(pid)
              .setNombre(processName)
              .setUsuario(user)
              .setPrioridad(prioridad)
              .setExpulsivo(expulsivo)
              .setCpuPct(cpu)
              .setMemMb(mem)
              .setDescripcion(processDescription)
              .setFilePath(filePath)
              .setCreatedAt(Instant.now())
              .build();
      if (!expulsivo && record.getPrioridad() < 1) {
        LOGGER.warn(
            "Proceso {} marcado no expulsivo con prioridad baja ({})", pid, record.getPrioridad());
      }
      records.add(record);
    }
    Catalog catalog =
        Catalog.builder()
            .setNombre(ensureLength(nombre, MAX_NAME))
            .setDescripcion(ensureLength(descripcion, MAX_DESCRIPTION))
            .setOrigen(criterio)
            .setN(n)
            .setFechaCreacion(Instant.now())
            .setProcesos(records)
            .build();
    return repository.saveCatalogWithProcesses(catalog);
  }

  private void validateCatalogInputs(String nombre, int n, SelectionCriterion criterio) {
    List<String> errors = new ArrayList<>();
    if (nombre == null || nombre.isBlank()) {
      errors.add("nombre_catalogo es obligatorio");
    }
    if (n <= 0) {
      errors.add("N debe ser mayor a 0");
    }
    if (criterio == null) {
      errors.add("criterio es obligatorio");
    }
    if (!errors.isEmpty()) {
      throw new ValidationException(errors);
    }
  }

  private BigDecimal sanitizeMetric(BigDecimal value) {
    if (value == null) {
      return null;
    }
    return value.setScale(2, RoundingMode.HALF_UP);
  }

  private String ensureLength(String value, int max) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.length() > max) {
      return trimmed.substring(0, max);
    }
    return trimmed;
  }

  private String buildProcessDescription(
      String catalogName,
      String catalogDescription,
      String processName,
      long pid,
      BigDecimal cpu,
      BigDecimal mem) {
    return """
    Proceso capturado para catalogo %s (pid=%d, cpu=%s%%, mem=%s MB).
    %s
    """
        .formatted(
            catalogName,
            pid,
            cpu == null ? "?" : cpu.toPlainString(),
            mem == null ? "?" : mem.toPlainString(),
            catalogDescription == null ? "" : catalogDescription);
  }

  public PagedResult<CatalogMetadata> listCatalogs(
      Optional<String> search,
      Optional<SelectionCriterion> origin,
      CatalogSort sort,
      int page,
      int size) {
    return repository.findCatalogs(search, origin, sort, page, size);
  }

  public Catalog getCatalog(long id) {
    return repository
        .findCatalog(id)
        .orElseThrow(() -> new ResourceNotFoundException("Catalogo no encontrado"));
  }

  public void updateCatalogMetadata(long id, String nombre, String descripcion) {
    if (nombre == null || nombre.isBlank()) {
      throw new ValidationException(List.of("nombre_catalogo obligatorio"));
    }
    repository.updateCatalogMetadata(
        id, ensureLength(nombre, MAX_NAME), ensureLength(descripcion, MAX_DESCRIPTION));
  }

  public void deleteCatalog(long id) {
    repository.deleteCatalog(id);
  }

  public PagedResult<ProcessRecord> listProcesses(
      long catalogId, ProcessFilter filter, ProcessSort sort, int page, int size) {
    return repository.findProcesses(catalogId, filter, sort, page, size);
  }

  public ProcessRecord getProcess(long catalogId, long processId) {
    return repository
        .findProcess(catalogId, processId)
        .orElseThrow(() -> new ResourceNotFoundException("Proceso no encontrado"));
  }

  public void updateProcess(long catalogId, long processId, ProcessUpdate update) {
    validateProcessUpdate(update);
    repository.updateProcess(catalogId, processId, update);
  }

  private void validateProcessUpdate(ProcessUpdate update) {
    List<String> errors = new ArrayList<>();
    update
        .descripcion()
        .ifPresent(
            value -> {
              if (value.length() > MAX_DESCRIPTION) {
                errors.add("descripcion supera longitud maxima");
              }
            });
    update
        .prioridad()
        .ifPresent(
            value -> {
              if (value < 0) {
                errors.add("prioridad debe ser >= 0");
              }
            });
    if (!errors.isEmpty()) {
      throw new ValidationException(errors);
    }
  }

  public void deleteProcess(long catalogId, long processId) {
    repository.deleteProcess(catalogId, processId);
  }

  public Catalog exportCatalog(long catalogId) {
    return getCatalog(catalogId);
  }

  public Catalog importCatalog(CatalogImportPayload payload) {
    if (payload == null) {
      throw new ValidationException(List.of("payload requerido"));
    }
    SelectionCriterion origen =
        Objects.requireNonNullElse(payload.origen(), SelectionCriterion.CPU);
    List<ProcessImport> processes =
        payload.procesos() == null ? List.of() : List.copyOf(payload.procesos());
    int n = payload.n() > 0 ? payload.n() : processes.size();
    validateCatalogInputs(payload.nombre(), n, origen);
    if (processes.isEmpty()) {
      throw new ValidationException(List.of("procesos es obligatorio"));
    }
    List<String> errors = new ArrayList<>();
    List<ProcessRecord> records = new ArrayList<>();
    for (ProcessImport processImport : processes) {
      ProcessRecord record = buildImportedRecord(processImport, errors);
      if (record != null) {
        records.add(record);
      }
    }
    if (!errors.isEmpty()) {
      throw new ValidationException(errors);
    }
    Catalog catalog =
        Catalog.builder()
            .setNombre(ensureLength(payload.nombre(), MAX_NAME))
            .setDescripcion(ensureLength(payload.descripcion(), MAX_DESCRIPTION))
            .setOrigen(origen)
            .setN(n)
            .setFechaCreacion(Instant.now())
            .setProcesos(records)
            .build();
    return repository.saveCatalogWithProcesses(catalog);
  }

  private ProcessRecord buildImportedRecord(ProcessImport process, List<String> errors) {
    if (process == null) {
      errors.add("proceso null en importacion");
      return null;
    }
    List<String> localErrors = new ArrayList<>();
    String nombre = ensureLength(process.nombre(), MAX_NAME);
    if (nombre == null || nombre.isBlank()) {
      localErrors.add("nombre de proceso requerido en importacion");
    }
    long pid = process.pid();
    if (pid < 0) {
      localErrors.add("pid invalido en importacion");
    }
    String usuario = ensureLength(process.usuario(), MAX_USER);
    int prioridad = process.prioridad() == null ? 0 : process.prioridad();
    if (prioridad < 0) {
      localErrors.add("prioridad invalida en importacion");
    }
    boolean expulsivo =
        process.expulsivo() != null
            ? process.expulsivo()
            : expulsivoEvaluator.isExpulsivo(nombre, usuario);
    String descripcion = ensureLength(process.descripcion(), MAX_DESCRIPTION);
    BigDecimal cpu = process.cpuPct() == null ? null : sanitizeMetric(process.cpuPct());
    BigDecimal mem = process.memMb() == null ? null : sanitizeMetric(process.memMb());
    if (!localErrors.isEmpty()) {
      errors.addAll(localErrors);
      return null;
    }
    String filePath = fileService.writeDescriptionFile(nombre, pid, descripcion);
    return ProcessRecord.builder()
        .setPid(pid)
        .setNombre(nombre)
        .setUsuario(usuario)
        .setPrioridad(prioridad)
        .setExpulsivo(expulsivo)
        .setCpuPct(cpu)
        .setMemMb(mem)
        .setDescripcion(descripcion)
        .setFilePath(filePath)
        .setCreatedAt(Instant.now())
        .build();
  }
}
