package com.pm.service.capture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Obtiene informacion adicional de procesos en sistemas Linux consultando archivos expuestos en
 * {@code /proc}.
 */
final class LinuxProcessInfoProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(LinuxProcessInfoProvider.class);

  Map<Long, ProcessExtraInfo> fetch(Collection<Long> pids) {
    if (pids == null || pids.isEmpty()) {
      return Map.of();
    }
    Map<Long, ProcessExtraInfo> result = new HashMap<>();
    for (Long id : pids) {
      if (id == null || id <= 0) {
        continue;
      }
      ProcessExtraInfo info = readInfo(id);
      if (info != null) {
        result.put(id, info);
      }
    }
    return result;
  }

  private ProcessExtraInfo readInfo(long pid) {
    Integer priority = readPriority(pid);
    boolean system = isSystemProcess(pid);
    if (priority == null && !system) {
      return null;
    }
    return new ProcessExtraInfo(null, priority, system);
  }

  private Integer readPriority(long pid) {
    Path stat = Path.of("/proc", Long.toString(pid), "stat");
    if (!Files.exists(stat)) {
      return null;
    }
    try {
      String content = Files.readString(stat);
      int closing = content.lastIndexOf(')');
      if (closing < 0 || closing + 1 >= content.length()) {
        return null;
      }
      String[] parts = content.substring(closing + 1).trim().split("\\s+");
      if (parts.length <= 16) {
        return null;
      }
      int niceValue = Integer.parseInt(parts[16]);
      return mapNiceToPriority(niceValue);
    } catch (IOException | NumberFormatException ex) {
      LOGGER.debug("No se pudo obtener prioridad linux para pid {}: {}", pid, ex.getMessage());
      return null;
    }
  }

  private boolean isSystemProcess(long pid) {
    Path status = Path.of("/proc", Long.toString(pid), "status");
    if (!Files.exists(status)) {
      return false;
    }
    try {
      for (String line : Files.readAllLines(status)) {
        if (line.startsWith("Uid:")) {
          String[] parts = line.substring(4).trim().split("\\s+");
          if (parts.length > 0) {
            try {
              int uid = Integer.parseInt(parts[0]);
              if (uid == 0) {
                return true;
              }
            } catch (NumberFormatException ignored) {
              // Ignoramos valores inesperados y continuamos con el resto del archivo.
            }
          }
          break;
        }
      }
    } catch (IOException ex) {
      LOGGER.debug("No se pudo determinar UID para pid {}: {}", pid, ex.getMessage());
    }
    // Como heurística adicional, consideramos procesos cuya ruta ejecutable está en /sbin o
    // /usr/sbin
    // como procesos de sistema.
    Path exe = Path.of("/proc", Long.toString(pid), "exe");
    try {
      Path target = Files.readSymbolicLink(exe);
      String lower = target.toString().toLowerCase(Locale.ROOT);
      return lower.startsWith("/usr/sbin") || lower.startsWith("/sbin");
    } catch (IOException ignored) {
      return false;
    }
  }

  private Integer mapNiceToPriority(int niceValue) {
    int clamped = Math.max(-20, Math.min(19, niceValue));
    if (clamped <= -15) {
      return 10; // Equivalente a REALTIME
    }
    if (clamped <= -10) {
      return 9; // Equivalente a HIGH
    }
    if (clamped <= -5) {
      return 7; // Equivalente a ABOVE_NORMAL
    }
    if (clamped <= 4) {
      return 5; // Equivalente a NORMAL
    }
    if (clamped <= 10) {
      return 3; // Equivalente a BELOW_NORMAL
    }
    return 1; // Equivalente a IDLE
  }
}
