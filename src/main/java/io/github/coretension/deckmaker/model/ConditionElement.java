package io.github.coretension.deckmaker.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * A parent element that conditionally renders its children based on a specified condition string.
 */
public class ConditionElement extends ParentCardElement {
    /** The condition string evaluated during data merging. */
    private final StringProperty condition = new SimpleStringProperty("");

    /**
     * Default constructor. Sets the element name to "Condition".
     */
    public ConditionElement() {
        this("Condition");
    }

    /**
     * Constructor with a custom name for the element.
     * @param name the name of the condition element
     */
    public ConditionElement(String name) {
        super(name);
    }

    /** @return the condition expression string */
    public String getCondition() { return condition.get(); }
    /** @param value the condition expression string to set */
    public void setCondition(String value) { condition.set(value); }
    /** @return the condition property */
    @JsonIgnore
    public StringProperty conditionProperty() { return condition; }
}
