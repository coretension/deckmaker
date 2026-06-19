package io.github.coretension.deckmaker;

import io.github.coretension.deckmaker.config.AppSettings;
import io.github.coretension.deckmaker.persistence.DeckStorage;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeckStorageLegacyConfigTest {
    @Test
    void loadSettingsMigratesLegacyCardMakerSettings() throws IOException {
        String originalUserHome = System.getProperty("user.home");
        Path userHome = Files.createTempDirectory("deckmaker-user-home");
        try {
            System.setProperty("user.home", userHome.toString());

            AppSettings settings = new AppSettings();
            settings.setLastOpenedDeckPath("C:\\decks\\legacy.cm");
            File legacySettings = userHome.resolve(".cardmaker").resolve("settings.json").toFile();
            legacySettings.getParentFile().mkdirs();
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.writeValue(legacySettings, settings);

            AppSettings loaded = DeckStorage.loadSettings();

            assertEquals("C:\\decks\\legacy.cm", loaded.getLastOpenedDeckPath());
            assertTrue(userHome.resolve(".deckmaker").resolve("settings.json").toFile().exists());
        } finally {
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    void recoveryTempFileFallsBackToLegacyCardMakerAutosave() throws IOException {
        String originalUserHome = System.getProperty("user.home");
        Path userHome = Files.createTempDirectory("deckmaker-user-home");
        try {
            System.setProperty("user.home", userHome.toString());
            File legacyTemp = userHome.resolve(".cardmaker").resolve("temp_deck.json").toFile();
            legacyTemp.getParentFile().mkdirs();
            Files.writeString(legacyTemp.toPath(), "{}");

            assertEquals(legacyTemp, DeckStorage.getRecoveryTempFile());
        } finally {
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    void loadSettingsDefaultsBleedGuideAppearanceWhenFieldsAreMissing() throws IOException {
        String originalUserHome = System.getProperty("user.home");
        Path userHome = Files.createTempDirectory("deckmaker-user-home");
        try {
            System.setProperty("user.home", userHome.toString());
            File settingsFile = userHome.resolve(".deckmaker").resolve("settings.json").toFile();
            settingsFile.getParentFile().mkdirs();
            Files.writeString(settingsFile.toPath(), "{\"lastOpenedDeckPath\":\"C:\\\\decks\\\\existing.dm\"}");

            AppSettings loaded = DeckStorage.loadSettings();

            assertEquals("#FF0000", loaded.getBleedGuideColor());
            assertEquals(1.0, loaded.getBleedGuideAlpha());
        } finally {
            System.setProperty("user.home", originalUserHome);
        }
    }
}
