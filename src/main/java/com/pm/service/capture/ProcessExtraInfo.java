package com.pm.service.capture;

import java.math.BigDecimal;

record ProcessExtraInfo(BigDecimal memMb, Integer priority, boolean systemProcess) {}
