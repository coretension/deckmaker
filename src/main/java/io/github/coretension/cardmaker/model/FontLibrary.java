package io.github.coretension.cardmaker.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;

import java.util.Map;

/**
 * A library that manages a collection of {@link FontElement} objects, indexed by name.
 */
public class FontLibrary {
    /** A map of font names to their respective font definitions. */
    private final ObservableMap<String, FontElement> fonts = FXCollections.observableHashMap();

    /**
     * @return the map of fonts
     */
    public Map<String, FontElement> getFonts() {
        return fonts;
    }

    /**
     * Replaces the current fonts with the provided map of fonts.
     * @param newFonts the new map of fonts to set
     */
    public void setFonts(Map<String, FontElement> newFonts) {
        fonts.clear();
        if (newFonts != null) {
            fonts.putAll(newFonts);
        }
    }

    /**
     * @return the observable map of fonts
     */
    @JsonIgnore
    public ObservableMap<String, FontElement> fontsProperty() {
        return fonts;
    }
}
