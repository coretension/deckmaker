package io.github.coretension.cardmaker.app;

import javafx.application.Application;

import javax.imageio.ImageIO;

/**
 * Entry point for the application.
 * This class is used to launch the JavaFX application.
 */
public class Launcher {
    /**
     * Main entry point.
     * @param args command line arguments
     */
    public static void main(String[] args) {
        // Trigger ImageIO registration to catch NoClassDefFoundError early if dependencies are missing
        ImageIO.getReaderFormatNames();
        Application.launch(CardMakerApplication.class, args);
    }
}
