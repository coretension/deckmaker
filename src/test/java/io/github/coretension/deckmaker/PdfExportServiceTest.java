package io.github.coretension.deckmaker;

import io.github.coretension.deckmaker.config.*;
import io.github.coretension.deckmaker.model.*;
import io.github.coretension.deckmaker.persistence.*;
import io.github.coretension.deckmaker.service.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PdfExportServiceTest {

    @Test
    public void testPdfExportStructure() throws IOException {
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

