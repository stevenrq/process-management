package com.pm.rest.dto;

import java.util.List;

/** Estructura comun de los errores que expone el API REST. */
public record ErrorResponse(String code, String message, List<String> details) {}
