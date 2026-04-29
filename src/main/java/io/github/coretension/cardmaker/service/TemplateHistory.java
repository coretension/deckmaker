package io.github.coretension.cardmaker.service;

import io.github.coretension.cardmaker.model.CardTemplate;
import io.github.coretension.cardmaker.persistence.DeckStorage;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;

/**
 * Maintains bounded undo/redo history for card templates.
 */
public final class TemplateHistory {
    private static final int MAX_HISTORY_SIZE = 100;

    private final Deque<String> undoSnapshots = new ArrayDeque<>();
    private final Deque<String> redoSnapshots = new ArrayDeque<>();

    private String currentSnapshot;
    private int recordingSuppressionDepth;

    public void reset(CardTemplate template) throws IOException {
        undoSnapshots.clear();
        redoSnapshots.clear();
        currentSnapshot = DeckStorage.toJson(template);
        undoSnapshots.addLast(currentSnapshot);
    }

    public boolean record(CardTemplate template) throws IOException {
        if (isRecordingSuppressed()) {
            return false;
        }

        String snapshot = DeckStorage.toJson(template);
        if (Objects.equals(snapshot, currentSnapshot)) {
            return false;
        }

        undoSnapshots.addLast(snapshot);
        while (undoSnapshots.size() > MAX_HISTORY_SIZE) {
            undoSnapshots.removeFirst();
        }

        redoSnapshots.clear();
        currentSnapshot = snapshot;
        return true;
    }

    public Optional<CardTemplate> undo() throws IOException {
        if (!canUndo()) {
            return Optional.empty();
        }

        String current = undoSnapshots.removeLast();
        redoSnapshots.addLast(current);
        currentSnapshot = undoSnapshots.peekLast();
        return Optional.of(DeckStorage.fromJson(currentSnapshot));
    }

    public Optional<CardTemplate> redo() throws IOException {
        if (!canRedo()) {
            return Optional.empty();
        }

        String snapshot = redoSnapshots.removeLast();
        undoSnapshots.addLast(snapshot);
        currentSnapshot = snapshot;
        return Optional.of(DeckStorage.fromJson(snapshot));
    }

    public boolean canUndo() {
        return undoSnapshots.size() > 1;
    }

    public boolean canRedo() {
        return !redoSnapshots.isEmpty();
    }

    public void suppressRecording() {
        recordingSuppressionDepth++;
    }

    public void resumeRecording() {
        if (recordingSuppressionDepth > 0) {
            recordingSuppressionDepth--;
        }
    }

    private boolean isRecordingSuppressed() {
        return recordingSuppressionDepth > 0;
    }
}
