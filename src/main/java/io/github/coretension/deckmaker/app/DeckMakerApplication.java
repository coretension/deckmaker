package io.github.coretension.deckmaker.app;

import io.github.coretension.deckmaker.config.AppSettings;
import io.github.coretension.deckmaker.model.CardDimension;
import io.github.coretension.deckmaker.persistence.DeckStorage;
import io.github.coretension.deckmaker.ui.DeckMakerController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

/**
 * Main JavaFX Application class for DeckMaker.
 * Handles initial setup, DPI detection, and loading the main view.
 */
public class DeckMakerApplication extends Application {
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

        AppSettings settings;
        try {
            settings = DeckStorage.loadSettings();
        } catch (IOException e) {
            settings = new AppSettings();
        }
        double width = settings.getWindowWidth() > 0 ? settings.getWindowWidth() : 1000;
        double height = settings.getWindowHeight() > 0 ? settings.getWindowHeight() : 700;

        FXMLLoader fxmlLoader = new FXMLLoader(DeckMakerApplication.class.getResource(
                "/io/github/coretension/deckmaker/deck-maker-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), width, height);
        DeckMakerController controller = fxmlLoader.getController();

        stage.setTitle("DeckMaker");
        Image appIcon = new Image(Objects.requireNonNull(DeckMakerApplication.class.getResourceAsStream(
            "/io/github/coretension/deckmaker/icons/app-icon.png")));
        stage.getIcons().add(appIcon);
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> controller.saveSettings());
        
        Runtime.getRuntime().addShutdownHook(new Thread(controller::saveSettings));
        
        stage.show();
    }
}
