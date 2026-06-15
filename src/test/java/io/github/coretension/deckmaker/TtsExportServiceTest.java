package io.github.coretension.deckmaker;

import io.github.coretension.deckmaker.model.CardTemplate;
import io.github.coretension.deckmaker.service.TtsExportService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TtsExportServiceTest {

    @Test
    public void testTtsExportServiceCreation() {
        CardTemplate template = new CardTemplate();
        List<Map<String, String>> csvData = new ArrayList<>();
        // Rendering is supplied at export time by the application; this checks service construction only.
        TtsExportService service = new TtsExportService(template, csvData, null);
        assertNotNull(service);
    }
}

