package com.pm.service.capture;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class WindowsProcessInfoProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(WindowsProcessInfoProvider.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  Map<Long, ProcessExtraInfo> fetch(Collection<Long> pids) {
    if (pids == null || pids.isEmpty()) {
      return Map.of();
    }
    String idList = pids.stream().map(String::valueOf).collect(Collectors.joining(","));
    String script =
        "Get-Process -Id "
            + idList
            + " -ErrorAction SilentlyContinue | "
            + "Select-Object Id,WorkingSet64,PriorityClass,Path | ConvertTo-Json -Depth 2";
    ProcessBuilder builder =
        new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-Command", script);
    builder.redirectErrorStream(true);
    try {
      Process process = builder.start();
      String output;
      try (BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        output = reader.lines().collect(Collectors.joining());
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
      List<WindowsProcessRow> rows = new ArrayList<>();
      if (node.isArray()) {
        rows = MAPPER.convertValue(node, new TypeReference<List<WindowsProcessRow>>() {});
      } else {
        rows.add(MAPPER.convertValue(node, WindowsProcessRow.class));
      }
      Map<Long, ProcessExtraInfo> map = new HashMap<>();
      for (WindowsProcessRow row : rows) {
        if (row == null || row.id == null) {
          continue;
        }
        BigDecimal memMb = null;
        Long workingSetBytes =
            row.workingSet64 != null ? row.workingSet64 : row.workingSet;
        if (workingSetBytes != null) {
          memMb =
              BigDecimal.valueOf(workingSetBytes)
                  .divide(BigDecimal.valueOf(1024 * 1024), 2, RoundingMode.HALF_UP);
        }
        Integer priority = mapPriority(row.priorityClass);
        boolean system = isSystemProcess(row.path);
        map.put(row.id, new ProcessExtraInfo(memMb, priority, system));
      }
      return map;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      LOGGER.warn("No se pudo consultar a PowerShell: {}", ex.getMessage());
      return Map.of();
    } catch (IOException ex) {
      LOGGER.warn("No se pudo consultar a PowerShell: {}", ex.getMessage());
      return Map.of();
    }
  }

  private Integer mapPriority(String priorityClass) {
    if (priorityClass == null) {
      return null;
    }
    return switch (priorityClass.toLowerCase(Locale.ROOT)) {
      case "idle" -> 1;
      case "belownormal" -> 3;
      case "normal" -> 5;
      case "abovenormal" -> 7;
      case "high" -> 9;
      case "realtime" -> 10;
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

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class WindowsProcessRow {
    public Long id;
    public Long workingSet64;
    public Long workingSet;
    public String priorityClass;
    public String path;

    public void setId(Long id) {
      this.id = id;
    }

    public void setWorkingSet64(Long workingSet64) {
      this.workingSet64 = workingSet64;
    }

    public void setWorkingSet(Long workingSet) {
      this.workingSet = workingSet;
    }

    public void setPriorityClass(String priorityClass) {
      this.priorityClass = priorityClass;
    }

    public void setPath(String path) {
      this.path = path;
    }
  }
}

