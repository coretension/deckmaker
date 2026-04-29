package io.github.coretension.cardmaker;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class DeckStorage {
    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        SimpleModule module = new SimpleModule();
        // Use raw types for addSerializer to match Jackson expectations while keeping serializers generic
        module.addSerializer(Property.class, (JsonSerializer) new PropertySerializer());
        module.addDeserializer(DoubleProperty.class, new DoublePropertyDeserializer());
        module.addDeserializer(StringProperty.class, new StringPropertyDeserializer());
        module.addDeserializer(IntegerProperty.class, new IntegerPropertyDeserializer());
        module.addDeserializer(BooleanProperty.class, new BooleanPropertyDeserializer());
        module.addDeserializer(ObjectProperty.class, new ObjectPropertyDeserializer());
        module.addSerializer(ObservableList.class, (JsonSerializer) new ObservableListSerializer());
        module.addDeserializer(ObservableList.class, new ObservableListDeserializer());
        mapper.registerModule(module);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Saves a card template to a file.
     */
    public static void save(CardTemplate template, File file) throws IOException {
        mapper.writerWithDefaultPrettyPrinter().writeValue(file, template);
    }

    /**
     * Loads a card template from a file.
     */
    public static CardTemplate load(File file) throws IOException {
        return mapper.readValue(file, CardTemplate.class);
    }

    /**
     * Saves application settings.
     */
    public static void saveSettings(AppSettings settings) throws IOException {
        mapper.writerWithDefaultPrettyPrinter().writeValue(getSettingsFile(), settings);
    }

    /**
     * Loads application settings.
     */
    public static AppSettings loadSettings() throws IOException {
        File file = getSettingsFile();
        if (file.exists()) {
            return mapper.readValue(file, AppSettings.class);
        }
        return new AppSettings();
    }

    /**
     * Returns the file where settings are stored, ensuring parent directories exist.
     */
    public static File getSettingsFile() {
        return getConfigFile("settings.json");
    }

    /**
     * Deep clones an object using JSON serialization.
     */
    public static <T> T clone(T object, Class<T> clazz) throws IOException {
        String json = mapper.writeValueAsString(object);
        return mapper.readValue(json, clazz);
    }

     /**
     * Serializes a card template to JSON for in-memory snapshots.
     */
    public static String toJson(CardTemplate template) throws IOException {
        return mapper.writeValueAsString(template);
    }

    /**
     * Deserializes a card template from an in-memory JSON snapshot.
     */
    public static CardTemplate fromJson(String json) throws IOException {
        return mapper.readValue(json, CardTemplate.class);
    }

    /**
     * Returns the file where the temporary deck is stored, ensuring parent directories exist.
     */
    public static File getTempFile() {
        return getConfigFile("temp_deck.json");
    }

    private static File getConfigFile(String filename) {
        String userHome = System.getProperty("user.home");
        Path path = Paths.get(userHome, ".cardmaker", filename);
        File file = path.toFile();
        ensureParentExists(file);
        return file;
    }

    private static void ensureParentExists(File file) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs()) {
                // We don't throw here to match original behavior but maybe we should log
            }
        }
    }

    private static class PropertySerializer extends JsonSerializer<Property<?>> {
        @Override
        public void serialize(Property<?> value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value == null || value.getValue() == null) {
                gen.writeNull();
            } else {
                gen.writeObject(value.getValue());
            }
        }
    }

    private static class DoublePropertyDeserializer extends JsonDeserializer<DoubleProperty> {
        @Override
        public DoubleProperty deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return new SimpleDoubleProperty(p.getValueAsDouble());
        }
    }

    private static class StringPropertyDeserializer extends JsonDeserializer<StringProperty> {
        @Override
        public StringProperty deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return new SimpleStringProperty(p.getValueAsString());
        }
    }

    private static class IntegerPropertyDeserializer extends JsonDeserializer<IntegerProperty> {
        @Override
        public IntegerProperty deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return new SimpleIntegerProperty(p.getValueAsInt());
        }
    }

    private static class BooleanPropertyDeserializer extends JsonDeserializer<BooleanProperty> {
        @Override
        public BooleanProperty deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return new SimpleBooleanProperty(p.getValueAsBoolean());
        }
    }

    private static class ObjectPropertyDeserializer extends JsonDeserializer<ObjectProperty<?>> {
        @Override
        public ObjectProperty<?> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            if (node.isTextual()) {
                String textValue = node.asText();
                Object val = tryParseEnum(textValue);
                return new SimpleObjectProperty<>(val != null ? val : textValue);
            }
            return new SimpleObjectProperty<>(node);
        }

        private Object tryParseEnum(String value) {
            Class<?>[] enumClasses = {
                    ContainerElement.LayoutType.class,
                    ContainerElement.Alignment.class,
                    javafx.scene.text.FontWeight.class,
                    javafx.scene.text.FontPosture.class
            };
            for (Class<?> enumClass : enumClasses) {
                try {
                    @SuppressWarnings("unchecked")
                    Enum<?> e = Enum.valueOf((Class<Enum>) enumClass, value);
                    return e;
                } catch (IllegalArgumentException ignored) {
                }
            }
            return null;
        }
    }

    private static class ObservableListSerializer extends JsonSerializer<ObservableList<?>> {
        @Override
        public void serialize(ObservableList<?> value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartArray();
            for (Object item : value) {
                gen.writeObject(item);
            }
            gen.writeEndArray();
        }
    }

    private static class ObservableListDeserializer extends JsonDeserializer<ObservableList<CardElement>> {
        @Override
        public ObservableList<CardElement> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            List<CardElement> list = ctxt.readValue(p, mapper.getTypeFactory().constructCollectionType(List.class, CardElement.class));
            return FXCollections.observableArrayList(list);
        }
    }
}
