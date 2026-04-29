package io.github.coretension.cardmaker;

import io.github.coretension.cardmaker.config.*;
import io.github.coretension.cardmaker.model.*;
import io.github.coretension.cardmaker.persistence.*;
import io.github.coretension.cardmaker.service.*;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

public class ImageElementOverflowTest {

    @Test
    public void testImageElementOverflowPersistence() throws IOException {
        CardTemplate template = new CardTemplate();
        
        ImageElement image = new ImageElement();
        image.setName("Test Image");
        image.setImagePath("test.png");
        image.setAllowOverflow(true);
        template.getElements().add(image);
        
        File tempFile = Files.createTempFile("deck_image", ".json").toFile();
        try {
            DeckStorage.save(template, tempFile);
            
            CardTemplate loaded = DeckStorage.load(tempFile);
            assertEquals(1, loaded.getElements().size());
            
            CardElement el = loaded.getElements().getFirst();
            assertInstanceOf(ImageElement.class, el);
            ImageElement loadedImage = (ImageElement) el;
            assertEquals("test.png", loadedImage.getImagePath());
            assertTrue(loadedImage.isAllowOverflow());
            
            // Toggle and save again
            loadedImage.setAllowOverflow(false);
            DeckStorage.save(loaded, tempFile);
            
            CardTemplate loaded2 = DeckStorage.load(tempFile);
            assertFalse(((ImageElement)loaded2.getElements().getFirst()).isAllowOverflow());
            
        } finally {
            tempFile.delete();
        }
    }
}

