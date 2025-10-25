package com.pm.ui;

import com.pm.context.ApplicationContext;

/** Contrato para controladores JavaFX que requieren acceso al ApplicationContext. */
public interface AppContextAware {

  void setApplicationContext(ApplicationContext context);
}
