package io.github.coretension.deckmaker.service;

import com.lowagie.text.Document;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;
import io.github.coretension.deckmaker.model.CardTemplate;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class PdfExportService {
    private final CardTemplate template;
    private final List<Map<String, String>> csvData;
    private final DeckRenderService renderer;

    public PdfExportService(CardTemplate template, List<Map<String, String>> csvData, DeckRenderService renderer) {
        this.template = template;
        this.csvData = csvData;
        this.renderer = renderer;
    }

    /**
     * Exports the current deck and data to a PDF file.
     *
     * @param file the destination PDF file
     * @throws IOException if an error occurs during export
     */
    public void exportToPdf(File file) throws IOException {
        double cardWidthMm = template.getDimension().getWidthMm();
        double cardHeightMm = template.getDimension().getHeightMm();
        boolean proMode = renderer.isProfessionalMode();
        double bleedMm = proMode ? template.getBleedMm() : 0;
        
        float totalWidthPt = (float)((cardWidthMm + 2 * bleedMm) * 72 / 25.4);
        float totalHeightPt = (float)((cardHeightMm + 2 * bleedMm) * 72 / 25.4);

        Document document = new Document(new Rectangle(totalWidthPt, totalHeightPt), 0, 0, 0, 0);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(file));
            
            // Set PDF to use CMYK Intent (simplified)
            writer.setPdfVersion(PdfWriter.VERSION_1_4);
            
            document.open();
            PdfContentByte cb = writer.getDirectContent();

            List<Map<String, String>> records = csvData.isEmpty() ? List.of(Map.of()) : csvData;

            for (Map<String, String> record : records) {
                document.newPage();
                
                // Render card to image at high DPI (300)
                BufferedImage cardImage = renderer.renderCardToImage(record, 300, false);
                
                com.lowagie.text.Image pdfImg = convertToCmykImage(cardImage);
                pdfImg.scaleToFit(totalWidthPt, totalHeightPt);
                pdfImg.setAbsolutePosition(0, 0);
                cb.addImage(pdfImg);
            }
        } catch (Exception e) {
            throw new IOException("Failed to export PDF: " + e.getMessage(), e);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
    }


    /**
     * Converts an RGB BufferedImage to a CMYK com.lowagie.text.Image.
     * Uses a simple RGB to CMYK conversion formula.
     *
     * @param rgbImage the source RGB image
     * @return the converted CMYK image
     */
    private com.lowagie.text.Image convertToCmykImage(BufferedImage rgbImage) {
        int w = rgbImage.getWidth();
        int h = rgbImage.getHeight();
        
        byte[] cmykData = new byte[w * h * 4];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = rgbImage.getRGB(x, y);
                float r = ((rgb >> 16) & 0xFF) / 255f;
                float g = ((rgb >> 8) & 0xFF) / 255f;
                float b = (rgb & 0xFF) / 255f;
                
                float k = 1.0f - Math.max(r, Math.max(g, b));
                float c = (1.0f - r - k) / (1.0f - k + 1e-9f);
                float m = (1.0f - g - k) / (1.0f - k + 1e-9f);
                float yVal = (1.0f - b - k) / (1.0f - k + 1e-9f);
                
                int index = (y * w + x) * 4;
                cmykData[index] = (byte) (Math.max(0, Math.min(1, c)) * 255);
                cmykData[index + 1] = (byte) (Math.max(0, Math.min(1, m)) * 255);
                cmykData[index + 2] = (byte) (Math.max(0, Math.min(1, yVal)) * 255);
                cmykData[index + 3] = (byte) (Math.max(0, Math.min(1, k)) * 255);
            }
        }
        
        try {
            return com.lowagie.text.Image.getInstance(w, h, 4, 8, cmykData);
        } catch (com.lowagie.text.DocumentException e) {
            throw new RuntimeException("Failed to create PDF image", e);
        }
    }
}
