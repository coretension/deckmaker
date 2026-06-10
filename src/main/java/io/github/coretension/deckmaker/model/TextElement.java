package io.github.coretension.deckmaker.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Represents a text element on a card.
 * Text elements can have custom font configurations, colors, and outlines.
 */
public class TextElement extends CardElement {
    /** The text content to display. */
    private final StringProperty text = new SimpleStringProperty("Text");
    /** The size of the text font. */
    private final DoubleProperty fontSize = new SimpleDoubleProperty(14);
    /** The color of the text in hex format. */
    private final StringProperty color = new SimpleStringProperty("#000000");
    /** The rotation angle of the text. */
    private final DoubleProperty angle = new SimpleDoubleProperty(0);
    /** The width of the text outline. */
    private final DoubleProperty outlineWidth = new SimpleDoubleProperty(0);
    /** The color of the text outline in hex format. */
    private final StringProperty outlineColor = new SimpleStringProperty("#000000");
    /** The name of the font configuration to use from the library. */
    private final StringProperty fontConfigName = new SimpleStringProperty("Default");

    /**
     * Default constructor. Sets the name to "Text".
     */
    public TextElement() {
        this("Text");
    }

    /**
     * Constructor with a custom name.
     * @param name the name of the text element
     */
    public TextElement(String name) {
        super(name);
    }

    /** @return the text content */
    public String getText() { return text.get(); }
    /** @param value the text content to set */
    public void setText(String value) { text.set(value); }
    /** @return the text property */
    @JsonIgnore
    public StringProperty textProperty() { return text; }

    /** @return the font size */
    public double getFontSize() { return fontSize.get(); }
    /** @param value the font size to set */
    public void setFontSize(double value) { fontSize.set(value); }
    /** @return the font size property */
    @JsonIgnore
    public DoubleProperty fontSizeProperty() { return fontSize; }

    /** @return the text color */
    public String getColor() { return color.get(); }
    /** @param value the text color to set */
    public void setColor(String value) { color.set(value); }
    /** @return the color property */
    @JsonIgnore
    public StringProperty colorProperty() { return color; }

    /** @return the rotation angle */
    public double getAngle() { return angle.get(); }
    /** @param value the rotation angle to set */
    public void setAngle(double value) { angle.set(value); }
    /** @return the angle property */
    @JsonIgnore
    public DoubleProperty angleProperty() { return angle; }

    /** @return the outline width */
    public double getOutlineWidth() { return outlineWidth.get(); }
    /** @param value the outline width to set */
    public void setOutlineWidth(double value) { outlineWidth.set(value); }
    /** @return the outline width property */
    @JsonIgnore
    public DoubleProperty outlineWidthProperty() { return outlineWidth; }

    /** @return the outline color */
    public String getOutlineColor() { return outlineColor.get(); }
    /** @param value the outline color to set */
    public void setOutlineColor(String value) { outlineColor.set(value); }
    /** @return the outline color property */
    @JsonIgnore
    public StringProperty outlineColorProperty() { return outlineColor; }


    /** @return the font configuration name */
    public String getFontConfigName() { return fontConfigName.get(); }
    /** @param value the font configuration name to set */
    public void setFontConfigName(String value) { fontConfigName.set(value); }
    /** @return the font configuration name property */
    @JsonIgnore
    public StringProperty fontConfigNameProperty() { return fontConfigName; }
}
