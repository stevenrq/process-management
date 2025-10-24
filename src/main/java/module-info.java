module com.pm {
  requires javafx.controls;
  requires javafx.fxml;
  requires io.javalin;
  requires com.fasterxml.jackson.databind;
  requires com.fasterxml.jackson.datatype.jsr310;
  requires com.zaxxer.hikari;
  requires java.sql;
  requires org.slf4j;

  opens com.pm to
      javafx.fxml;
  opens com.pm.ui to
      javafx.fxml;
  opens com.pm.config to
      com.fasterxml.jackson.databind;
  opens com.pm.domain to
      com.fasterxml.jackson.databind;
  opens com.pm.domain.catalog to
      com.fasterxml.jackson.databind;
  opens com.pm.domain.process to
      com.fasterxml.jackson.databind;
  opens com.pm.rest.dto to
      com.fasterxml.jackson.databind;
  opens com.pm.service.capture to
      com.fasterxml.jackson.databind;

  exports com.pm;
  exports com.pm.context;
  exports com.pm.config;
  exports com.pm.domain;
  exports com.pm.domain.catalog;
  exports com.pm.domain.process;
  exports com.pm.service;
  exports com.pm.rest;
  exports com.pm.ui;
}
