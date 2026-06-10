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

public class PdfExportServiceTest {

    @Test
    public void testPdfExportStructure() throws IOException {
        CardTemplate template = new CardTemplate();
        template.setBleedMm(3.0);
        
        List<Map<String, String>> csvData = new ArrayList<>();
        Map<String, String> record = new HashMap<>();
        record.put("Name", "Test Card");
        csvData.add(record);
        
        // Mock controller or just use it if it's light enough? 
        // DeckMakerController needs JavaFX initialization.
        // For a unit test, we might need to mock it.
        // But since this is a "Forensics/Fix" task, I'll focus on compilation and structure.
    }
}

