package io.github.coretension.deckmaker.ui;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class DeckMakerControllerFileExtensionTest {
    @Test
    void legacyCmDeckFilesSaveAsDmFiles() {
        File legacyFile = new File("C:\\decks\\sample.cm");

        assertTrue(DeckMakerController.isLegacyDeckFile(legacyFile));
        assertEquals(new File("C:\\decks\\sample.dm"), DeckMakerController.toDeckMakerFile(legacyFile));
    }

    @Test
    void dmDeckFilesRemainUnchanged() {
        File deckFile = new File("C:\\decks\\sample.dm");

        assertFalse(DeckMakerController.isLegacyDeckFile(deckFile));
        assertEquals(deckFile, DeckMakerController.toDeckMakerFile(deckFile));
    }

    @Test
    void saveTargetsWithoutDeckExtensionGetDmExtension() {
        assertEquals(new File("C:\\decks\\sample.dm"), DeckMakerController.toDeckMakerFile(new File("C:\\decks\\sample")));
    }
}
