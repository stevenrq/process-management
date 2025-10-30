package com.pm.persistence;

import com.pm.domain.PagedResult;
import com.pm.domain.SelectionCriterion;
import com.pm.domain.catalog.Catalog;
import com.pm.domain.catalog.CatalogMetadata;
import com.pm.domain.catalog.CatalogSort;
import com.pm.domain.process.ProcessFilter;
import com.pm.domain.process.ProcessRecord;
import com.pm.domain.process.ProcessSort;
import com.pm.domain.process.ProcessUpdate;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * Encapsula el acceso JDBC para persistir catalogos y sus procesos asociados en la base de datos.
 */
public final class CatalogRepository {

  private final DataSource dataSource;

  public CatalogRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  public Catalog saveCatalogWithProcesses(Catalog catalog) {
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      long catalogId =
          insertCatalog(
              connection,
              catalog.getNombre(),
              catalog.getDescripcion(),
              catalog.getOrigen(),
              catalog.getN());
      List<ProcessRecord> storedProcesses =
          insertProcesses(connection, catalogId, catalog.getProcesos());
      connection.commit();

      return catalog.toBuilder()
          .setId(catalogId)
          .setProcesos(storedProcesses)
          .setFechaCreacion(fetchCatalogCreation(connection, catalogId))
          .build();
    } catch (SQLException ex) {
      throw new IllegalStateException("Error al guardar catálogo", ex);
    }
  }

  private Instant fetchCatalogCreation(Connection connection, long catalogId) throws SQLException {
    try (PreparedStatement ps =
        connection.prepareStatement("SELECT fecha_creacion FROM catalog WHERE id_catalog = ?")) {
      ps.setLong(1, catalogId);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          Timestamp ts = rs.getTimestamp(1);
          return ts == null ? Instant.now() : ts.toInstant();
        }
        return Instant.now();
      }
    }
  }

  private long insertCatalog(
      Connection connection, String nombre, String descripcion, SelectionCriterion origen, int n)
      throws SQLException {
    try (PreparedStatement ps =
        connection.prepareStatement(
            "INSERT INTO catalog(nombre, descripcion, origen, n) VALUES (?, ?, ?, ?)",
            Statement.RETURN_GENERATED_KEYS)) {
      ps.setString(1, nombre);
      ps.setString(2, descripcion);
      ps.setString(3, origen.name());
      ps.setInt(4, n);
      ps.executeUpdate();
      try (ResultSet keys = ps.getGeneratedKeys()) {
        if (keys.next()) {
          return keys.getLong(1);
        }
        throw new SQLException("No se generó id_catalog");
      }
    }
  }

  private List<ProcessRecord> insertProcesses(
      Connection connection, long catalogId, List<ProcessRecord> processes) throws SQLException {

    List<ProcessRecord> stored = new ArrayList<>();
    String sql =
        """
        INSERT INTO process(
          id_catalog,
          pid,
          nombre,
          usuario,
          prioridad,
          expulsivo,
          cpu_pct,
          mem_mb,
          descripcion,
          file_path)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

    try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

      ps.setLong(1, catalogId);

      for (ProcessRecord processRecord : processes) {
        ps.setLong(2, processRecord.getPid());
        ps.setString(3, processRecord.getNombre());
        ps.setString(4, processRecord.getUsuario());
        ps.setInt(5, processRecord.getPrioridad());
        ps.setBoolean(6, processRecord.isExpulsivo());

        ps.setObject(7, processRecord.getCpuPct(), java.sql.Types.DECIMAL);
        ps.setObject(8, processRecord.getMemMb(), java.sql.Types.DECIMAL);

        ps.setString(9, processRecord.getDescripcion());
        ps.setString(10, processRecord.getFilePath());
        ps.addBatch();
      }

      ps.executeBatch();

      try (ResultSet keys = ps.getGeneratedKeys()) {
        int index = 0;
        while (keys.next() && index < processes.size()) {
          long id = keys.getLong(1);
          ProcessRecord original = processes.get(index++);
          stored.add(
              original.toBuilder()
                  .setId(id)
                  .setCatalogId(catalogId)
                  .setCreatedAt(Instant.now())
                  .build());
        }
      }
    }
    return stored;
  }

  public PagedResult<CatalogMetadata> findCatalogs(
      Optional<String> search,
      Optional<SelectionCriterion> origin,
      CatalogSort sort,
      int page,
      int size) {
    int offset = (page - 1) * size;
    List<String> conditions = new ArrayList<>();
    List<Object> params = new ArrayList<>();
    search.ifPresent(
        value -> {
          conditions.add("(LOWER(nombre) LIKE ? OR LOWER(descripcion) LIKE ?)");
          String term = "%" + value.toLowerCase() + "%";
          params.add(term);
          params.add(term);
        });
    origin.ifPresent(
        value -> {
          conditions.add("origen = ?");
          params.add(value.name());
        });

    String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    String query =
        "SELECT id_catalog, nombre, descripcion, origen, n, fecha_creacion FROM catalog"
            + where
            + " ORDER BY "
            + sort.sql()
            + " LIMIT ? OFFSET ?";
    String countSql = "SELECT COUNT(1) FROM catalog" + where;

    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      long total = executeCount(connection, countSql, params);
      List<CatalogMetadata> content = new ArrayList<>();
      try (PreparedStatement ps = connection.prepareStatement(query)) {
        int index = 1;
        for (Object param : params) {
          ps.setObject(index++, param);
        }
        ps.setInt(index++, size);
        ps.setInt(index, offset);
        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            content.add(mapCatalogMetadata(rs));
          }
        }
      }
      connection.commit();
      return new PagedResult<>(content, page, size, total);
    } catch (SQLException ex) {
      throw new IllegalStateException("Error al listar catálogos", ex);
    }
  }

  private long executeCount(Connection connection, String sql, List<Object> params)
      throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(sql)) {
      int index = 1;
      for (Object param : params) {
        ps.setObject(index++, param);
      }
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return rs.getLong(1);
        }
        return 0;
      }
    }
  }

  private CatalogMetadata mapCatalogMetadata(ResultSet rs) throws SQLException {
    return new CatalogMetadata(
        rs.getLong("id_catalog"),
        rs.getString("nombre"),
        rs.getString("descripcion"),
        SelectionCriterion.fromString(rs.getString("origen")),
        rs.getInt("n"),
        rs.getTimestamp("fecha_creacion").toInstant());
  }

  public Optional<Catalog> findCatalog(long catalogId) {
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      Optional<CatalogMetadata> metadata = findCatalogMetadata(connection, catalogId);
      if (metadata.isEmpty()) {
        connection.commit();
        return Optional.empty();
      }
      List<ProcessRecord> processes = findProcessesForCatalog(connection, catalogId);
      connection.commit();
      Catalog catalog =
          Catalog.builder()
              .setId(metadata.get().id())
              .setNombre(metadata.get().nombre())
              .setDescripcion(metadata.get().descripcion())
              .setOrigen(metadata.get().origen())
              .setN(metadata.get().n())
              .setFechaCreacion(metadata.get().fechaCreacion())
              .setProcesos(processes)
              .build();
      return Optional.of(catalog);
    } catch (SQLException ex) {
      throw new IllegalStateException("Error al obtener catálogo", ex);
    }
  }

  private Optional<CatalogMetadata> findCatalogMetadata(Connection connection, long catalogId)
      throws SQLException {
    try (PreparedStatement ps =
        connection.prepareStatement(
            """
            SELECT id_catalog, nombre, descripcion, origen, n, fecha_creacion
            FROM catalog WHERE id_catalog = ?
            """)) {
      ps.setLong(1, catalogId);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return Optional.of(mapCatalogMetadata(rs));
        }
        return Optional.empty();
      }
    }
  }

  private List<ProcessRecord> findProcessesForCatalog(Connection connection, long catalogId)
      throws SQLException {
    List<ProcessRecord> list = new ArrayList<>();
    try (PreparedStatement ps =
        connection.prepareStatement(
            """
            SELECT id_process, id_catalog, pid, nombre, usuario, prioridad, expulsivo,
                   cpu_pct, mem_mb, descripcion, file_path, created_at
            FROM process
            WHERE id_catalog = ?
            ORDER BY created_at DESC
            """)) {
      ps.setLong(1, catalogId);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          list.add(mapProcess(rs));
        }
      }
    }
    return list;
  }

  private ProcessRecord mapProcess(ResultSet rs) throws SQLException {
    BigDecimal cpu = rs.getBigDecimal("cpu_pct");
    BigDecimal mem = rs.getBigDecimal("mem_mb");
    Timestamp createdTs = rs.getTimestamp("created_at");
    return ProcessRecord.builder()
        .setId(rs.getLong("id_process"))
        .setCatalogId(rs.getLong("id_catalog"))
        .setPid(rs.getLong("pid"))
        .setNombre(rs.getString("nombre"))
        .setUsuario(rs.getString("usuario"))
        .setPrioridad(rs.getInt("prioridad"))
        .setExpulsivo(rs.getBoolean("expulsivo"))
        .setCpuPct(cpu)
        .setMemMb(mem)
        .setDescripcion(rs.getString("descripcion"))
        .setFilePath(rs.getString("file_path"))
        .setCreatedAt(createdTs == null ? null : createdTs.toInstant())
        .build();
  }

  public PagedResult<ProcessRecord> findProcesses(
      long catalogId, ProcessFilter filter, ProcessSort sort, int page, int size) {
    int offset = (page - 1) * size;
    List<String> conditions = new ArrayList<>();
    List<Object> params = new ArrayList<>();
    conditions.add("id_catalog = ?");
    params.add(catalogId);
    filter
        .usuario()
        .ifPresent(
            value -> {
              conditions.add("LOWER(usuario) = ?");
              params.add(value.toLowerCase());
            });
    filter
        .expulsivo()
        .ifPresent(
            value -> {
              conditions.add("expulsivo = ?");
              params.add(value);
            });
    filter
        .nombreLike()
        .ifPresent(
            value -> {
              conditions.add("LOWER(nombre) LIKE ?");
              params.add("%" + value.toLowerCase() + "%");
            });
    filter
        .pid()
        .ifPresent(
            value -> {
              conditions.add("pid = ?");
              params.add(value);
            });

    String where = " WHERE " + String.join(" AND ", conditions);
    String sql =
        "SELECT id_process, id_catalog, pid, nombre, usuario, prioridad, expulsivo, cpu_pct,"
            + " mem_mb, descripcion, file_path, created_at FROM process "
            + where
            + " ORDER BY "
            + sort.sql()
            + " LIMIT ? OFFSET ?";
    String count = "SELECT COUNT(1) FROM process" + where;

    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      long total = executeCount(connection, count, params);
      List<ProcessRecord> content = new ArrayList<>();
      try (PreparedStatement ps = connection.prepareStatement(sql)) {
        int index = 1;
        for (Object param : params) {
          ps.setObject(index++, param);
        }
        ps.setInt(index++, size);
        ps.setInt(index, offset);
        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            content.add(mapProcess(rs));
          }
        }
      }
      connection.commit();
      return new PagedResult<>(content, page, size, total);
    } catch (SQLException ex) {
      throw new IllegalStateException("Error al listar procesos", ex);
    }
  }

  public Optional<ProcessRecord> findProcess(long catalogId, long processId) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement ps =
            connection.prepareStatement(
                """
                SELECT id_process, id_catalog, pid, nombre, usuario, prioridad, expulsivo,
                       cpu_pct, mem_mb, descripcion, file_path, created_at
                FROM process
                WHERE id_catalog = ? AND id_process = ?
                """)) {
      ps.setLong(1, catalogId);
      ps.setLong(2, processId);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return Optional.of(mapProcess(rs));
        }
      }
      return Optional.empty();
    } catch (SQLException ex) {
      throw new IllegalStateException("Error al obtener proceso", ex);
    }
  }

  public void updateProcess(long catalogId, long processId, ProcessUpdate update) {
    StringBuilder sql = new StringBuilder("UPDATE process SET ");
    List<String> sets = new ArrayList<>();
    List<Object> params = new ArrayList<>();
    update
        .descripcion()
        .ifPresent(
            value -> {
              sets.add("descripcion = ?");
              params.add(value);
            });
    update
        .prioridad()
        .ifPresent(
            value -> {
              sets.add("prioridad = ?");
              params.add(value);
            });
    update
        .expulsivo()
        .ifPresent(
            value -> {
              sets.add("expulsivo = ?");
              params.add(value);
            });
    if (sets.isEmpty()) {
      return;
    }
    sql.append(String.join(", ", sets));
    sql.append(" WHERE id_catalog = ? AND id_process = ?");
    params.add(catalogId);
    params.add(processId);
    try (Connection connection = dataSource.getConnection();
        PreparedStatement ps = connection.prepareStatement(sql.toString())) {
      int index = 1;
      for (Object param : params) {
        ps.setObject(index++, param);
      }
      ps.executeUpdate();
      connection.commit();
    } catch (SQLException ex) {
      throw new IllegalStateException("Error al actualizar proceso", ex);
    }
  }

  public void deleteProcess(long catalogId, long processId) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement ps =
            connection.prepareStatement(
                "DELETE FROM process WHERE id_catalog = ? AND id_process = ?")) {
      ps.setLong(1, catalogId);
      ps.setLong(2, processId);
      ps.executeUpdate();
      connection.commit();
    } catch (SQLException ex) {
      throw new IllegalStateException("Error al eliminar proceso", ex);
    }
  }

  public void updateCatalogMetadata(long catalogId, String nombre, String descripcion) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement ps =
            connection.prepareStatement(
                "UPDATE catalog SET nombre = ?, descripcion = ? WHERE id_catalog = ?")) {
      ps.setString(1, nombre);
      ps.setString(2, descripcion);
      ps.setLong(3, catalogId);
      ps.executeUpdate();
      connection.commit();
    } catch (SQLException ex) {
      throw new IllegalStateException("Error al actualizar catálogo", ex);
    }
  }

  public void deleteCatalog(long catalogId) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement ps =
            connection.prepareStatement("DELETE FROM catalog WHERE id_catalog = ?")) {
      ps.setLong(1, catalogId);
      ps.executeUpdate();
      connection.commit();
    } catch (SQLException ex) {
      throw new IllegalStateException("Error al eliminar catálogo", ex);
    }
  }
}
