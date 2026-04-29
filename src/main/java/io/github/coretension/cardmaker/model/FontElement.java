package io.github.coretension.cardmaker.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javafx.beans.property.*;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;

/**
 * Defines a font style that can be applied to text elements.
 */
public class FontElement extends CardElement {
    /** The name of the font family (e.g., "Arial"). */
    private final StringProperty fontFamily = new SimpleStringProperty("Arial");
    /** The size of the font in points. */
    private final DoubleProperty fontSize = new SimpleDoubleProperty(14);
    /** The weight of the font (e.g., NORMAL, BOLD). */
    private final ObjectProperty<FontWeight> fontWeight = new SimpleObjectProperty<>(FontWeight.NORMAL);
    /** The posture of the font (e.g., REGULAR, ITALIC). */
    private final ObjectProperty<FontPosture> fontPosture = new SimpleObjectProperty<>(FontPosture.REGULAR);
    /** The color of the text in hex format. */
    private final StringProperty color = new SimpleStringProperty("#000000");
    /** The rotation angle of the text. */
    private final DoubleProperty angle = new SimpleDoubleProperty(0);
    /** The width of the text outline. */
    private final DoubleProperty outlineWidth = new SimpleDoubleProperty(0);
    /** The color of the text outline. */
    private final StringProperty outlineColor = new SimpleStringProperty("#000000");

    /**
     * Default constructor. Sets the name to "Font".
     */
    public FontElement() {
        this("Font");
    }

    /**
     * Constructor with a custom name.
     * @param name the name of the font element
     */
    public FontElement(String name) {
        super(name);
    }

    /** @return the font family name */
    public String getFontFamily() { return fontFamily.get(); }
    /** @param value the font family name to set */
    public void setFontFamily(String value) { fontFamily.set(value); }
    /** @return the font family property */
    @JsonIgnore
    public StringProperty fontFamilyProperty() { return fontFamily; }

    /** @return the font size */
    public double getFontSize() { return fontSize.get(); }
    /** @param value the font size to set */
    public void setFontSize(double value) { fontSize.set(value); }
    /** @return the font size property */
    @JsonIgnore
    public DoubleProperty fontSizeProperty() { return fontSize; }

    /** @return the font weight */
    public FontWeight getFontWeight() { return fontWeight.get(); }
    /** @param value the font weight to set */
    public void setFontWeight(FontWeight value) { fontWeight.set(value); }
    /** @return the font weight property */
    @JsonIgnore
    public ObjectProperty<FontWeight> fontWeightProperty() { return fontWeight; }

    /** @return the font posture */
    public FontPosture getFontPosture() { return fontPosture.get(); }
    /** @param value the font posture to set */
    public void setFontPosture(FontPosture value) { fontPosture.set(value); }
    /** @return the font posture property */
    @JsonIgnore
    public ObjectProperty<FontPosture> fontPostureProperty() { return fontPosture; }

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
}
