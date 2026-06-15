package io.github.coretension.deckmaker;

import io.github.coretension.deckmaker.model.ContainerElement;
import io.github.coretension.deckmaker.model.TextElement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CardElementEnabledTest {

    @Test
    public void testEnabledProperty() {
        TextElement text = new TextElement();
        assertTrue(text.isEnabled(), "Elements should be enabled by default");
        
        text.setEnabled(false);
        assertFalse(text.isEnabled());
        
        text.setEnabled(true);
        assertTrue(text.isEnabled());
    }

    @Test
    public void testParentEnabledProperty() {
        ContainerElement container = new ContainerElement();
        assertTrue(container.isEnabled());
        
        container.setEnabled(false);
        assertFalse(container.isEnabled());
    }
}

