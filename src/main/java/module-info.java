module io.github.coretension.cardmaker {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.swing;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.bootstrapfx.core;
    requires java.desktop;

    requires com.opencsv;
    requires com.fasterxml.jackson.databind;
    requires com.github.librepdf.openpdf;
    requires org.apache.poi.ooxml;

    opens io.github.coretension.cardmaker.ui to javafx.fxml;
    opens io.github.coretension.cardmaker.model to com.fasterxml.jackson.databind;
    opens io.github.coretension.cardmaker.config to com.fasterxml.jackson.databind;

    exports io.github.coretension.cardmaker.app;
    exports io.github.coretension.cardmaker.model;
}
