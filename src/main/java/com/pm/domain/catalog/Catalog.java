package com.pm.domain.catalog;

import com.pm.domain.SelectionCriterion;
import com.pm.domain.process.ProcessRecord;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Modelo de dominio que representa un catalogo de procesos capturados o importados. */
public final class Catalog {

  private final Long id;
  private final String nombre;
  private final String descripcion;
  private final SelectionCriterion origen;
  private final int n;
  private final Instant fechaCreacion;
  private final List<ProcessRecord> procesos;

  private Catalog(Builder builder) {
    this.id = builder.id;
    this.nombre = builder.nombre;
    this.descripcion = builder.descripcion;
    this.origen = builder.origen;
    this.n = builder.n;
    this.fechaCreacion = builder.fechaCreacion;
    this.procesos = Collections.unmodifiableList(new ArrayList<>(builder.procesos));
  }

  public Long getId() {
    return id;
  }

  public String getNombre() {
    return nombre;
  }

  public String getDescripcion() {
    return descripcion;
  }

  public SelectionCriterion getOrigen() {
    return origen;
  }

  public int getN() {
    return n;
  }

  public Instant getFechaCreacion() {
    return fechaCreacion;
  }

  public List<ProcessRecord> getProcesos() {
    return procesos;
  }

  public static Builder builder() {
    return new Builder();
  }

  public Builder toBuilder() {
    return new Builder()
        .setId(id)
        .setNombre(nombre)
        .setDescripcion(descripcion)
        .setOrigen(origen)
        .setN(n)
        .setFechaCreacion(fechaCreacion)
        .setProcesos(procesos);
  }

  public static final class Builder {
    private Long id;
    private String nombre;
    private String descripcion;
    private SelectionCriterion origen;
    private int n;
    private Instant fechaCreacion;
    private List<ProcessRecord> procesos = new ArrayList<>();

    public Builder setId(Long id) {
      this.id = id;
      return this;
    }

    public Builder setNombre(String nombre) {
      this.nombre = nombre;
      return this;
    }

    public Builder setDescripcion(String descripcion) {
      this.descripcion = descripcion;
      return this;
    }

    public Builder setOrigen(SelectionCriterion origen) {
      this.origen = origen;
      return this;
    }

    public Builder setN(int n) {
      this.n = n;
      return this;
    }

    public Builder setFechaCreacion(Instant fechaCreacion) {
      this.fechaCreacion = fechaCreacion;
      return this;
    }

    public Builder setProcesos(List<ProcessRecord> procesos) {
      this.procesos = new ArrayList<>(Objects.requireNonNullElseGet(procesos, ArrayList::new));
      return this;
    }

    public Builder addProceso(ProcessRecord processRecord) {
      this.procesos.add(processRecord);
      return this;
    }

    public Catalog build() {
      return new Catalog(this);
    }
  }
}
