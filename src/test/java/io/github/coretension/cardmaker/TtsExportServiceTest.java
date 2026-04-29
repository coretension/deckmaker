package io.github.coretension.cardmaker;

import io.github.coretension.cardmaker.config.*;
import io.github.coretension.cardmaker.model.*;
import io.github.coretension.cardmaker.persistence.*;
import io.github.coretension.cardmaker.service.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class TtsExportServiceTest {

    @Test
    public void testTtsExportServiceCreation() {
        CardTemplate template = new CardTemplate();
        List<Map<String, String>> csvData = new ArrayList<>();
        // CardMakerController is harder to instantiate without JavaFX, but we can check if service exists
        TtsExportService service = new TtsExportService(template, csvData, null);
        assertNotNull(service);
    }
}

