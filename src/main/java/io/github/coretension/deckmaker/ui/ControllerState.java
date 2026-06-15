package io.github.coretension.deckmaker.ui;

/**
 * Mutable UI/controller state holder used by {@link DeckMakerController}.
 */
final class ControllerState {
    private boolean previewMode;
    private boolean professionalMode;
    private boolean showClippedContent;
    private boolean snapToGuides = true;
    private boolean showGrid = true;
    private double zoomLevel = 1.0;
    private boolean dirty;
    private boolean restoringPanelDividers;
    private boolean persistPanelDividers;

    boolean isPreviewMode() {
        return previewMode;
    }

    void setPreviewMode(boolean previewMode) {
        this.previewMode = previewMode;
    }

    boolean isProfessionalMode() {
        return professionalMode;
    }

    void setProfessionalMode(boolean professionalMode) {
        this.professionalMode = professionalMode;
    }

    boolean isShowClippedContent() {
        return showClippedContent;
    }

    void setShowClippedContent(boolean showClippedContent) {
        this.showClippedContent = showClippedContent;
    }

    boolean isSnapToGuides() {
        return snapToGuides;
    }

    void setSnapToGuides(boolean snapToGuides) {
        this.snapToGuides = snapToGuides;
    }

    boolean isShowGrid() {
        return showGrid;
    }

    void setShowGrid(boolean showGrid) {
        this.showGrid = showGrid;
    }

    double getZoomLevel() {
        return zoomLevel;
    }

    void setZoomLevel(double zoomLevel) {
        this.zoomLevel = zoomLevel;
    }

    boolean isDirty() {
        return dirty;
    }

    void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    boolean isRestoringPanelDividers() {
        return restoringPanelDividers;
    }

    void setRestoringPanelDividers(boolean restoringPanelDividers) {
        this.restoringPanelDividers = restoringPanelDividers;
    }

    boolean isPersistPanelDividers() {
        return persistPanelDividers;
    }

    void setPersistPanelDividers(boolean persistPanelDividers) {
        this.persistPanelDividers = persistPanelDividers;
    }
}
