package io.github.coretension.deckmaker;

import io.github.coretension.deckmaker.model.CardTemplate;
import io.github.coretension.deckmaker.service.PdfExportService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PdfExportServiceTest {

    @Test
    public void testPdfExportStructure() {
        CardTemplate template = new CardTemplate();
        template.setBleedMm(3.0);
        
        List<Map<String, String>> csvData = new ArrayList<>();
        Map<String, String> record = new HashMap<>();
        record.put("Name", "Test Card");
        csvData.add(record);
        
        PdfExportService service = new PdfExportService(template, csvData, null);
        assertNotNull(service);
    }
}

