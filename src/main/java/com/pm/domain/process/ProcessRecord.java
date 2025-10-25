package com.pm.domain.process;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Entidad inmutable que almacena los datos persistidos de un proceso en un catalogo. */
public final class ProcessRecord {

  private final Long id;
  private final Long catalogId;
  private final long pid;
  private final String nombre;
  private final String usuario;
  private final int prioridad;
  private final boolean expulsivo;
  private final BigDecimal cpuPct;
  private final BigDecimal memMb;
  private final String descripcion;
  private final String filePath;
  private final Instant createdAt;

  private ProcessRecord(Builder builder) {
    this.id = builder.id;
    this.catalogId = builder.catalogId;
    this.pid = builder.pid;
    this.nombre = builder.nombre;
    this.usuario = builder.usuario;
    this.prioridad = builder.prioridad;
    this.expulsivo = builder.expulsivo;
    this.cpuPct = builder.cpuPct;
    this.memMb = builder.memMb;
    this.descripcion = builder.descripcion;
    this.filePath = builder.filePath;
    this.createdAt = builder.createdAt;
  }

  public Long getId() {
    return id;
  }

  public Long getCatalogId() {
    return catalogId;
  }

  public long getPid() {
    return pid;
  }

  public String getNombre() {
    return nombre;
  }

  public String getUsuario() {
    return usuario;
  }

  public int getPrioridad() {
    return prioridad;
  }

  public boolean isExpulsivo() {
    return expulsivo;
  }

  public BigDecimal getCpuPct() {
    return cpuPct;
  }

  public BigDecimal getMemMb() {
    return memMb;
  }

  public String getDescripcion() {
    return descripcion;
  }

  public String getFilePath() {
    return filePath;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Builder toBuilder() {
    return new Builder()
        .setId(id)
        .setCatalogId(catalogId)
        .setPid(pid)
        .setNombre(nombre)
        .setUsuario(usuario)
        .setPrioridad(prioridad)
        .setExpulsivo(expulsivo)
        .setCpuPct(cpuPct)
        .setMemMb(memMb)
        .setDescripcion(descripcion)
        .setFilePath(filePath)
        .setCreatedAt(createdAt);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private Long id;
    private Long catalogId;
    private long pid;
    private String nombre;
    private String usuario;
    private int prioridad;
    private boolean expulsivo;
    private BigDecimal cpuPct;
    private BigDecimal memMb;
    private String descripcion;
    private String filePath;
    private Instant createdAt;

    public Builder setId(Long id) {
      this.id = id;
      return this;
    }

    public Builder setCatalogId(Long catalogId) {
      this.catalogId = catalogId;
      return this;
    }

    public Builder setPid(long pid) {
      this.pid = pid;
      return this;
    }

    public Builder setNombre(String nombre) {
      this.nombre = nombre;
      return this;
    }

    public Builder setUsuario(String usuario) {
      this.usuario = usuario;
      return this;
    }

    public Builder setPrioridad(int prioridad) {
      this.prioridad = prioridad;
      return this;
    }

    public Builder setExpulsivo(boolean expulsivo) {
      this.expulsivo = expulsivo;
      return this;
    }

    public Builder setCpuPct(BigDecimal cpuPct) {
      this.cpuPct = cpuPct;
      return this;
    }

    public Builder setMemMb(BigDecimal memMb) {
      this.memMb = memMb;
      return this;
    }

    public Builder setDescripcion(String descripcion) {
      this.descripcion = descripcion;
      return this;
    }

    public Builder setFilePath(String filePath) {
      this.filePath = filePath;
      return this;
    }

    public Builder setCreatedAt(Instant createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public ProcessRecord build() {
      Objects.requireNonNull(nombre, "nombre requerido");
      return new ProcessRecord(this);
    }
  }
}
