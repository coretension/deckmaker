module io.github.coretension.deckmaker {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.swing;

    requires java.desktop;

    requires com.opencsv;
    requires com.fasterxml.jackson.databind;
    requires com.github.librepdf.openpdf;
    requires org.apache.poi.ooxml;

    opens io.github.coretension.deckmaker.ui to javafx.fxml;
    opens io.github.coretension.deckmaker.model to com.fasterxml.jackson.databind;
    opens io.github.coretension.deckmaker.config to com.fasterxml.jackson.databind;

    exports io.github.coretension.deckmaker.app;
    exports io.github.coretension.deckmaker.model;
}
