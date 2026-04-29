package io.github.coretension.cardmaker.config;

/**
 * Holds application-wide settings and state.
 */
public class AppSettings {
    /** The file path of the last deck opened by the user. */
    private String lastOpenedDeckPath;
    /** Whether professional mode (bleed and outline) is enabled. */
    private boolean professionalMode = false;

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
}
