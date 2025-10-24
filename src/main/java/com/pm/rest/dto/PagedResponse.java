package com.pm.rest.dto;

import java.util.List;

public record PagedResponse<T>(List<T> content, int page, int size, long total) {}
