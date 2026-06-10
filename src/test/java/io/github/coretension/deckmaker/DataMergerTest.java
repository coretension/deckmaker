package io.github.coretension.deckmaker;

import io.github.coretension.deckmaker.config.*;
import io.github.coretension.deckmaker.model.*;
import io.github.coretension.deckmaker.persistence.*;
import io.github.coretension.deckmaker.service.*;
import com.opencsv.exceptions.CsvException;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class DataMergerTest {

    @Test
    public void testLoadOds() throws IOException, CsvException {
        Path tempFile = Files.createTempFile("test", ".ods");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempFile.toFile()))) {
            zos.putNextEntry(new ZipEntry("content.xml"));
            String content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<office:document-content xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\" " +
                    "xmlns:table=\"urn:oasis:names:tc:opendocument:xmlns:table:1.0\" " +
                    "xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\">\n" +
                    "  <office:body>\n" +
                    "    <office:spreadsheet>\n" +
                    "      <table:table>\n" +
                    "        <table:table-row>\n" +
                    "          <table:table-cell><text:p>Name</text:p></table:table-cell>\n" +
                    "          <table:table-cell><text:p>Power</text:p></table:table-cell>\n" +
                    "        </table:table-row>\n" +
                    "        <table:table-row>\n" +
                    "          <table:table-cell><text:p>Hero</text:p></table:table-cell>\n" +
                    "          <table:table-cell><text:p>Flight</text:p></table:table-cell>\n" +
                    "        </table:table-row>\n" +
                    "      </table:table>\n" +
                    "    </office:spreadsheet>\n" +
                    "  </office:body>\n" +
                    "</office:document-content>";
            zos.write(content.getBytes());
            zos.closeEntry();
        }

        DataMerger merger = new DataMerger();
        DataMerger.CsvResult result = merger.loadCsv(tempFile.toAbsolutePath().toString());

        assertEquals(2, result.headers.size());
        assertEquals("Name", result.headers.get(0));
        assertEquals("Power", result.headers.get(1));

        assertEquals(1, result.records.size());
        assertEquals("Hero", result.records.get(0).get("Name"));
        assertEquals("Flight", result.records.get(0).get("Power"));

        Files.deleteIfExists(tempFile);
    }

    @Test
    public void testLoadCsv() throws IOException, CsvException {
        Path tempFile = Files.createTempFile("test", ".csv");
        Files.writeString(tempFile, "Name,Power\nHero,Flight\nSidekick,Speed");
        
        DataMerger merger = new DataMerger();
        DataMerger.CsvResult result = merger.loadCsv(tempFile.toAbsolutePath().toString());
        
        assertEquals(2, result.headers.size());
        assertEquals("Name", result.headers.get(0));
        assertEquals("Power", result.headers.get(1));
        
        assertEquals(2, result.records.size());
        assertEquals("Hero", result.records.get(0).get("Name"));
        assertEquals("Flight", result.records.get(0).get("Power"));
        assertEquals("Sidekick", result.records.get(1).get("Name"));
        assertEquals("Speed", result.records.get(1).get("Power"));
        
        Files.deleteIfExists(tempFile);
    }

    @Test
    public void testMerge() {
        DataMerger merger = new DataMerger();
        Map<String, String> record = new HashMap<>();
        record.put("Name", "Hero");
        record.put("Power", "Flight");

        assertEquals("Name: Hero", merger.merge("Name: {{Name}}", record));
        assertEquals("Hero has Flight", merger.merge("{{Name}} has {{Power}}", record));
        assertEquals("No change", merger.merge("No change", record));
        assertEquals("{{Unknown}} stays", merger.merge("{{Unknown}} stays", record));
    }

    @Test
    public void testMergeWithEmptyRecord() {
        DataMerger merger = new DataMerger();
        Map<String, String> record = new HashMap<>();
        assertEquals("{{Name}}", merger.merge("{{Name}}", record));
    }

    @Test
    public void testEvaluateCondition() {
        DataMerger merger = new DataMerger();
        Map<String, String> record = new HashMap<>();
        record.put("Type", "Spell");
        record.put("Power", "10");

        assertTrue(merger.evaluateCondition("{{Type}} == Spell", record));
        assertFalse(merger.evaluateCondition("{{Type}} == Creature", record));
        assertTrue(merger.evaluateCondition("{{Type}} != Creature", record));
        assertFalse(merger.evaluateCondition("{{Type}} != Spell", record));
        
        assertTrue(merger.evaluateCondition("{{Power}} == 10", record));
        assertTrue(merger.evaluateCondition("10 == {{Power}}", record));
        
        // No operator: checks if not empty after merge
        assertTrue(merger.evaluateCondition("{{Type}}", record));
        assertFalse(merger.evaluateCondition("{{Unknown}}", record));
        
        // Empty condition: always true
        assertTrue(merger.evaluateCondition("", record));
        assertTrue(merger.evaluateCondition(null, record));
        
        // Null record: false if condition present
        assertFalse(merger.evaluateCondition("{{Type}}", null));
    }
}

