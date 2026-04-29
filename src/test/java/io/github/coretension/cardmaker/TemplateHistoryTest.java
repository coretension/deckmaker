package io.github.coretension.cardmaker;

import io.github.coretension.cardmaker.config.*;
import io.github.coretension.cardmaker.model.*;
import io.github.coretension.cardmaker.persistence.*;
import io.github.coretension.cardmaker.service.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TemplateHistoryTest {
    @Test
    public void testUndoAndRedoRestoreTemplateSnapshots() throws Exception {
        TemplateHistory history = new TemplateHistory();
        CardTemplate template = new CardTemplate();

        history.reset(template);
        template.getElements().add(new TextElement("Name"));
        history.record(template);

        assertTrue(history.canUndo());
        assertFalse(history.canRedo());

        CardTemplate undone = history.undo().orElseThrow();
        assertTrue(undone.getElements().isEmpty());
        assertFalse(history.canUndo());
        assertTrue(history.canRedo());

        CardTemplate redone = history.redo().orElseThrow();
        assertEquals(1, redone.getElements().size());
        assertEquals("Name", redone.getElements().getFirst().getName());
    }

    @Test
    public void testDuplicateSnapshotsAreIgnored() throws Exception {
        TemplateHistory history = new TemplateHistory();
        CardTemplate template = new CardTemplate();

        history.reset(template);

        assertFalse(history.record(template));
        assertFalse(history.canUndo());
    }

    @Test
    public void testSuppressedRecordingDoesNotCreateUndoStep() throws Exception {
        TemplateHistory history = new TemplateHistory();
        CardTemplate template = new CardTemplate();

        history.reset(template);
        history.suppressRecording();
        template.getElements().add(new TextElement("Suppressed"));

        assertFalse(history.record(template));
        history.resumeRecording();
        assertFalse(history.canUndo());
    }
}

