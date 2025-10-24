package com.pm.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads application configuration from {@code application.properties}, allowing overrides through
 * system properties or environment variables that match the property name in upper snake case.
 */
public final class AppConfig {

  private static final Logger LOGGER = LoggerFactory.getLogger(AppConfig.class);
  private final Properties properties = new Properties();

  public AppConfig() {
    loadProperties();
  }

  private void loadProperties() {
    try (InputStream in =
        AppConfig.class.getClassLoader().getResourceAsStream("application.properties")) {
      if (in == null) {
        throw new IllegalStateException("application.properties not found on classpath");
      }
      properties.load(in);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load configuration", e);
    }
  }

  private String readProperty(String key) {
    String sysValue = System.getProperty(key);
    if (sysValue != null && !sysValue.isBlank()) {
      return sysValue;
    }
    String envKey = key.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
    String envValue = System.getenv(envKey);
    if (envValue != null && !envValue.isBlank()) {
      return envValue;
    }
    return properties.getProperty(key);
  }

  private String readRequired(String key) {
    String value = readProperty(key);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Configuration property missing: " + key);
    }
    return value;
  }

  public String getDbUrl() {
    return readRequired("app.db.url");
  }

  public String getDbUser() {
    return readRequired("app.db.user");
  }

  public String getDbPassword() {
    return readRequired("app.db.password");
  }

  public int getDbPoolSize() {
    return parseInt("app.db.pool.size", 5);
  }

  public int getRestPort() {
    return parseInt("app.rest.port", 8080);
  }

  public String getAllowedCorsOrigin() {
    return Optional.ofNullable(readProperty("app.rest.cors.allowedOrigin"))
        .filter(value -> !value.isBlank())
        .orElse("*");
  }

  public Path getFilesBaseDir() {
    return resolvePath(readRequired("app.files.baseDir"));
  }

  public Path getExportDir() {
    return resolvePath(readRequired("app.export.dir"));
  }

  public Path getImportDir() {
    return resolvePath(readRequired("app.import.dir"));
  }

  public Duration getCaptureSampleDuration() {
    long millis = parseLong("app.capture.sampleMillis", 300);
    if (millis < 50) {
      LOGGER.warn("Configured capture sample millis too low ({}), using 50ms", millis);
      millis = 50;
    }
    return Duration.ofMillis(millis);
  }

  public List<String> getExpulsiveSystemUsers() {
    return readList("app.capture.expulsivo.systemUsers");
  }

  public List<String> getExpulsiveNamePatterns() {
    return readList("app.capture.expulsivo.namePatterns");
  }

  private List<String> readList(String key) {
    String value = readProperty(key);
    if (value == null || value.isBlank()) {
      return Collections.emptyList();
    }
    return Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(str -> !str.isEmpty())
        .map(str -> str.toLowerCase(Locale.ROOT))
        .collect(Collectors.toList());
  }

  private Path resolvePath(String value) {
    Path path = Paths.get(value);
    if (!path.isAbsolute()) {
      path = Paths.get(System.getProperty("user.dir")).resolve(path).normalize();
    }
    return path;
  }

  private int parseInt(String key, int defaultValue) {
    String value = readProperty(key);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException ex) {
      throw new IllegalStateException("Invalid integer value for " + key + ": " + value, ex);
    }
  }

  private long parseLong(String key, long defaultValue) {
    String value = readProperty(key);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      return Long.parseLong(value.trim());
    } catch (NumberFormatException ex) {
      throw new IllegalStateException("Invalid long value for " + key + ": " + value, ex);
    }
  }

  public void logConfiguration() {
    LOGGER.info("REST server: port={}, CORS origin={}", getRestPort(), getAllowedCorsOrigin());
    LOGGER.info(
        "Directories: files={}, export={}, import={}",
        getFilesBaseDir(),
        getExportDir(),
        getImportDir());
    LOGGER.info(
        "Expulsivo heuristics: users={}, namePatterns={}",
        getExpulsiveSystemUsers(),
        getExpulsiveNamePatterns());
  }
}
