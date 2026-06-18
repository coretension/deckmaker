package io.github.coretension.deckmaker.service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/**
 * Exports one rendered card as a standalone image file.
 */
public class SingleCardImageExportService {
    private static final double EXPORT_DPI = 300;

    private final DeckRenderService renderer;

    public SingleCardImageExportService(DeckRenderService renderer) {
        this.renderer = renderer;
    }

    public void exportCard(File file, Map<String, String> record) throws IOException {
        if (renderer == null) {
            throw new IOException("No renderer is available for card image export.");
        }

        String format = imageFormatFor(file);
        Map<String, String> safeRecord = record == null ? Map.of() : record;
        BufferedImage cardImage = renderer.renderCardToImage(safeRecord, EXPORT_DPI, false);
        BufferedImage writableImage = "jpg".equals(format) ? toRgbImage(cardImage) : cardImage;

        if (!ImageIO.write(writableImage, format, file)) {
            throw new IOException("Unsupported image format: " + format);
        }
    }

    public static File withImageExtension(File file, String extension) {
        if (file == null || hasSupportedImageExtension(file)) {
            return file;
        }
        String normalizedExtension = extension == null || extension.isBlank() ? "png" : extension.toLowerCase(Locale.ROOT);
        if ("jpeg".equals(normalizedExtension)) {
            normalizedExtension = "jpg";
        }
        return new File(file.getPath() + "." + normalizedExtension);
    }

    private static boolean hasSupportedImageExtension(File file) {
        String lowerName = file.getName().toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg");
    }

    private static String imageFormatFor(File file) {
        String lowerName = file.getName().toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            return "jpg";
        }
        return "png";
    }

    private static BufferedImage toRgbImage(BufferedImage source) {
        BufferedImage rgbImage = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = rgbImage.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, source.getWidth(), source.getHeight());
        g2d.drawImage(source, 0, 0, null);
        g2d.dispose();
        return rgbImage;
    }
}
