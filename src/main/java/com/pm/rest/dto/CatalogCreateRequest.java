package com.pm.rest.dto;

/** Representa el cuerpo de la peticion para crear un catalogo nuevo. */
public record CatalogCreateRequest(int n, String criterio, String nombre, String descripcion) {}
