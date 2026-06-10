package io.github.coretension.deckmaker.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javafx.beans.property.*;

/**
 * Represents an image element that can be displayed on a card.
 */
public class ImageElement extends CardElement {
    /** Path to the image file. */
    private final StringProperty imagePath = new SimpleStringProperty("");
    /** Width of the image. */
    private final DoubleProperty width = new SimpleDoubleProperty(50);
    /** Height of the image. */
    private final DoubleProperty height = new SimpleDoubleProperty(50);
    /** Whether to maintain the image's aspect ratio when resizing. */
    private final BooleanProperty lockAspectRatio = new SimpleBooleanProperty(true);
    /** Whether the image is allowed to overflow its defined container. */
    private final BooleanProperty allowOverflow = new SimpleBooleanProperty(false);

    /**
     * Default constructor. Sets the name to "Image".
     */
    public ImageElement() {
        this("Image");
    }

    /**
     * Constructor with a custom name.
     * @param name the name of the image element
     */
    public ImageElement(String name) {
        super(name);
    }

    /** @return the image path string */
    public String getImagePath() { return imagePath.get(); }
    /** @param value the image path string to set */
    public void setImagePath(String value) { imagePath.set(value); }
    /** @return the image path property */
    @JsonIgnore
    public StringProperty imagePathProperty() { return imagePath; }

    /** @return the image width */
    public double getWidth() { return width.get(); }
    /** @param value the image width to set */
    public void setWidth(double value) { width.set(value); }
    /** @return the width property */
    @JsonIgnore
    public DoubleProperty widthProperty() { return width; }

    /** @return the image height */
    public double getHeight() { return height.get(); }
    /** @param value the image height to set */
    public void setHeight(double value) { height.set(value); }
    /** @return the height property */
    @JsonIgnore
    public DoubleProperty heightProperty() { return height; }

    /** @return true if aspect ratio is locked */
    public boolean isLockAspectRatio() { return lockAspectRatio.get(); }
    /** @param value true to lock aspect ratio */
    public void setLockAspectRatio(boolean value) { lockAspectRatio.set(value); }
    /** @return the lock aspect ratio property */
    @JsonIgnore
    public BooleanProperty lockAspectRatioProperty() { return lockAspectRatio; }

    /** @return true if overflow is allowed */
    public boolean isAllowOverflow() { return allowOverflow.get(); }
    /** @param value true to allow overflow */
    public void setAllowOverflow(boolean value) { allowOverflow.set(value); }
    /** @return the allow overflow property */
    @JsonIgnore
    public BooleanProperty allowOverflowProperty() { return allowOverflow; }
}
