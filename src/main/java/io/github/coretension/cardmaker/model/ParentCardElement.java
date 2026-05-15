package io.github.coretension.cardmaker.model;

import com.fasterxml.jackson.annotation.JsonSetter;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Collection;

/**
 * Abstract base class for card elements that can contain other card elements as children.
 */
public abstract class ParentCardElement extends CardElement {
    /** List of child elements contained within this element. */
    private final ObservableList<CardElement> children = FXCollections.observableArrayList();

    /**
     * Default constructor.
     */
    public ParentCardElement() {
    }

    /**
     * Constructor with a name for the element.
     * @param name the name of the parent element
     */
    public ParentCardElement(String name) {
        super(name);
    }

    /**
     * @return the observable list of child elements
     */
    public ObservableList<CardElement> getChildren() {
        return children;
    }

    @JsonSetter("children")
    public void setChildren(Collection<CardElement> children) {
        this.children.setAll(children == null ? FXCollections.observableArrayList() : children);
    }
}
