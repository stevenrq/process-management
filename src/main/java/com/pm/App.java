package com.pm;

import com.pm.context.ApplicationContext;
import java.io.IOException;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Aplicacion JavaFX que levanta el contexto de servicios y muestra la ventana principal.
 */
public class App extends Application {

  private static Scene scene;
  private static ApplicationContext context;

  @Override
  public void start(Stage stage) throws IOException {
    context = new ApplicationContext();
    context.getRestServer().start();
    scene = new Scene(loadFXML("primary"), 960, 640);
    stage.setScene(scene);
    stage.show();
  }

  static void setRoot(String fxml) throws IOException {
    scene.setRoot(loadFXML(fxml));
  }

  private static Parent loadFXML(String fxml) throws IOException {
    FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource(fxml + ".fxml"));
    // Forzamos la creacion manual para poder enlazar el ApplicationContext despues de cargar.
    fxmlLoader.setControllerFactory(
        type -> {
          try {
            return type.getDeclaredConstructor().newInstance();
          } catch (Exception ex) {
            throw new IllegalStateException("No se pudo crear controlador " + type.getName(), ex);
          }
        });
    Parent parent = fxmlLoader.load();
    Object controller = fxmlLoader.getController();
    if (controller instanceof com.pm.ui.AppContextAware aware && context != null) {
      // Se inyecta el contexto en controladores que lo declaran expresamente.
      aware.setApplicationContext(context);
    }
    return parent;
  }

  @Override
  public void stop() {
    if (context != null) {
      context.close();
    }
  }

  public static ApplicationContext getApplicationContext() {
    return context;
  }

  public static void main(String[] args) {
    launch();
  }
}
