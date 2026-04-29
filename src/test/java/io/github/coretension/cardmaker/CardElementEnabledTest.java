package io.github.coretension.cardmaker;

import io.github.coretension.cardmaker.config.*;
import io.github.coretension.cardmaker.model.*;
import io.github.coretension.cardmaker.persistence.*;
import io.github.coretension.cardmaker.service.*;
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

