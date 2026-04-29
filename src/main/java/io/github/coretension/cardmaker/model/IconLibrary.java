package io.github.coretension.cardmaker.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;

import java.util.Map;

/**
 * A library that manages icon mappings.
 * Mappings are organized by a name (e.g., "Default"), and within each mapping,
 * a string key is mapped to an image path.
 */
public class IconLibrary {
    /** Map of mapping names to their respective key-to-path icon maps. */
    private final ObservableMap<String, Map<String, String>> mappings = FXCollections.observableHashMap();

    /** @return the icon mappings */
    public Map<String, Map<String, String>> getMappings() {
        return mappings;
    }

    /**
     * Replaces the current mappings with the provided ones.
     * @param newMappings the new mappings to set
     */
    public void setMappings(Map<String, Map<String, String>> newMappings) {
        mappings.clear();
        if (newMappings != null) {
            mappings.putAll(newMappings);
        }
    }

    /** @return the observable mappings property */
    @JsonIgnore
    public ObservableMap<String, Map<String, String>> mappingsProperty() {
        return mappings;
    }
}
