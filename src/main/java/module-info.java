module com.pm {
  requires javafx.controls;
  requires javafx.fxml;

  opens com.pm to
      javafx.fxml;

  exports com.pm;
}
