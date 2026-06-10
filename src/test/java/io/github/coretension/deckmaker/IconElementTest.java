package io.github.coretension.deckmaker;

import io.github.coretension.deckmaker.config.*;
import io.github.coretension.deckmaker.model.*;
import io.github.coretension.deckmaker.persistence.*;
import io.github.coretension.deckmaker.service.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IconElementTest {

    @Test
    public void testDefaultValues() {
        IconElement ice = new IconElement();
        assertEquals(32.0, ice.getIconWidth());
        assertEquals(32.0, ice.getIconHeight());
        assertEquals("Default", ice.getMappingName());
        assertEquals("", ice.getValue());
    }

    @Test
    public void testProperties() {
        IconElement ice = new IconElement();
        ice.valueProperty().set("ABC");
        assertEquals("ABC", ice.getValue());
        
        ice.setIconWidth(48);
        assertEquals(48.0, ice.getIconWidth());
        
        ice.setIconHeight(48);
        assertEquals(48.0, ice.getIconHeight());
        
        ice.setMappingName("Custom");
        assertEquals("Custom", ice.getMappingName());
    }
}

