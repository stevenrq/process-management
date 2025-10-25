package com.pm.service.capture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consulta informacion de procesos en sistemas Windows usando PowerShell y la convierte a objetos
 * reutilizables.
 */
final class WindowsProcessInfoProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(WindowsProcessInfoProvider.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final int BATCH_SIZE = 40;

  Map<Long, ProcessExtraInfo> fetch(Collection<Long> pids) {
    if (pids == null || pids.isEmpty()) {
      return Map.of();
    }
    ArrayDeque<Long> queue = new ArrayDeque<>(pids);
    Map<Long, ProcessExtraInfo> aggregated = new HashMap<>();
    while (!queue.isEmpty()) {
      List<Long> batch = new ArrayList<>(BATCH_SIZE);
      while (batch.size() < BATCH_SIZE && !queue.isEmpty()) {
        batch.add(queue.removeFirst());
      }
      aggregated.putAll(fetchBatch(batch));
    }
    return aggregated;
  }

  private Map<Long, ProcessExtraInfo> fetchBatch(List<Long> pidBatch) {
    if (pidBatch.isEmpty()) {
      return Map.of();
    }
    String idList = pidBatch.stream().map(String::valueOf).collect(Collectors.joining(","));
    // Se fuerza UTF-8 para que la decodificacion JSON sea consistente en Windows.
    String script =
        "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8; "
            + "$OutputEncoding = [System.Text.Encoding]::UTF8; "
            + "Get-Process -Id "
            + idList
            + " -ErrorAction SilentlyContinue | Select-Object"
            + " Id,WorkingSet64,WorkingSet,PriorityClass,Path | ConvertTo-Json -Depth 2";
    ProcessBuilder builder =
        new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-Command", script);
    builder.redirectErrorStream(true);
    try {
      Process process = builder.start();
      String output;
      try (BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        output = reader.lines().collect(Collectors.joining(System.lineSeparator()));
      }
      int exit = process.waitFor();
      if (exit != 0) {
        LOGGER.debug("PowerShell terminó con código {}", exit);
        return Map.of();
      }
      if (output == null || output.isBlank()) {
        return Map.of();
      }
      JsonNode node = MAPPER.readTree(output);
      List<JsonNode> rows = new ArrayList<>();
      if (node.isArray()) {
        node.forEach(rows::add);
      } else {
        rows.add(node);
      }
      Map<Long, ProcessExtraInfo> batchResult = new HashMap<>();
      for (JsonNode row : rows) {
        if (row == null || row.isNull()) {
          continue;
        }
        long pid = row.path("Id").asLong(-1L);
        if (pid <= 0) {
          continue;
        }
        BigDecimal memMb = parseMemoryMb(row);
        Integer priority = mapPriority(parsePriority(row.get("PriorityClass")));
        boolean system = isSystemProcess(safeText(row.get("Path")));
        batchResult.put(pid, new ProcessExtraInfo(memMb, priority, system));
      }
      return batchResult;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      LOGGER.warn("No se pudo consultar a PowerShell: {}", ex.getMessage());
      return Map.of();
    } catch (IOException ex) {
      LOGGER.warn("No se pudo consultar a PowerShell: {}", ex.getMessage());
      return Map.of();
    }
  }

  private BigDecimal parseMemoryMb(JsonNode row) {
    if (row == null) {
      return null;
    }
    JsonNode memNode = row.get("WorkingSet64");
    if (memNode == null || memNode.isNull()) {
      memNode = row.get("WorkingSet");
    }
    if (memNode == null || memNode.isNull()) {
      return null;
    }
    long bytes;
    if (memNode.isNumber()) {
      bytes = memNode.longValue();
    } else {
      try {
        bytes = Long.parseLong(memNode.asText().trim());
      } catch (NumberFormatException ex) {
        return null;
      }
    }
    return BigDecimal.valueOf(bytes)
        .divide(BigDecimal.valueOf(1024 * 1024), 2, RoundingMode.HALF_UP);
  }

  private String parsePriority(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    if (node.isTextual()) {
      return node.asText();
    }
    if (node.isNumber()) {
      return Long.toString(node.longValue());
    }
    return node.asText();
  }

  private Integer mapPriority(String priorityClass) {
    if (priorityClass == null || priorityClass.isBlank()) {
      return null;
    }
    String normalized = priorityClass.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "idle", "64" -> 1;
      case "belownormal", "16384" -> 3;
      case "normal", "32" -> 5;
      case "abovenormal", "32768" -> 7;
      case "high", "128" -> 9;
      case "realtime", "256" -> 10;
      default -> null;
    };
  }

  private boolean isSystemProcess(String path) {
    if (path == null || path.isBlank()) {
      return true;
    }
    String lower = path.toLowerCase(Locale.ROOT);
    return lower.contains("\\windows\\")
        || lower.contains("\\system32\\")
        || lower.startsWith("c:\\windows")
        || lower.startsWith("c:/windows");
  }

  private String safeText(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    if (node.isTextual()) {
      return node.asText();
    }
    return node.asText();
  }
}
