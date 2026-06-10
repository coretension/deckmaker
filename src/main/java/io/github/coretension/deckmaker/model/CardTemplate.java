package io.github.coretension.deckmaker.model;

import com.fasterxml.jackson.annotation.JsonSetter;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Collection;

/**
 * Represents a template for a card, including its dimensions, elements, and resources.
 */
public class CardTemplate {
    /** The physical dimensions of the card. */
    private CardDimension dimension = CardDimension.POKER;
    /** The bleed margin in millimeters. */
    private double bleedMm = 3.0;
    /** The list of elements that make up the card's design. */
    private final ObservableList<CardElement> elements = FXCollections.observableArrayList();
    /** Path to the CSV data file for bulk card generation. */
    private String csvPath;
    /** Library of icons used in this template. */
    private IconLibrary iconLibrary = new IconLibrary();
    /** Library of fonts used in this template. */
    private FontLibrary fontLibrary = new FontLibrary();

    /** @return the card dimension */
    public CardDimension getDimension() { return dimension; }
    /** @param dimension the card dimension to set */
    public void setDimension(CardDimension dimension) { this.dimension = dimension; }

    /** @return the bleed margin in millimeters */
    public double getBleedMm() { return bleedMm; }
    /** @param bleedMm the bleed margin in millimeters to set */
    public void setBleedMm(double bleedMm) { this.bleedMm = bleedMm; }

    /** @return the observable list of card elements */
    public ObservableList<CardElement> getElements() { return elements; }

    @JsonSetter("elements")
    public void setElements(Collection<CardElement> elements) {
        this.elements.setAll(elements == null ? FXCollections.observableArrayList() : elements);
    }

    /** @return the path to the CSV data file */
    public String getCsvPath() { return csvPath; }
    /** @param csvPath the path to the CSV data file to set */
    public void setCsvPath(String csvPath) { this.csvPath = csvPath; }

    /** @return the icon library */
    public IconLibrary getIconLibrary() { return iconLibrary; }
    /** @param iconLibrary the icon library to set */
    public void setIconLibrary(IconLibrary iconLibrary) { this.iconLibrary = iconLibrary; }

    /** @return the font library */
    public FontLibrary getFontLibrary() { return fontLibrary; }
    /** @param fontLibrary the font library to set */
    public void setFontLibrary(FontLibrary fontLibrary) { this.fontLibrary = fontLibrary; }
}
