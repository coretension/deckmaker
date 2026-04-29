package io.github.coretension.cardmaker.ui;

import javafx.beans.property.DoubleProperty;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;

import java.io.File;

public class UIUtils {

    public static HBox createSliderWithNumericField(DoubleProperty property, double min, double max) {
        Slider slider = new Slider(min, max, property.get());
        slider.setMinWidth(150);
        slider.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(slider, Priority.ALWAYS);
        slider.valueProperty().bindBidirectional(property);

        TextField textField = new TextField();
        textField.setPrefWidth(50);

        // Formatting function for numeric field
        java.lang.Runnable updateText = () -> {
            if (max <= 1.0) {
                textField.setText(String.format("%.2f", property.get()));
            } else {
                textField.setText(String.format("%.0f", property.get()));
            }
        };

        updateText.run();

        property.addListener((obs, old, newVal) -> {
            if (!textField.isFocused()) {
                updateText.run();
            }
        });

        textField.setOnAction(e -> {
            try {
                double val = Double.parseDouble(textField.getText());
                property.set(Math.max(min, Math.min(max, val)));
                updateText.run();
            } catch (NumberFormatException ex) {
                updateText.run();
            }
        });

        textField.focusedProperty().addListener((obs, old, newVal) -> {
            if (!newVal) { // Lost focus
                try {
                    double val = Double.parseDouble(textField.getText());
                    property.set(Math.max(min, Math.min(max, val)));
                    updateText.run();
                } catch (NumberFormatException ex) {
                    updateText.run();
                }
            }
        });

        return new HBox(10, slider, textField);
    }

    public static String toHexString(Color color) {
        return String.format("#%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }

    public static String relativizePath(File file) {
        return relativizePath(file, null);
    }

    public static String relativizePath(File file, File baseDir) {
        if (file == null) return "";
        String path = file.getAbsolutePath();

        if (baseDir != null) {
            try {
                return baseDir.toPath().relativize(file.toPath()).toString();
            } catch (IllegalArgumentException ex) {
                // Different drives, continue with other methods
            }
        }

        String userHome = System.getProperty("user.home");
        if (path.startsWith(userHome)) {
            return "~" + path.substring(userHome.length());
        }
        return path;
    }
}
