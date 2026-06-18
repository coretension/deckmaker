package io.github.coretension.deckmaker;

import io.github.coretension.deckmaker.model.CardElement;
import io.github.coretension.deckmaker.service.DeckRenderService;
import io.github.coretension.deckmaker.service.SingleCardImageExportService;
import javafx.collections.ObservableList;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SingleCardImageExportServiceTest {
    @Test
    void appendsSelectedImageExtensionWhenMissing() {
        File target = new File("C:\\cards\\front");

        assertEquals(new File("C:\\cards\\front.png"), SingleCardImageExportService.withImageExtension(target, "png"));
        assertEquals(new File("C:\\cards\\front.jpg"), SingleCardImageExportService.withImageExtension(target, "jpeg"));
    }

    @Test
    void keepsExistingSupportedImageExtension() {
        File target = new File("C:\\cards\\front.jpg");

        assertEquals(target, SingleCardImageExportService.withImageExtension(target, "png"));
    }

    @Test
    void exportsSingleCardAsPngWithoutBleedGuide() throws Exception {
        CapturingRenderer renderer = new CapturingRenderer();
        SingleCardImageExportService service = new SingleCardImageExportService(renderer);
        File target = Files.createTempFile("single-card", ".png").toFile();
        target.deleteOnExit();

        Map<String, String> record = Map.of("Name", "Ace");
        service.exportCard(target, record);

        BufferedImage exported = ImageIO.read(target);
        assertNotNull(exported);
        assertEquals(4, exported.getWidth());
        assertEquals(4, exported.getHeight());
        assertSame(record, renderer.record);
        assertFalse(renderer.showBleedGuide);
    }

    private static class CapturingRenderer implements DeckRenderService {
        private Map<String, String> record;
        private boolean showBleedGuide;

        @Override
        public boolean isProfessionalMode() {
            return false;
        }

        @Override
        public BufferedImage renderCardToImage(Map<String, String> record, double dpi, boolean showBleedGuide) {
            this.record = record;
            this.showBleedGuide = showBleedGuide;

            BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = image.createGraphics();
            g2d.setColor(Color.MAGENTA);
            g2d.fillRect(0, 0, image.getWidth(), image.getHeight());
            g2d.dispose();
            return image;
        }

        @Override
        public void renderElements(ObservableList<CardElement> elements, Pane targetPane, Map<String, String> currentRecord, boolean forFinalDesign) {
            throw new UnsupportedOperationException("Not used in this test.");
        }
    }
}
