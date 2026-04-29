package io.github.coretension.cardmaker.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Represents an icon element that can be displayed on a card.
 * Icons are often mapped from a string value (e.g., in CSV data) to an image file.
 */
public class IconElement extends CardElement {
    /** The string value that represents the icon (e.g., "{fire}"). */
    private final StringProperty value = new SimpleStringProperty("");
    /** The width to render the icon. */
    private final DoubleProperty iconWidth = new SimpleDoubleProperty(32);
    /** The height to render the icon. */
    private final DoubleProperty iconHeight = new SimpleDoubleProperty(32);
    /** The name of the icon mapping to use. */
    private final StringProperty mappingName = new SimpleStringProperty("Default");

    /**
     * Default constructor. Sets the name to "Icons".
     */
    public IconElement() {
        this("Icons");
    }

    /**
     * Constructor with a custom name.
     * @param name the name of the icon element
     */
    public IconElement(String name) {
        super(name);
    }

    /** @return the icon value string */
    public String getValue() { return value.get(); }
    /** @param val the icon value string to set */
    public void setValue(String val) { value.set(val); }
    /** @return the value property */
    @JsonIgnore
    public StringProperty valueProperty() { return value; }

    /** @return the icon width */
    public double getIconWidth() { return iconWidth.get(); }
    /** @param val the icon width to set */
    public void setIconWidth(double val) { iconWidth.set(val); }
    /** @return the icon width property */
    @JsonIgnore
    public DoubleProperty iconWidthProperty() { return iconWidth; }

    /** @return the icon height */
    public double getIconHeight() { return iconHeight.get(); }
    /** @param val the icon height to set */
    public void setIconHeight(double val) { iconHeight.set(val); }
    /** @return the icon height property */
    @JsonIgnore
    public DoubleProperty iconHeightProperty() { return iconHeight; }

    /** @return the mapping name */
    public String getMappingName() { return mappingName.get(); }
    /** @param val the mapping name to set */
    public void setMappingName(String val) { mappingName.set(val); }
    /** @return the mapping name property */
    @JsonIgnore
    public StringProperty mappingNameProperty() { return mappingName; }
}
