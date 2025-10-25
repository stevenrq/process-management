package com.pm.rest.dto;

/** Cuerpo de la peticion parcial para actualizar un proceso existente. */
public record ProcessUpdateRequest(String descripcion, Integer prioridad, Boolean expulsivo) {}
