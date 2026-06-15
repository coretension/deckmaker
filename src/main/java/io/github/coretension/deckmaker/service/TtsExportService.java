package io.github.coretension.deckmaker.service;

import io.github.coretension.deckmaker.model.CardTemplate;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Service to export a deck of cards as a single image grid compatible with Tabletop Simulator.
 * TTS typically uses a 10x7 grid (70 cards max per image).
 */
public class TtsExportService {
    private final CardTemplate template;
    private final List<Map<String, String>> csvData;
    private final DeckRenderService renderer;

    public TtsExportService(CardTemplate template, List<Map<String, String>> csvData, DeckRenderService renderer) {
        this.template = template;
        this.csvData = csvData;
        this.renderer = renderer;
    }

    /**
     * Exports the deck to a single image file for Tabletop Simulator.
     *
     * @param file           the destination image file (e.g., .jpg or .png)
     * @param cardsPerRow    number of cards per row (max 10 for TTS)
     * @param cardsPerColumn number of cards per column (max 7 for TTS)
     * @throws IOException if an error occurs during export
     */
    public void exportToTts(File file, int cardsPerRow, int cardsPerColumn) throws IOException {
        if (cardsPerRow < 1 || cardsPerRow > 10 || cardsPerColumn < 1 || cardsPerColumn > 7) {
            throw new IOException("TTS deck sheet grid must be between 1x1 and 10x7.");
        }

        List<Map<String, String>> records = csvData.isEmpty() ? List.of(Map.of()) : csvData;
        int totalCards = records.size();
        
        // TTS supports up to cardsPerRow * cardsPerColumn cards per sheet. 
        // If there are more, we might need multiple sheets, but usually one sheet is expected for a "deck".
        // The last slot (bottom right) is often used for the card back in TTS if not filled, 
        // but here we just fill as many as we have.
        
        double dpi = 300; // High resolution for export
        boolean proMode = renderer.isProfessionalMode();
        double bleedMm = proMode ? template.getBleedMm() : 0;
        
        int cardWidthPx = (int) Math.round((template.getDimension().getWidthMm() + 2 * bleedMm) * dpi / 25.4);
        int cardHeightPx = (int) Math.round((template.getDimension().getHeightMm() + 2 * bleedMm) * dpi / 25.4);

        int sheetWidth = cardWidthPx * cardsPerRow;
        int sheetHeight = cardHeightPx * cardsPerColumn;

        BufferedImage sheet = new BufferedImage(sheetWidth, sheetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = sheet.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, sheetWidth, sheetHeight);

        int count = 0;
        for (Map<String, String> record : records) {
            if (count >= cardsPerRow * cardsPerColumn) break; // Limit to one sheet for now

            BufferedImage cardImage = renderer.renderCardToImage(record, dpi, true);
            int row = count / cardsPerRow;
            int col = count % cardsPerRow;
            int x = col * cardWidthPx;
            int y = row * cardHeightPx;

            g2d.drawImage(cardImage, x, y, null);
            count++;
        }

        g2d.dispose();

        String format = "jpg";
        if (file.getName().toLowerCase().endsWith(".png")) {
            format = "png";
        }
        ImageIO.write(sheet, format, file);
    }

}
