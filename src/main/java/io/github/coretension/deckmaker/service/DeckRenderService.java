package io.github.coretension.deckmaker.service;

import io.github.coretension.deckmaker.model.CardElement;
import javafx.collections.ObservableList;
import javafx.scene.layout.Pane;

import java.awt.image.BufferedImage;
import java.util.Map;

/**
 * Narrow rendering contract used by production services without depending on the UI controller.
 */
public interface DeckRenderService {
    boolean isProfessionalMode();

    BufferedImage renderCardToImage(Map<String, String> record, double dpi, boolean showBleedGuide);

    void renderElements(ObservableList<CardElement> elements, Pane targetPane, Map<String, String> currentRecord, boolean forFinalDesign);
}
