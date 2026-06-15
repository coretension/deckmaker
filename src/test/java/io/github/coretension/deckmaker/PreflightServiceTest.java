package io.github.coretension.deckmaker;

import io.github.coretension.deckmaker.model.*;
import io.github.coretension.deckmaker.service.DataMerger;
import io.github.coretension.deckmaker.service.PreflightService;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreflightServiceTest {
    private final PreflightService preflight = new PreflightService(new DataMerger());

    @Test
    void blocksProductionWhenCsvRecordsAreMissing() {
        CardTemplate template = new CardTemplate();
        template.getElements().add(new TextElement());

        PreflightService.PreflightReport report = preflight.validate(
                template,
                List.of(),
                List.of("Name"),
                null
        );

        assertTrue(report.hasErrors());
        assertTrue(hasMessage(report, "No CSV records are loaded"));
    }

    @Test
    void reportsMissingCsvHeadersInBindableFields() {
        CardTemplate template = new CardTemplate();
        TextElement text = new TextElement();
        text.setText("Name: {{MissingName}}");
        template.getElements().add(text);

        PreflightService.PreflightReport report = preflight.validate(
                template,
                List.of(Map.of("Name", "Hero")),
                List.of("Name"),
                null
        );

        assertTrue(report.hasErrors());
        assertTrue(hasMessage(report, "{{MissingName}}"));
    }

    @Test
    void reportsMissingDynamicImageFiles() {
        CardTemplate template = new CardTemplate();
        ImageElement image = new ImageElement();
        image.setImagePath("{{ImagePath}}");
        template.getElements().add(image);

        PreflightService.PreflightReport report = preflight.validate(
                template,
                List.of(Map.of("ImagePath", "missing.png")),
                List.of("ImagePath"),
                null
        );

        assertTrue(report.hasErrors());
        assertTrue(hasMessage(report, "missing file on record 1"));
    }

    @Test
    void warnsForBareConditionColumnNames() {
        CardTemplate template = new CardTemplate();
        ConditionElement condition = new ConditionElement();
        condition.setCondition("Type");
        template.getElements().add(condition);

        PreflightService.PreflightReport report = preflight.validate(
                template,
                List.of(Map.of("Type", "Creature")),
                List.of("Type"),
                null
        );

        assertFalse(report.hasErrors());
        assertTrue(report.hasWarnings());
        assertTrue(hasMessage(report, "bare CSV column"));
    }

    @Test
    void acceptsValidIconMappings() throws Exception {
        File iconFile = Files.createTempFile("deckmaker-icon", ".png").toFile();
        try {
            CardTemplate template = new CardTemplate();
            template.getIconLibrary().getMappings().put("cost", Map.of("A", iconFile.getAbsolutePath()));
            IconElement icon = new IconElement();
            icon.setMappingName("cost");
            icon.setValue("{{Cost}}");
            template.getElements().add(icon);

            PreflightService.PreflightReport report = preflight.validate(
                    template,
                    List.of(Map.of("Cost", "AAA")),
                    List.of("Cost"),
                    null
            );

            assertTrue(report.isClean());
        } finally {
            iconFile.delete();
        }
    }

    private boolean hasMessage(PreflightService.PreflightReport report, String text) {
        return report.issues().stream().anyMatch(issue -> issue.message().contains(text));
    }
}
