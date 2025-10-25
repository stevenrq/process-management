package com.pm.rest.dto;

import java.util.List;

/** Respuesta generica para listas paginadas expuestas a la interfaz. */
public record PagedResponse<T>(List<T> content, int page, int size, long total) {}
