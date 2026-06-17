package io.github.coretension.deckmaker;

import io.github.coretension.deckmaker.service.DataMerger;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DataMergerSaveTest {

    @Test
    public void testSaveAndLoadOds() throws Exception {
        DataMerger dataMerger = new DataMerger();
        List<String> headers = List.of("Name", "Type", "HP");
        List<Map<String, String>> records = new ArrayList<>();
        
        Map<String, String> r1 = new HashMap<>();
        r1.put("Name", "Hero");
        r1.put("Type", "Character");
        r1.put("HP", "100");
        records.add(r1);
        
        Map<String, String> r2 = new HashMap<>();
        r2.put("Name", "Villain");
        r2.put("Type", "Enemy");
        r2.put("HP", "200");
        records.add(r2);

        File tempFile = Files.createTempFile("testdata", ".ods").toFile();
        try {
            dataMerger.saveOds(tempFile.getAbsolutePath(), headers, records);
            
            assertTrue(tempFile.exists());
            assertTrue(tempFile.length() > 0);
            
            DataMerger.CsvResult loaded = dataMerger.loadOds(tempFile.getAbsolutePath());
            
            assertEquals(headers, loaded.headers);
            assertEquals(2, loaded.records.size());
            
            assertEquals("Hero", loaded.records.getFirst().get("Name"));
            assertEquals("Character", loaded.records.getFirst().get("Type"));
            assertEquals("100", loaded.records.getFirst().get("HP"));
            
            assertEquals("Villain", loaded.records.get(1).get("Name"));
            assertEquals("Enemy", loaded.records.get(1).get("Type"));
            assertEquals("200", loaded.records.get(1).get("HP"));
            
        } finally {
            tempFile.delete();
        }
    }

    @Test
    public void testSaveDataAutoFormat() throws Exception {
        DataMerger dataMerger = new DataMerger();
        List<String> headers = List.of("ID", "Value");
        List<Map<String, String>> records = List.of(Map.of("ID", "1", "Value", "A"));

        File csvFile = Files.createTempFile("test", ".csv").toFile();
        File odsFile = Files.createTempFile("test", ".ods").toFile();
        
        try {
            dataMerger.saveData(csvFile.getAbsolutePath(), headers, records);
            dataMerger.saveData(odsFile.getAbsolutePath(), headers, records);
            
            DataMerger.CsvResult csvLoaded = dataMerger.loadCsv(csvFile.getAbsolutePath());
            DataMerger.CsvResult odsLoaded = dataMerger.loadCsv(odsFile.getAbsolutePath());
            
            assertEquals("1", csvLoaded.records.getFirst().get("ID"));
            assertEquals("1", odsLoaded.records.getFirst().get("ID"));
        } finally {
            csvFile.delete();
            odsFile.delete();
        }
    }

    @Test
    public void testSaveAndLoadXlsx() throws Exception {
        DataMerger dataMerger = new DataMerger();
        List<String> headers = List.of("Name", "Value", "Numeric");
        List<Map<String, String>> records = new ArrayList<>();

        Map<String, String> r1 = new HashMap<>();
        r1.put("Name", "Item 1");
        r1.put("Value", "Text 1");
        r1.put("Numeric", "123");
        records.add(r1);

        Map<String, String> r2 = new HashMap<>();
        r2.put("Name", "Item 2");
        r2.put("Value", "Text 2");
        r2.put("Numeric", "45.6");
        records.add(r2);

        File tempFile = Files.createTempFile("testdata", ".xlsx").toFile();
        try {
            dataMerger.saveXlsx(tempFile.getAbsolutePath(), headers, records);

            assertTrue(tempFile.exists());
            assertTrue(tempFile.length() > 0);

            DataMerger.CsvResult loaded = dataMerger.loadXlsx(tempFile.getAbsolutePath());

            assertEquals(headers, loaded.headers);
            assertEquals(2, loaded.records.size());

            assertEquals("Item 1", loaded.records.getFirst().get("Name"));
            assertEquals("Text 1", loaded.records.getFirst().get("Value"));
            assertEquals("123", loaded.records.getFirst().get("Numeric"));

            assertEquals("Item 2", loaded.records.get(1).get("Name"));
            assertEquals("Text 2", loaded.records.get(1).get("Value"));
            assertEquals("45.6", loaded.records.get(1).get("Numeric"));

        } finally {
            tempFile.delete();
        }
    }
}

