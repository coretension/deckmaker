package io.github.coretension.deckmaker.service;

import io.github.coretension.deckmaker.model.CardElement;
import io.github.coretension.deckmaker.model.CardTemplate;
import io.github.coretension.deckmaker.model.ConditionElement;
import io.github.coretension.deckmaker.model.ContainerElement;
import io.github.coretension.deckmaker.model.IconElement;
import io.github.coretension.deckmaker.model.ImageElement;
import io.github.coretension.deckmaker.model.ParentCardElement;
import io.github.coretension.deckmaker.model.TextElement;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates a deck template before production actions such as print and export.
 */
public class PreflightService {
    private static final int MAX_REPEATED_ISSUES = 12;
    private static final Pattern TAG_PATTERN = Pattern.compile("\\{\\{(.+?)\\}\\}");

    private final DataMerger dataMerger;

    public PreflightService(DataMerger dataMerger) {
        this.dataMerger = dataMerger;
    }

    public PreflightReport validate(CardTemplate template,
                                    List<Map<String, String>> records,
                                    List<String> headers,
                                    File deckFile) {
        CardTemplate checkedTemplate = template == null ? new CardTemplate() : template;
        List<Map<String, String>> checkedRecords = records == null ? List.of() : records;
        List<String> checkedHeaders = headers == null ? List.of() : headers;
        Set<String> headerSet = new HashSet<>(checkedHeaders);

        CardTemplateContext context = new CardTemplateContext(
                checkedTemplate,
                checkedRecords,
                headerSet,
                deckFile == null ? null : deckFile.getParentFile()
        );

        PreflightReport report = new PreflightReport();
        if (checkedRecords.isEmpty()) {
            report.add(Severity.ERROR, "No CSV records are loaded, so print/export would produce no cards.");
        }
        if (checkedTemplate.getElements().isEmpty()) {
            report.add(Severity.WARNING, "The template has no elements.");
        }

        validateElements(checkedTemplate.getElements(), context, report, "");
        return report;
    }

    private void validateElements(List<CardElement> elements,
                                  CardTemplateContext context,
                                  PreflightReport report,
                                  String pathPrefix) {
        for (CardElement element : elements) {
            String elementPath = pathPrefix.isEmpty() ? element.getName() : pathPrefix + " > " + element.getName();
            switch (element) {
                case TextElement textElement -> validateTags(
                        "Text content in " + elementPath,
                        textElement.getText(),
                        context,
                        report
                );
                case ImageElement imageElement -> validateImageElement(imageElement, context, report, elementPath);
                case IconElement iconElement -> validateIconElement(iconElement, context, report, elementPath);
                case ConditionElement conditionElement -> validateConditionElement(conditionElement, context, report, elementPath);
                default -> {
                    // No preflight checks needed for this element type.
                }
            }

            if (element instanceof ParentCardElement parent) {
                validateElements(parent.getChildren(), context, report, elementPath);
            }
        }
    }

    private void validateConditionElement(ConditionElement element,
                                          CardTemplateContext context,
                                          PreflightReport report,
                                          String elementPath) {
        String condition = element.getCondition();
        validateTags("Condition in " + elementPath, condition, context, report);
        if (condition != null && !condition.isBlank() && !condition.contains("{{")) {
            String trimmed = condition.trim();
            if (context.headers().contains(trimmed)) {
                report.add(Severity.WARNING, "Condition in " + elementPath
                        + " uses bare CSV column '" + trimmed + "'. Use {{" + trimmed + "}} so it evaluates record data.");
            }
        }
    }

    private void validateImageElement(ImageElement element,
                                      CardTemplateContext context,
                                      PreflightReport report,
                                      String elementPath) {
        String imagePath = element.getImagePath();
        validateTags("Image path in " + elementPath, imagePath, context, report);
        if (imagePath == null || imagePath.isBlank()) {
            report.add(Severity.WARNING, "Image element " + elementPath + " has no image path.");
            return;
        }

        if (!imagePath.contains("{{")) {
            validateFileExists(imagePath, context.deckDirectory(), report,
                    "Image element " + elementPath + " points to a missing file: " + imagePath);
            return;
        }

        File csvDirectory = getCsvDirectory(context.template());
        int issueCount = 0;
        for (int i = 0; i < context.records().size(); i++) {
            String mergedPath = dataMerger.merge(imagePath, context.records().get(i));
            if (mergedPath == null || mergedPath.isBlank() || mergedPath.contains("{{")) {
                issueCount = addRepeatedIssue(report, Severity.ERROR, issueCount,
                        "Image element " + elementPath + " has an unresolved path on record " + (i + 1) + ": " + mergedPath);
                continue;
            }
            if (!resolveFile(mergedPath, csvDirectory).exists()) {
                issueCount = addRepeatedIssue(report, Severity.ERROR, issueCount,
                        "Image element " + elementPath + " points to a missing file on record " + (i + 1) + ": " + mergedPath);
            }
        }
    }

    private void validateIconElement(IconElement element,
                                     CardTemplateContext context,
                                     PreflightReport report,
                                     String elementPath) {
        validateTags("Icon value in " + elementPath, element.getValue(), context, report);

        Map<String, String> iconMap = context.template().getIconLibrary().getMappings().get(element.getMappingName());
        if (iconMap == null || iconMap.isEmpty()) {
            report.add(Severity.ERROR, "Icon element " + elementPath + " uses missing or empty icon mapping: " + element.getMappingName());
            return;
        }

        for (Map.Entry<String, String> entry : iconMap.entrySet()) {
            String key = entry.getKey();
            String path = entry.getValue();
            if (key == null || key.isEmpty()) {
                continue;
            }
            if (path == null || path.isBlank()) {
                report.add(Severity.ERROR, "Icon mapping '" + element.getMappingName() + "' has no image file for key '" + key + "'.");
            } else {
                validateFileExists(path, context.deckDirectory(), report,
                        "Icon mapping '" + element.getMappingName() + "' key '" + key + "' points to a missing file: " + path);
            }
        }

        int issueCount = 0;
        for (int i = 0; i < context.records().size(); i++) {
            String value = dataMerger.merge(element.getValue(), context.records().get(i));
            if (value == null || value.isBlank()) {
                continue;
            }
            String unmatched = findUnmatchedIconText(value, iconMap.keySet());
            if (!unmatched.isBlank()) {
                issueCount = addRepeatedIssue(report, Severity.WARNING, issueCount,
                        "Icon element " + elementPath + " has unmapped text on record " + (i + 1) + ": " + unmatched);
            }
        }
    }

    private void validateTags(String fieldName,
                              String templateText,
                              CardTemplateContext context,
                              PreflightReport report) {
        for (String tag : extractTags(templateText)) {
            if (!context.headers().contains(tag)) {
                report.add(Severity.ERROR, fieldName + " references missing CSV header: {{" + tag + "}}");
            }
        }
    }

    private List<String> extractTags(String value) {
        if (value == null || !value.contains("{{")) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        Matcher matcher = TAG_PATTERN.matcher(value);
        while (matcher.find()) {
            tags.add(matcher.group(1));
        }
        return tags;
    }

    private void validateFileExists(String path, File baseDirectory, PreflightReport report, String message) {
        if (!resolveFile(path, baseDirectory).exists()) {
            report.add(Severity.ERROR, message);
        }
    }

    private File resolveFile(String path, File baseDirectory) {
        File file = new File(path);
        if (!file.isAbsolute() && baseDirectory != null) {
            return new File(baseDirectory, path);
        }
        return file;
    }

    private File getCsvDirectory(CardTemplate template) {
        String csvPath = template.getCsvPath();
        if (csvPath == null || csvPath.isBlank()) {
            return null;
        }
        return new File(csvPath).getParentFile();
    }

    private String findUnmatchedIconText(String value, Set<String> keys) {
        List<String> sortedKeys = keys.stream()
                .filter(key -> key != null && !key.isEmpty())
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .toList();
        String remaining = value;
        StringBuilder unmatched = new StringBuilder();
        while (!remaining.isEmpty()) {
            boolean matched = false;
            for (String key : sortedKeys) {
                if (remaining.startsWith(key)) {
                    remaining = remaining.substring(key.length());
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                unmatched.append(remaining.charAt(0));
                remaining = remaining.substring(1);
            }
        }
        return unmatched.toString();
    }

    private int addRepeatedIssue(PreflightReport report, Severity severity, int issueCount, String message) {
        if (issueCount < MAX_REPEATED_ISSUES) {
            report.add(severity, message);
        } else if (issueCount == MAX_REPEATED_ISSUES) {
            report.add(severity, "Additional repeated issues omitted.");
        }
        return issueCount + 1;
    }

    public enum Severity {
        ERROR,
        WARNING
    }

    public record PreflightIssue(Severity severity, String message) { }

    public static final class PreflightReport {
        private final List<PreflightIssue> issues = new ArrayList<>();

        public List<PreflightIssue> issues() {
            return List.copyOf(issues);
        }

        public boolean hasErrors() {
            return issues.stream().anyMatch(issue -> issue.severity() == Severity.ERROR);
        }

        public boolean hasWarnings() {
            return issues.stream().anyMatch(issue -> issue.severity() == Severity.WARNING);
        }

        public boolean isClean() {
            return issues.isEmpty();
        }

        private void add(Severity severity, String message) {
            issues.add(new PreflightIssue(severity, message));
        }
    }

    private record CardTemplateContext(CardTemplate template,
                                       List<Map<String, String>> records,
                                       Set<String> headers,
                                       File deckDirectory) { }
}
