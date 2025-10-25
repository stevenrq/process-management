package com.pm.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

/** Gestiona el ciclo de vida del pool de conexiones HikariCP configurado para la aplicacion. */
public final class DatabaseManager implements AutoCloseable {

  private final HikariDataSource dataSource;

  public DatabaseManager(AppConfig config) {
    HikariConfig hikariConfig = new HikariConfig();
    hikariConfig.setJdbcUrl(config.getDbUrl());
    hikariConfig.setUsername(config.getDbUser());
    hikariConfig.setPassword(config.getDbPassword());
    hikariConfig.setMaximumPoolSize(config.getDbPoolSize());
    hikariConfig.setPoolName("process-management-pool");
    hikariConfig.setAutoCommit(false);
    this.dataSource = new HikariDataSource(hikariConfig);
  }

  public DataSource getDataSource() {
    return dataSource;
  }

  public Connection getConnection() throws SQLException {
    return dataSource.getConnection();
  }

  @Override
  public void close() {
    dataSource.close();
  }
}
