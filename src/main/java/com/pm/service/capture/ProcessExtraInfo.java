package com.pm.service.capture;

import java.math.BigDecimal;

/** Agrupa la informacion complementaria obtenida para cada proceso en Windows. */
record ProcessExtraInfo(BigDecimal memMb, Integer priority, boolean systemProcess) {}
