package io.github.coretension.deckmaker;

import io.github.coretension.deckmaker.model.ContainerElement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ContainerElementTest {

    @Test
    public void testDefaultValues() {
        ContainerElement ce = new ContainerElement();
        assertEquals(ContainerElement.LayoutType.POSITIONAL, ce.getLayoutType());
        assertEquals(ContainerElement.Alignment.LEFT, ce.getAlignment());
        assertEquals(ContainerElement.VerticalAlignment.TOP, ce.getVerticalAlignment());
        assertEquals(0.0, ce.getSpacing());
        assertEquals(100.0, ce.getWidth());
        assertEquals(100.0, ce.getHeight());
        assertFalse(ce.isLocked());
    }

    @Test
    public void testSetProperties() {
        ContainerElement ce = new ContainerElement();
        ce.setLayoutType(ContainerElement.LayoutType.VERTICAL);
        ce.setAlignment(ContainerElement.Alignment.CENTER);
        ce.setVerticalAlignment(ContainerElement.VerticalAlignment.BOTTOM);
        ce.setSpacing(10.0);
        ce.setLocked(true);
        
        assertEquals(ContainerElement.LayoutType.VERTICAL, ce.getLayoutType());
        assertEquals(ContainerElement.Alignment.CENTER, ce.getAlignment());
        assertEquals(ContainerElement.VerticalAlignment.BOTTOM, ce.getVerticalAlignment());
        assertEquals(10.0, ce.getSpacing());
        assertTrue(ce.isLocked());
    }

    @Test
    public void testProperties() {
        ContainerElement ce = new ContainerElement();
        ce.layoutTypeProperty().set(ContainerElement.LayoutType.HORIZONTAL);
        ce.alignmentProperty().set(ContainerElement.Alignment.RIGHT);

        assertEquals(ContainerElement.LayoutType.HORIZONTAL, ce.getLayoutType());
        assertEquals(ContainerElement.Alignment.RIGHT, ce.getAlignment());
    }
    @Test
    public void testStackLayout() {
        ContainerElement ce = new ContainerElement();
        ce.setLayoutType(ContainerElement.LayoutType.STACK);
        assertEquals(ContainerElement.LayoutType.STACK, ce.getLayoutType());
    }
}

