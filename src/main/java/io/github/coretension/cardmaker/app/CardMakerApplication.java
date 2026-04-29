package io.github.coretension.cardmaker.app;

import io.github.coretension.cardmaker.model.CardDimension;
import io.github.coretension.cardmaker.ui.CardMakerController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Main JavaFX Application class for Card Maker.
 * Handles initial setup, DPI detection, and loading the main view.
 */
public class CardMakerApplication extends Application {
    /**
     * Starts the JavaFX application.
     * @param stage the primary stage for this application
     * @throws IOException if the FXML file cannot be loaded
     */
    @Override
    public void start(Stage stage) throws IOException {
        // Auto-detect screen DPI
        double screenDpi = Screen.getPrimary().getDpi();
        CardDimension.setDpi(screenDpi);

        FXMLLoader fxmlLoader = new FXMLLoader(CardMakerApplication.class.getResource(
                "/io/github/coretension/cardmaker/card-maker-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1000, 700);
        CardMakerController controller = fxmlLoader.getController();

        stage.setTitle("Card Maker");
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> {
            controller.saveSettings();
        });
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            controller.saveSettings();
        }));
        
        stage.show();
    }
}
