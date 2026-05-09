package io.github.coretension.cardmaker.config;

/**
 * Holds application-wide settings and state.
 */
public class AppSettings {
    /** The file path of the last deck opened by the user. */
    private String lastOpenedDeckPath;
    /** Whether professional mode (bleed and outline) is enabled. */
    private boolean professionalMode = false;
    /** Divider position between element tree and canvas panel. */
    private double leftPanelDividerPosition = 0.22;
    /** Divider position between canvas and properties panel. */
    private double rightPanelDividerPosition = 0.78;

    /** @return the last opened deck path */
    public String getLastOpenedDeckPath() {
        return lastOpenedDeckPath;
    }

    /** @param lastOpenedDeckPath the last opened deck path to set */
    public void setLastOpenedDeckPath(String lastOpenedDeckPath) {
        this.lastOpenedDeckPath = lastOpenedDeckPath;
    }

    /** @return whether professional mode is enabled */
    public boolean isProfessionalMode() {
        return professionalMode;
    }

    /** @param professionalMode the professional mode state to set */
    public void setProfessionalMode(boolean professionalMode) {
        this.professionalMode = professionalMode;
    }

    /** @return divider position between element tree and canvas panel */
    public double getLeftPanelDividerPosition() {
        return leftPanelDividerPosition;
    }

    /** @param leftPanelDividerPosition divider position between element tree and canvas panel */
    public void setLeftPanelDividerPosition(double leftPanelDividerPosition) {
        this.leftPanelDividerPosition = leftPanelDividerPosition;
    }

    /** @return divider position between canvas and properties panel */
    public double getRightPanelDividerPosition() {
        return rightPanelDividerPosition;
    }

    /** @param rightPanelDividerPosition divider position between canvas and properties panel */
    public void setRightPanelDividerPosition(double rightPanelDividerPosition) {
        this.rightPanelDividerPosition = rightPanelDividerPosition;
    }
}
