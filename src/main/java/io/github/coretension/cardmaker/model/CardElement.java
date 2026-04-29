package io.github.coretension.cardmaker.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import javafx.beans.property.*;

/**
 * Represents a base element that can be placed on a card.
 * This class provides common properties like position (x, y), name, and enabled state.
 * It is serialized/deserialized using Jackson with type information.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = TextElement.class, name = "text"),
    @JsonSubTypes.Type(value = ImageElement.class, name = "image"),
    @JsonSubTypes.Type(value = FontElement.class, name = "font"),
    @JsonSubTypes.Type(value = ContainerElement.class, name = "container"),
    @JsonSubTypes.Type(value = ConditionElement.class, name = "condition"),
    @JsonSubTypes.Type(value = ParentCardElement.class, name = "parent"),
    @JsonSubTypes.Type(value = IconElement.class, name = "icon")
})
public abstract class CardElement {
    /** X coordinate of the element on the card. */
    protected final DoubleProperty x = new SimpleDoubleProperty(0);
    /** Y coordinate of the element on the card. */
    protected final DoubleProperty y = new SimpleDoubleProperty(0);
    /** Name identifier for the element. */
    protected final StringProperty name = new SimpleStringProperty("Element");
    /** Indicates if the element is active and should be processed. */
    protected final BooleanProperty enabled = new SimpleBooleanProperty(true);

    /**
     * Default constructor.
     */
    public CardElement() {}

    /**
     * Constructor with a name for the element.
     * @param name the name of the element
     */
    public CardElement(String name) {
        setName(name);
    }

    /**
     * @return the x coordinate value
     */
    public double getX() { return x.get(); }

    /**
     * @param value the x coordinate value to set
     */
    public void setX(double value) { x.set(value); }

    /**
     * @return the x coordinate property
     */
    @JsonIgnore
    public DoubleProperty xProperty() { return x; }

    /**
     * @return the y coordinate value
     */
    public double getY() { return y.get(); }

    /**
     * @param value the y coordinate value to set
     */
    public void setY(double value) { y.set(value); }

    /**
     * @return the y coordinate property
     */
    @JsonIgnore
    public DoubleProperty yProperty() { return y; }

    /**
     * @return the name of the element
     */
    public String getName() { return name.get(); }

    /**
     * @param value the name to set for the element
     */
    public void setName(String value) { name.set(value); }

    /**
     * @return the name property
     */
    @JsonIgnore
    public StringProperty nameProperty() { return name; }

    /**
     * Checks if the element is enabled and should be rendered.
     * @return true if enabled, false otherwise
     */
    public boolean isEnabled() { return enabled.get(); }
    
    /**
     * Sets whether the element is enabled.
     * @param value true to enable, false to disable
     */
    public void setEnabled(boolean value) { enabled.set(value); }
    
    /**
     * @return the enabled property
     */
    @JsonIgnore
    public BooleanProperty enabledProperty() { return enabled; }
}
