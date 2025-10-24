package com.pm.service;

import java.util.List;
import java.util.Locale;

public final class ExpulsivoEvaluator {

  private final List<String> systemUsers;
  private final List<String> namePatterns;

  public ExpulsivoEvaluator(List<String> systemUsers, List<String> namePatterns) {
    this.systemUsers = systemUsers;
    this.namePatterns = namePatterns;
  }

  public boolean isExpulsivo(String nombre, String usuario) {
    if (isSystemUser(usuario)) {
      return false;
    }
    if (matchesPattern(nombre)) {
      return false;
    }
    return true;
  }

  private boolean isSystemUser(String usuario) {
    if (usuario == null) {
      return false;
    }
    String normalized = usuario.toLowerCase(Locale.ROOT);
    return systemUsers.stream().anyMatch(normalized::contains);
  }

  private boolean matchesPattern(String nombre) {
    if (nombre == null) {
      return false;
    }
    String normalized = nombre.toLowerCase(Locale.ROOT);
    return namePatterns.stream().anyMatch(normalized::contains);
  }
}
