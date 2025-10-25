package com.pm.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verifica que el esquema relacional requerido exista y crea tablas e indices si faltan.
 */
public final class DatabaseInitializer {

  private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseInitializer.class);

  private final DataSource dataSource;

  public DatabaseInitializer(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  public void initialize() {
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try (Statement st = connection.createStatement()) {
        st.execute(
            """
            CREATE TABLE IF NOT EXISTS catalog (
              id_catalog     BIGINT PRIMARY KEY AUTO_INCREMENT,
              nombre         VARCHAR(120) NOT NULL,
              descripcion    VARCHAR(5000),
              origen         VARCHAR(8) NOT NULL,
              n              INT NOT NULL,
              fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """);
        st.execute(
            """
            CREATE TABLE IF NOT EXISTS process (
              id_process   BIGINT PRIMARY KEY AUTO_INCREMENT,
              id_catalog   BIGINT NOT NULL,
              pid          BIGINT NOT NULL,
              nombre       VARCHAR(120) NOT NULL,
              usuario      VARCHAR(80),
              prioridad    INT NOT NULL DEFAULT 0,
              expulsivo    BOOLEAN NOT NULL DEFAULT TRUE,
              cpu_pct      DECIMAL(6,2),
              mem_mb       DECIMAL(12,2),
              descripcion  VARCHAR(5000),
              file_path    VARCHAR(300),
              created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
              CONSTRAINT fk_process_catalog FOREIGN KEY (id_catalog)
                REFERENCES catalog(id_catalog) ON DELETE CASCADE
            )
            """);
      }
      ensureIndex(
          connection,
          "process",
          "idx_process_catalog",
          "CREATE INDEX idx_process_catalog ON process(id_catalog)");
      ensureIndex(
          connection, "process", "idx_process_pid", "CREATE INDEX idx_process_pid ON process(pid)");
      connection.commit();
      LOGGER.info("Database schema verified");
    } catch (SQLException ex) {
      throw new IllegalStateException("Unable to initialize database schema", ex);
    }
  }

  private void ensureIndex(
      Connection connection, String tableName, String indexName, String createSql)
      throws SQLException {
    if (indexExists(connection, tableName, indexName)) {
      return;
    }
    try (Statement st = connection.createStatement()) {
      st.execute(createSql);
    }
  }

  private boolean indexExists(Connection connection, String tableName, String indexName)
      throws SQLException {
    String sql =
        """
        SELECT COUNT(1)
        FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?
        """;
    try (PreparedStatement ps = connection.prepareStatement(sql)) {
      ps.setString(1, tableName);
      ps.setString(2, indexName);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return rs.getInt(1) > 0;
        }
        return false;
      }
    }
  }
}
