package io.github.coretension.cardmaker.service;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class DataMerger {

    public static class CsvResult {
        public List<String> headers;
        public List<Map<String, String>> records;

        public CsvResult(List<String> headers, List<Map<String, String>> records) {
            this.headers = headers;
            this.records = records;
        }
    }

    public CsvResult loadCsv(String filePath) throws IOException, CsvException {
        if (filePath.toLowerCase().endsWith(".ods")) {
            return loadOds(filePath);
        }
        if (filePath.toLowerCase().endsWith(".xlsx")) {
            return loadXlsx(filePath);
        }
        try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
            List<String[]> lines = reader.readAll();
            if (lines.isEmpty()) return new CsvResult(new ArrayList<>(), new ArrayList<>());

            List<String> headers = List.of(lines.getFirst());
            List<Map<String, String>> records = new ArrayList<>();

            for (int i = 1; i < lines.size(); i++) {
                String[] values = lines.get(i);
                Map<String, String> record = new HashMap<>();
                for (int j = 0; j < headers.size() && j < values.length; j++) {
                    record.put(headers.get(j), values[j]);
                }
                records.add(record);
            }
            return new CsvResult(headers, records);
        }
    }

    /**
     * Minimal dependency-free ODS parser.
     * ODS is a ZIP containing content.xml with table:table-row and table:table-cell elements.
     */
    public CsvResult loadOds(String filePath) throws IOException {
        try (ZipFile zipFile = new ZipFile(filePath)) {
            ZipEntry entry = zipFile.getEntry("content.xml");
            if (entry == null) throw new IOException("Not a valid ODS file: content.xml missing");

            try (InputStream is = zipFile.getInputStream(entry)) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(is);

                NodeList rowNodes = doc.getElementsByTagNameNS("urn:oasis:names:tc:opendocument:xmlns:table:1.0", "table-row");
                List<List<String>> allRows = new ArrayList<>();

                for (int i = 0; i < rowNodes.getLength(); i++) {
                    Node rowNode = rowNodes.item(i);
                    List<String> rowValues = new ArrayList<>();
                    NodeList cellNodes = rowNode.getChildNodes();
                    for (int j = 0; j < cellNodes.getLength(); j++) {
                        Node cellNode = cellNodes.item(j);
                        if ("table-cell".equals(cellNode.getLocalName())) {
                            String value = getCellTextValue(cellNode);
                            rowValues.add(value);

                            // Handle table:number-columns-repeated
                            Node repeatedAttr = cellNode.getAttributes().getNamedItemNS("urn:oasis:names:tc:opendocument:xmlns:table:1.0", "number-columns-repeated");
                            if (repeatedAttr != null) {
                                int repeatedCount = Integer.parseInt(repeatedAttr.getNodeValue());
                                // We only repeat if it's a reasonable number to avoid OOM from huge empty sheets
                                for (int k = 1; k < repeatedCount && k < 1000; k++) {
                                    rowValues.add(value);
                                }
                            }
                        }
                    }
                    if (!rowValues.stream().allMatch(String::isEmpty)) {
                        allRows.add(rowValues);
                    }
                }

                if (allRows.isEmpty()) return new CsvResult(new ArrayList<>(), new ArrayList<>());

                List<String> headers = allRows.getFirst();
                List<Map<String, String>> records = new ArrayList<>();
                for (int i = 1; i < allRows.size(); i++) {
                    List<String> row = allRows.get(i);
                    Map<String, String> record = new HashMap<>();
                    for (int j = 0; j < headers.size() && j < row.size(); j++) {
                        record.put(headers.get(j), row.get(j));
                    }
                    records.add(record);
                }
                return new CsvResult(headers, records);
            } catch (Exception e) {
                throw new IOException("Failed to parse ODS content.xml: " + e.getMessage(), e);
            }
        }
    }

    private String getCellTextValue(Node cellNode) {
        StringBuilder sb = new StringBuilder();
        NodeList textNodes = cellNode.getChildNodes();
        for (int i = 0; i < textNodes.getLength(); i++) {
            Node node = textNodes.item(i);
            if ("p".equals(node.getLocalName())) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(node.getTextContent());
            }
        }
        return sb.toString();
    }

    public CsvResult loadXlsx(String filePath) throws IOException {
        try (InputStream is = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            List<String> headers = new ArrayList<>();
            List<Map<String, String>> records = new ArrayList<>();

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) return new CsvResult(headers, records);

            for (Cell cell : headerRow) {
                headers.add(getExcelCellValue(cell));
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                Map<String, String> record = new HashMap<>();
                boolean hasContent = false;
                for (int j = 0; j < headers.size(); j++) {
                    Cell cell = row.getCell(j);
                    String val = getExcelCellValue(cell);
                    if (!val.isEmpty()) hasContent = true;
                    record.put(headers.get(j), val);
                }
                if (hasContent) {
                    records.add(record);
                }
            }
            return new CsvResult(headers, records);
        }
    }

    private String getExcelCellValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toString();
                } else {
                    double val = cell.getNumericCellValue();
                    if (val == (long) val) {
                        yield String.valueOf((long) val);
                    } else {
                        yield String.valueOf(val);
                    }
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        yield String.valueOf(cell.getNumericCellValue());
                    } catch (Exception e2) {
                        yield cell.getCellFormula();
                    }
                }
            }
            default -> "";
        };
    }


    public void saveCsv(String filePath, List<String> headers, List<Map<String, String>> records) throws IOException {
        try (CSVWriter writer = new CSVWriter(new FileWriter(filePath))) {
            String[] headerArray = headers.toArray(new String[0]);
            writer.writeNext(headerArray);

            for (Map<String, String> record : records) {
                String[] values = new String[headers.size()];
                for (int i = 0; i < headers.size(); i++) {
                    values[i] = record.getOrDefault(headers.get(i), "");
                }
                writer.writeNext(values);
            }
        }
    }

    public void saveOds(String filePath, List<String> headers, List<Map<String, String>> records) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();

            // Root element
            org.w3c.dom.Element officeDoc = doc.createElementNS("urn:oasis:names:tc:opendocument:xmlns:office:1.0", "office:document-content");
            officeDoc.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:office", "urn:oasis:names:tc:opendocument:xmlns:office:1.0");
            officeDoc.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:table", "urn:oasis:names:tc:opendocument:xmlns:table:1.0");
            officeDoc.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:text", "urn:oasis:names:tc:opendocument:xmlns:text:1.0");
            doc.appendChild(officeDoc);

            org.w3c.dom.Element officeBody = doc.createElementNS("urn:oasis:names:tc:opendocument:xmlns:office:1.0", "office:body");
            officeDoc.appendChild(officeBody);

            org.w3c.dom.Element officeSpreadsheet = doc.createElementNS("urn:oasis:names:tc:opendocument:xmlns:office:1.0", "office:spreadsheet");
            officeBody.appendChild(officeSpreadsheet);

            org.w3c.dom.Element table = doc.createElementNS("urn:oasis:names:tc:opendocument:xmlns:table:1.0", "table:table");
            table.setAttributeNS("urn:oasis:names:tc:opendocument:xmlns:table:1.0", "table:name", "Sheet1");
            officeSpreadsheet.appendChild(table);

            // Header row
            org.w3c.dom.Element headerRow = doc.createElementNS("urn:oasis:names:tc:opendocument:xmlns:table:1.0", "table:table-row");
            table.appendChild(headerRow);
            for (String header : headers) {
                org.w3c.dom.Element cell = doc.createElementNS("urn:oasis:names:tc:opendocument:xmlns:table:1.0", "table:table-cell");
                cell.setAttributeNS("urn:oasis:names:tc:opendocument:xmlns:office:1.0", "office:value-type", "string");
                org.w3c.dom.Element p = doc.createElementNS("urn:oasis:names:tc:opendocument:xmlns:text:1.0", "text:p");
                p.setTextContent(header);
                cell.appendChild(p);
                headerRow.appendChild(cell);
            }

            // Data rows
            for (Map<String, String> record : records) {
                org.w3c.dom.Element row = doc.createElementNS("urn:oasis:names:tc:opendocument:xmlns:table:1.0", "table:table-row");
                table.appendChild(row);
                for (String header : headers) {
                    org.w3c.dom.Element cell = doc.createElementNS("urn:oasis:names:tc:opendocument:xmlns:table:1.0", "table:table-cell");
                    cell.setAttributeNS("urn:oasis:names:tc:opendocument:xmlns:office:1.0", "office:value-type", "string");
                    String value = record.getOrDefault(header, "");
                    String[] lines = value.split("\n", -1);
                    for (String line : lines) {
                        org.w3c.dom.Element p = doc.createElementNS("urn:oasis:names:tc:opendocument:xmlns:text:1.0", "text:p");
                        p.setTextContent(line);
                        cell.appendChild(p);
                    }
                    row.appendChild(cell);
                }
            }

            // Write to content.xml in memory
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            DOMSource source = new DOMSource(doc);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            StreamResult result = new StreamResult(bos);
            transformer.transform(source, result);

            // Create ODS zip file
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(filePath))) {
                // mimetype (must be first and uncompressed)
                ZipEntry mimetypeEntry = new ZipEntry("mimetype");
                mimetypeEntry.setMethod(ZipEntry.STORED);
                byte[] mimetypeBytes = "application/vnd.oasis.opendocument.spreadsheet".getBytes(StandardCharsets.UTF_8);
                mimetypeEntry.setSize(mimetypeBytes.length);
                mimetypeEntry.setCrc(calculateCrc(mimetypeBytes));
                zos.putNextEntry(mimetypeEntry);
                zos.write(mimetypeBytes);
                zos.closeEntry();

                // content.xml
                ZipEntry contentEntry = new ZipEntry("content.xml");
                zos.putNextEntry(contentEntry);
                zos.write(bos.toByteArray());
                zos.closeEntry();

                // Minimal manifest
                ZipEntry manifestEntry = new ZipEntry("META-INF/manifest.xml");
                zos.putNextEntry(manifestEntry);
                String manifestContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<manifest:manifest xmlns:manifest=\"urn:oasis:names:tc:opendocument:xmlns:manifest:1.0\" manifest:version=\"1.2\">\n" +
                        " <manifest:file-entry manifest:full-path=\"/\" manifest:version=\"1.2\" manifest:media-type=\"application/vnd.oasis.opendocument.spreadsheet\"/>\n" +
                        " <manifest:file-entry manifest:full-path=\"content.xml\" manifest:media-type=\"text/xml\"/>\n" +
                        "</manifest:manifest>";
                zos.write(manifestContent.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        } catch (Exception e) {
            throw new IOException("Failed to save ODS file: " + e.getMessage(), e);
        }
    }

    public void saveXlsx(String filePath, List<String> headers, List<Map<String, String>> records) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(filePath)) {
            Sheet sheet = workbook.createSheet("Sheet1");

            // Header row
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
            }

            // Data rows
            for (int i = 0; i < records.size(); i++) {
                Row row = sheet.createRow(i + 1);
                Map<String, String> record = records.get(i);
                for (int j = 0; j < headers.size(); j++) {
                    Cell cell = row.createCell(j);
                    cell.setCellValue(record.getOrDefault(headers.get(j), ""));
                }
            }

            workbook.write(fos);
        }
    }

    private long calculateCrc(byte[] data) {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(data);
        return crc.getValue();
    }

    public void saveData(String filePath, List<String> headers, List<Map<String, String>> records) throws IOException {
        if (filePath.toLowerCase().endsWith(".ods")) {
            saveOds(filePath, headers, records);
        } else if (filePath.toLowerCase().endsWith(".xlsx")) {
            saveXlsx(filePath, headers, records);
        } else {
            saveCsv(filePath, headers, records);
        }
    }

    /**
     * Merges a template string with values from a data record.
     * Tags in the format {{ColumnName}} are replaced with corresponding record values.
     *
     * @param template the template string
     * @param record   the data record
     * @return the merged string
     */
    public String merge(String template, Map<String, String> record) {
        if (template == null) return null;
        if (record == null || !template.contains("{{")) return template;

        StringBuilder sb = new StringBuilder();
        int lastPos = 0;
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{\\{(.+?)\\}\\}");
        java.util.regex.Matcher matcher = pattern.matcher(template);
        while (matcher.find()) {
            sb.append(template, lastPos, matcher.start());
            String key = matcher.group(1);
            sb.append(record.getOrDefault(key, matcher.group(0)));
            lastPos = matcher.end();
        }
        sb.append(template.substring(lastPos));
        return sb.toString();
    }

    /**
     * Evaluates a simple conditional expression against a data record.
     * Supports "==" and "!=" operators. If no operator is present, returns true if the merged string is not empty.
     *
     * @param condition the condition string
     * @param record    the data record
     * @return true if the condition is met
     */
    public boolean evaluateCondition(String condition, Map<String, String> record) {
        if (condition == null || condition.trim().isEmpty()) return true;
        if (record == null) return false;

        String mergedCondition = merge(condition, record).trim();
        
        // Simple equality: "val1 == val2"
        if (mergedCondition.contains("==")) {
            String[] parts = mergedCondition.split("==");
            if (parts.length == 2) {
                return parts[0].trim().equals(parts[1].trim());
            }
        }
        
        // Simple inequality: "val1 != val2"
        if (mergedCondition.contains("!=")) {
            String[] parts = mergedCondition.split("!=");
            if (parts.length == 2) {
                return !parts[0].trim().equals(parts[1].trim());
            }
        }

        // Check if merge actually did something or if the tag remains
        if (mergedCondition.startsWith("{{") && mergedCondition.endsWith("}}")) {
            return false;
        }

        // Just check if not empty if no operator
        return !mergedCondition.isEmpty();
    }
}
