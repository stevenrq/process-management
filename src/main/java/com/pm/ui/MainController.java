package com.pm.ui;

import com.pm.context.ApplicationContext;
import com.pm.domain.SelectionCriterion;
import com.pm.domain.ValidationException;
import com.pm.domain.catalog.CatalogMetadata;
import com.pm.domain.catalog.CatalogSort;
import com.pm.domain.process.ProcessFilter;
import com.pm.domain.process.ProcessRecord;
import com.pm.domain.process.ProcessSort;
import com.pm.domain.process.ProcessUpdate;
import com.pm.service.CatalogService;
import com.pm.service.JsonCatalogService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controlador principal de la interfaz JavaFX que coordina el flujo entre la vista y los servicios.
 */
public final class MainController implements AppContextAware {

  private static final Logger LOGGER = LoggerFactory.getLogger(MainController.class);
  private static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  @FXML private TextField txtNombre;
  @FXML private TextArea txtDescripcion;
  @FXML private ComboBox<SelectionCriterion> cboCriterio;
  @FXML private Spinner<Integer> spnN;

  @FXML private TextField txtBuscar;
  @FXML private ComboBox<String> cboFiltroCriterio;
  @FXML private Button btnCapturarCatalogo;
  @FXML private TableView<CatalogMetadata> tblCatalogos;
  @FXML private TableColumn<CatalogMetadata, Number> colCatalogoId;
  @FXML private TableColumn<CatalogMetadata, String> colCatalogoNombre;
  @FXML private TableColumn<CatalogMetadata, String> colCatalogoOrigen;
  @FXML private TableColumn<CatalogMetadata, Number> colCatalogoN;
  @FXML private TableColumn<CatalogMetadata, String> colCatalogoFecha;

  @FXML private Label lblCatalogoNombre;
  @FXML private Label lblCatalogoDescripcion;
  @FXML private Label lblCatalogoOrigen;
  @FXML private Label lblCatalogoN;
  @FXML private Label lblCatalogoFecha;

  @FXML private TableView<ProcessRecord> tblProcesos;
  @FXML private TableColumn<ProcessRecord, Number> colProcesoPid;
  @FXML private TableColumn<ProcessRecord, String> colProcesoNombre;
  @FXML private TableColumn<ProcessRecord, String> colProcesoUsuario;
  @FXML private TableColumn<ProcessRecord, BigDecimal> colProcesoCpu;
  @FXML private TableColumn<ProcessRecord, BigDecimal> colProcesoMem;
  @FXML private TableColumn<ProcessRecord, Number> colProcesoPrioridad;
  @FXML private TableColumn<ProcessRecord, String> colProcesoExpulsivo;

  @FXML private TextField txtProcesoBuscar;
  @FXML private TextField txtProcesoUsuario;
  @FXML private ComboBox<String> cboProcesoExpulsivo;
  @FXML private ComboBox<String> cboProcesoOrden;
  @FXML private Button btnProcesoBuscar;

  @FXML private TextArea txtProcesoDescripcion;
  @FXML private Spinner<Integer> spnProcesoPrioridad;
  @FXML private CheckBox chkProcesoExpulsivo;

  @FXML private Label lblStatus;
  @FXML private ProgressIndicator piCapturaCatalogo;

  private ApplicationContext context;
  private CatalogService catalogService;
  private JsonCatalogService jsonCatalogService;
  private CatalogMetadata selectedCatalog;
  private ProcessRecord selectedProcess;
  private final ObservableList<CatalogMetadata> catalogos = FXCollections.observableArrayList();
  private final ObservableList<ProcessRecord> procesos = FXCollections.observableArrayList();
  private final ObservableList<String> procesoOrdenOpciones = FXCollections.observableArrayList();

  private static final String ORDEN_RECIENTE = "Recientes primero";
  private static final String ORDEN_ANTIGUO = "Antiguos primero";
  private static final String ORDEN_CPU_DESC = "Mayor CPU";
  private static final String ORDEN_CPU_ASC = "Menor CPU";
  private static final String ORDEN_MEM_DESC = "Mayor memoria";
  private static final String ORDEN_PRIORIDAD_DESC = "Mayor prioridad";
  private static final String ORDEN_PRIORIDAD_ASC = "Menor prioridad";
  private static final String ORDEN_NOMBRE_ASC = "Nombre A-Z";

  @FXML
  public void initialize() {
    cboCriterio.setItems(FXCollections.observableArrayList(SelectionCriterion.values()));
    cboCriterio.getSelectionModel().select(SelectionCriterion.CPU);
    spnN.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 500, 10, 1));

    cboFiltroCriterio.setItems(FXCollections.observableArrayList("Todos", "CPU", "MEM"));
    cboFiltroCriterio.getSelectionModel().selectFirst();

    configureCatalogTable();
    configureProcessTable();
    spnProcesoPrioridad.setValueFactory(
        new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 99, 0));
    configureProcessFilters();

    tblCatalogos
        .getSelectionModel()
        .selectedItemProperty()
        .addListener((obs, oldValue, newValue) -> onCatalogSelected(newValue));
    tblProcesos
        .getSelectionModel()
        .selectedItemProperty()
        .addListener((obs, oldValue, newValue) -> onProcessSelected(newValue));
  }

  private void configureCatalogTable() {
    tblCatalogos.setItems(catalogos);
    colCatalogoId.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().id()));
    colCatalogoNombre.setCellValueFactory(
        cell -> new ReadOnlyObjectWrapper<>(cell.getValue().nombre()));
    colCatalogoOrigen.setCellValueFactory(
        cell -> new ReadOnlyObjectWrapper<>(cell.getValue().origen().name()));
    colCatalogoN.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().n()));
    colCatalogoFecha.setCellValueFactory(
        cell -> new ReadOnlyObjectWrapper<>(formatInstant(cell.getValue().fechaCreacion())));
  }

  private void configureProcessTable() {
    tblProcesos.setItems(procesos);
    colProcesoPid.setCellValueFactory(
        cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getPid()));
    colProcesoNombre.setCellValueFactory(
        cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getNombre()));
    colProcesoUsuario.setCellValueFactory(
        cell -> new ReadOnlyObjectWrapper<>(nullToDash(cell.getValue().getUsuario())));
    colProcesoCpu.setCellValueFactory(
        cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getCpuPct()));
    colProcesoMem.setCellValueFactory(
        cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getMemMb()));
    colProcesoCpu.setCellFactory(column -> createNumericCell(2, "%"));
    colProcesoMem.setCellFactory(column -> createNumericCell(2, " MB"));
    colProcesoCpu.setComparator(MainController::compareBigDecimal);
    colProcesoMem.setComparator(MainController::compareBigDecimal);
    colProcesoPrioridad.setCellValueFactory(
        cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getPrioridad()));
    colProcesoExpulsivo.setCellValueFactory(
        cell -> new ReadOnlyObjectWrapper<>(cell.getValue().isExpulsivo() ? "Si" : "No"));
  }

  private void configureProcessFilters() {
    if (cboProcesoExpulsivo != null) {
      cboProcesoExpulsivo.setItems(
          FXCollections.observableArrayList("Todos", "Expulsivos", "No expulsivos"));
      cboProcesoExpulsivo.getSelectionModel().selectFirst();
    }
    procesoOrdenOpciones.setAll(
        List.of(
            ORDEN_RECIENTE,
            ORDEN_ANTIGUO,
            ORDEN_CPU_DESC,
            ORDEN_CPU_ASC,
            ORDEN_MEM_DESC,
            ORDEN_PRIORIDAD_DESC,
            ORDEN_PRIORIDAD_ASC,
            ORDEN_NOMBRE_ASC));
    if (cboProcesoOrden != null) {
      cboProcesoOrden.setItems(procesoOrdenOpciones);
      cboProcesoOrden.getSelectionModel().select(ORDEN_RECIENTE);
    }
    if (btnProcesoBuscar != null) {
      btnProcesoBuscar
          .disableProperty()
          .bind(tblCatalogos.getSelectionModel().selectedItemProperty().isNull());
    }
  }

  private TableCell<ProcessRecord, BigDecimal> createNumericCell(int scale, String suffix) {
    return new TableCell<>() {
      @Override
      protected void updateItem(BigDecimal item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setText("-");
        } else {
          setText(item.setScale(scale, RoundingMode.HALF_UP).toPlainString() + suffix);
        }
      }
    };
  }

  private static int compareBigDecimal(BigDecimal a, BigDecimal b) {
    if (a == null && b == null) {
      return 0;
    }
    if (a == null) {
      return -1;
    }
    if (b == null) {
      return 1;
    }
    return a.compareTo(b);
  }

  private String nullToDash(String value) {
    return value == null || value.isBlank() ? "-" : value;
  }

  @Override
  public void setApplicationContext(ApplicationContext context) {
    this.context = context;
    this.catalogService = context.getCatalogService();
    this.jsonCatalogService = context.getJsonCatalogService();
    loadCatalogos();
  }

  @FXML
  private void onCapturarCatalogo() {
    if (catalogService == null) {
      return;
    }
    setCatalogCaptureLoading(true);
    String nombre = txtNombre.getText();
    String descripcion = txtDescripcion.getText();
    int n = spnN.getValue();
    SelectionCriterion criterio = cboCriterio.getValue();
    runAsync(
        () -> catalogService.createCatalog(nombre, descripcion, n, criterio),
        catalogo -> {
          clearCaptureForm();
          showStatus("Catálogo creado: " + catalogo.getNombre());
          loadCatalogos();
        },
        () -> setCatalogCaptureLoading(false));
  }

  @FXML
  private void onBuscarCatalogos() {
    loadCatalogos();
  }

  @FXML
  private void onBuscarProcesos() {
    if (selectedCatalog == null) {
      showWarning("Seleccione un catálogo para listar procesos.");
      return;
    }
    loadProcesos(selectedCatalog);
  }

  @FXML
  private void onExportarCatalogo() {
    if (selectedCatalog == null) {
      showWarning("Seleccione un catálogo para exportar.");
      return;
    }
    runAsync(
        () -> jsonCatalogService.exportCatalog(selectedCatalog.id()),
        path -> {
          showStatus("Catálogo exportado a " + path);
          showInfo("Exportación completada", "Archivo guardado en:\n" + path);
        });
  }

  @FXML
  private void onEliminarCatalogo() {
    if (selectedCatalog == null) {
      showWarning("Seleccione un catálogo para eliminar.");
      return;
    }
    Alert confirm =
        new Alert(
            Alert.AlertType.CONFIRMATION,
            "¿Eliminar catálogo seleccionado?",
            ButtonType.OK,
            ButtonType.CANCEL);
    confirm.setHeaderText("Eliminar catálogo");
    if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
      return;
    }
    runAsync(
        () -> {
          catalogService.deleteCatalog(selectedCatalog.id());
          return null;
        },
        unused -> {
          showStatus("Catálogo eliminado");
          selectedCatalog = null;
          procesos.clear();
          clearCatalogDetails();
          loadCatalogos();
        });
  }

  @FXML
  private void onImportarCatalogo() {
    if (context == null) {
      return;
    }
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Importar catálogo JSON");
    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
    chooser.setInitialDirectory(context.getDirectoryManager().getImportDir().toFile());
    Window window = txtNombre.getScene().getWindow();
    var file = chooser.showOpenDialog(window);
    if (file == null) {
      return;
    }
    runAsync(
        () -> jsonCatalogService.importCatalog(file.toPath()),
        catalog -> {
          showStatus("Catálogo importado: " + catalog.getNombre());
          loadCatalogos();
        });
  }

  @FXML
  private void onActualizarProceso() {
    if (selectedCatalog == null || selectedProcess == null) {
      showWarning("Seleccione un proceso para editar.");
      return;
    }
    String descripcion = txtProcesoDescripcion.getText();
    int prioridad = spnProcesoPrioridad.getValue();
    boolean expulsivo = chkProcesoExpulsivo.isSelected();
    ProcessUpdate update =
        new ProcessUpdate(
            Optional.ofNullable(descripcion), Optional.of(prioridad), Optional.of(expulsivo));
    runAsync(
        () -> {
          catalogService.updateProcess(selectedCatalog.id(), selectedProcess.getId(), update);
          return null;
        },
        unused -> {
          showStatus("Proceso actualizado");
          loadProcesos(selectedCatalog);
        });
  }

  @FXML
  private void onEliminarProceso() {
    if (selectedCatalog == null || selectedProcess == null) {
      showWarning("Seleccione un proceso para eliminar.");
      return;
    }
    Alert confirm =
        new Alert(
            Alert.AlertType.CONFIRMATION,
            "¿Eliminar proceso seleccionado?",
            ButtonType.OK,
            ButtonType.CANCEL);
    confirm.setHeaderText("Eliminar proceso");
    if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
      return;
    }
    runAsync(
        () -> {
          catalogService.deleteProcess(selectedCatalog.id(), selectedProcess.getId());
          return null;
        },
        unused -> {
          showStatus("Proceso eliminado");
          loadProcesos(selectedCatalog);
        });
  }

  private void loadCatalogos() {
    if (catalogService == null) {
      return;
    }
    String searchText = txtBuscar.getText();
    Optional<String> search =
        searchText == null || searchText.isBlank() ? Optional.empty() : Optional.of(searchText);
    Optional<SelectionCriterion> origin =
        switch (cboFiltroCriterio.getValue()) {
          case "CPU" -> Optional.of(SelectionCriterion.CPU);
          case "MEM" -> Optional.of(SelectionCriterion.MEMORY);
          default -> Optional.empty();
        };
    runAsync(
        () -> catalogService.listCatalogs(search, origin, CatalogSort.FECHA_CREACION_DESC, 1, 100),
        result -> {
          catalogos.setAll(result.content());
          if (!catalogos.isEmpty()) {
            tblCatalogos.getSelectionModel().selectFirst();
          } else {
            clearCatalogDetails();
            procesos.clear();
            selectedCatalog = null;
          }
        });
  }

  private void loadProcesos(CatalogMetadata catalogo) {
    runAsync(
        () ->
            catalogService.listProcesses(
                catalogo.id(), buildProcessFilter(), resolveProcessSort(), 1, 200),
        result -> {
          procesos.setAll(result.content());
          if (!procesos.isEmpty()) {
            tblProcesos.getSelectionModel().selectFirst();
          } else {
            clearProcessForm();
          }
        });
  }

  private ProcessFilter buildProcessFilter() {
    Optional<String> usuario = optionalText(txtProcesoUsuario);
    Optional<String> nombre = optionalText(txtProcesoBuscar);
    Optional<Boolean> expulsivo = resolveExpulsivoFilter();
    return new ProcessFilter(usuario, expulsivo, nombre, Optional.empty());
  }

  private Optional<String> optionalText(TextField field) {
    if (field == null) {
      return Optional.empty();
    }
    String value = field.getText();
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(value.trim());
  }

  private Optional<Boolean> resolveExpulsivoFilter() {
    if (cboProcesoExpulsivo == null) {
      return Optional.empty();
    }
    String value = cboProcesoExpulsivo.getValue();
    if (value == null) {
      return Optional.empty();
    }
    return switch (value) {
      case "Expulsivos" -> Optional.of(true);
      case "No expulsivos" -> Optional.of(false);
      default -> Optional.empty();
    };
  }

  private ProcessSort resolveProcessSort() {
    if (cboProcesoOrden == null) {
      return ProcessSort.CREATED_DESC;
    }
    String selection = cboProcesoOrden.getValue();
    if (selection == null) {
      return ProcessSort.CREATED_DESC;
    }
    return switch (selection) {
      case ORDEN_ANTIGUO -> ProcessSort.CREATED_ASC;
      case ORDEN_CPU_DESC -> ProcessSort.CPU_DESC;
      case ORDEN_CPU_ASC -> ProcessSort.CPU_ASC;
      case ORDEN_MEM_DESC -> ProcessSort.MEM_DESC;
      case ORDEN_PRIORIDAD_DESC -> ProcessSort.PRIORIDAD_DESC;
      case ORDEN_PRIORIDAD_ASC -> ProcessSort.PRIORIDAD_ASC;
      case ORDEN_NOMBRE_ASC -> ProcessSort.NOMBRE_ASC;
      default -> ProcessSort.CREATED_DESC;
    };
  }

  private void resetProcessFilters() {
    if (txtProcesoBuscar != null) {
      txtProcesoBuscar.clear();
    }
    if (txtProcesoUsuario != null) {
      txtProcesoUsuario.clear();
    }
    if (cboProcesoExpulsivo != null) {
      cboProcesoExpulsivo.getSelectionModel().selectFirst();
    }
    if (cboProcesoOrden != null) {
      cboProcesoOrden.getSelectionModel().select(ORDEN_RECIENTE);
    }
  }

  private void onCatalogSelected(CatalogMetadata catalogo) {
    if (catalogo == null) {
      clearCatalogDetails();
      procesos.clear();
      selectedCatalog = null;
      return;
    }
    boolean changed = this.selectedCatalog == null || this.selectedCatalog.id() != catalogo.id();
    this.selectedCatalog = catalogo;
    if (changed) {
      resetProcessFilters();
    }
    lblCatalogoNombre.setText(catalogo.nombre());
    lblCatalogoDescripcion.setText(catalogo.descripcion());
    lblCatalogoOrigen.setText(catalogo.origen().name());
    lblCatalogoN.setText(String.valueOf(catalogo.n()));
    lblCatalogoFecha.setText(formatInstant(catalogo.fechaCreacion()));
    loadProcesos(catalogo);
  }

  private void onProcessSelected(ProcessRecord processRecord) {
    this.selectedProcess = processRecord;
    if (processRecord == null) {
      clearProcessForm();
      return;
    }
    txtProcesoDescripcion.setText(processRecord.getDescripcion());
    spnProcesoPrioridad.getValueFactory().setValue(processRecord.getPrioridad());
    chkProcesoExpulsivo.setSelected(processRecord.isExpulsivo());
  }

  private void clearCaptureForm() {
    txtNombre.clear();
    txtDescripcion.clear();
    spnN.getValueFactory().setValue(10);
    cboCriterio.getSelectionModel().select(SelectionCriterion.CPU);
  }

  private void clearCatalogDetails() {
    lblCatalogoNombre.setText("-");
    lblCatalogoDescripcion.setText("-");
    lblCatalogoOrigen.setText("-");
    lblCatalogoN.setText("-");
    lblCatalogoFecha.setText("-");
  }

  private void clearProcessForm() {
    txtProcesoDescripcion.clear();
    spnProcesoPrioridad.getValueFactory().setValue(0);
    chkProcesoExpulsivo.setSelected(true);
  }

  private String formatInstant(Instant instant) {
    if (instant == null) {
      return "-";
    }
    return DATE_FORMAT.format(instant.atZone(ZoneId.systemDefault()).toLocalDateTime());
  }

  // Centraliza la ejecucion asincrona garantizando que las respuestas regresen en el hilo de UI.
  private <T> void runAsync(Supplier<T> supplier, Consumer<T> onSuccess) {
    runAsync(supplier, onSuccess, () -> {});
  }

  private <T> void runAsync(Supplier<T> supplier, Consumer<T> onSuccess, Runnable onComplete) {
    CompletableFuture.supplyAsync(supplier)
        .whenComplete(
            (result, throwable) -> {
              Runnable completion = onComplete != null ? onComplete : () -> {};
              Platform.runLater(
                  () -> {
                    if (throwable != null) {
                      completion.run();
                      showError(throwable);
                    } else {
                      try {
                        onSuccess.accept(result);
                      } finally {
                        completion.run();
                      }
                    }
                  });
            });
  }

  private void showStatus(String message) {
    lblStatus.setText(message);
    LOGGER.info(message);
  }

  private void setCatalogCaptureLoading(boolean loading) {
    if (piCapturaCatalogo != null) {
      piCapturaCatalogo.setVisible(loading);
      piCapturaCatalogo.setManaged(loading);
    }
    if (btnCapturarCatalogo != null) {
      btnCapturarCatalogo.setDisable(loading);
    }
  }

  private void showWarning(String message) {
    Alert alert = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
    alert.setHeaderText(null);
    alert.showAndWait();
  }

  private void showInfo(String title, String message) {
    Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
    alert.setHeaderText(title);
    alert.showAndWait();
  }

  private void showError(Throwable throwable) {
    Throwable cause = unwrap(throwable);
    String message = cause.getMessage();
    if (cause instanceof ValidationException validation && validation.getErrors() != null) {
      message = String.join("\n", validation.getErrors());
    }
    LOGGER.error("Error en UI", cause);
    Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
    alert.setHeaderText("Ocurrió un error");
    alert.showAndWait();
    showStatus("Error: " + message);
  }

  private Throwable unwrap(Throwable throwable) {
    if (throwable instanceof java.util.concurrent.CompletionException completion
        && completion.getCause() != null) {
      return completion.getCause();
    }
    return throwable;
  }
}
