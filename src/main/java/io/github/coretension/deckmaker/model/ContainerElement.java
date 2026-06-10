package io.github.coretension.deckmaker.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javafx.beans.property.*;

/**
 * A parent element that provides layout capabilities for its children.
 * Supports various layout types and alignments.
 */
public class   ContainerElement extends ParentCardElement {
    /** Defines how child elements are positioned within the container. */
    public enum LayoutType { POSITIONAL, FLOW, VERTICAL, HORIZONTAL, STACK }
    /** Defines the horizontal alignment of children within the container. */
    public enum Alignment { LEFT, CENTER, RIGHT }
    /** Defines the vertical alignment of children within the container. */
    public enum VerticalAlignment { TOP, MIDDLE, BOTTOM }

    /** Width of the container. */
    private final DoubleProperty width = new SimpleDoubleProperty(100);
    /** Height of the container. */
    private final DoubleProperty height = new SimpleDoubleProperty(100);
    /** Whether to maintain the aspect ratio when resizing. */
    private final BooleanProperty lockAspectRatio = new SimpleBooleanProperty(false);
    /** Transparency level (0.0 to 1.0). */
    private final DoubleProperty alpha = new SimpleDoubleProperty(1.0);
    /** Background color in hex format (e.g., #RRGGBBAA). */
    private final StringProperty backgroundColor = new SimpleStringProperty("#FFFFFF00"); // Transparent by default
    /** Current layout strategy. */
    private final ObjectProperty<LayoutType> layoutType = new SimpleObjectProperty<>(LayoutType.POSITIONAL);
    /** Current alignment strategy. */
    private final ObjectProperty<Alignment> alignment = new SimpleObjectProperty<>(Alignment.LEFT);
    /** Current vertical alignment strategy. */
    private final ObjectProperty<VerticalAlignment> verticalAlignment = new SimpleObjectProperty<>(VerticalAlignment.TOP);
    /** Spacing between elements in flow, vertical, or horizontal layouts. */
    private final DoubleProperty spacing = new SimpleDoubleProperty(0);
    /** Whether the container's properties are locked for editing. */
    private final BooleanProperty locked = new SimpleBooleanProperty(false);

    /**
     * Default constructor. Sets the name to "Container".
     */
    public ContainerElement() {
        this("Container");
    }

    /**
     * Constructor with a custom name.
     * @param name the name of the container
     */
    public ContainerElement(String name) {
        super(name);
    }

    /** @return the width of the container */
    public double getWidth() { return width.get(); }
    /** @param value the width to set */
    public void setWidth(double value) { width.set(value); }
    /** @return the width property */
    @JsonIgnore
    public DoubleProperty widthProperty() { return width; }

    /** @return the height of the container */
    public double getHeight() { return height.get(); }
    /** @param value the height to set */
    public void setHeight(double value) { height.set(value); }
    /** @return the height property */
    @JsonIgnore
    public DoubleProperty heightProperty() { return height; }

    /** @return the alpha (transparency) value */
    public double getAlpha() { return alpha.get(); }
    /** @param value the alpha value to set */
    public void setAlpha(double value) { alpha.set(value); }
    /** @return the alpha property */
    @JsonIgnore
    public DoubleProperty alphaProperty() { return alpha; }

    /** @return the background color string */
    public String getBackgroundColor() { return backgroundColor.get(); }
    /** @param value the background color string to set */
    public void setBackgroundColor(String value) { backgroundColor.set(value); }
    /** @return the background color property */
    @JsonIgnore
    public StringProperty backgroundColorProperty() { return backgroundColor; }

    /** @return the layout type */
    public LayoutType getLayoutType() { return layoutType.get(); }
    /** @param value the layout type to set */
    public void setLayoutType(LayoutType value) { layoutType.set(value); }
    /** @return the layout type property */
    @JsonIgnore
    public ObjectProperty<LayoutType> layoutTypeProperty() { return layoutType; }

    /** @return the alignment */
    public Alignment getAlignment() { return alignment.get(); }
    /** @param value the alignment to set */
    public void setAlignment(Alignment value) { alignment.set(value); }
    /** @return the alignment property */
    @JsonIgnore
    public ObjectProperty<Alignment> alignmentProperty() { return alignment; }

    /** @return the vertical alignment */
    public VerticalAlignment getVerticalAlignment() { return verticalAlignment.get(); }
    /** @param value the vertical alignment to set */
    public void setVerticalAlignment(VerticalAlignment value) { verticalAlignment.set(value); }
    /** @return the vertical alignment property */
    @JsonIgnore
    public ObjectProperty<VerticalAlignment> verticalAlignmentProperty() { return verticalAlignment; }

    /** @return the spacing between children */
    public double getSpacing() { return spacing.get(); }
    /** @param value the spacing to set */
    public void setSpacing(double value) { spacing.set(value); }
    /** @return the spacing property */
    @JsonIgnore
    public DoubleProperty spacingProperty() { return spacing; }

    /** @return true if locked, false otherwise */
    public boolean isLocked() { return locked.get(); }
    /** @param value true to lock, false to unlock */
    public void setLocked(boolean value) { locked.set(value); }
    /** @return the locked property */
    @JsonIgnore
    public BooleanProperty lockedProperty() { return locked; }

    /** @return true if aspect ratio is locked */
    public boolean isLockAspectRatio() { return lockAspectRatio.get(); }
    /** @param value true to lock aspect ratio */
    public void setLockAspectRatio(boolean value) { lockAspectRatio.set(value); }
    /** @return the lock aspect ratio property */
    @JsonIgnore
    public BooleanProperty lockAspectRatioProperty() { return lockAspectRatio; }

    @Override
    public String toString() {
        return getName();
    }
}
