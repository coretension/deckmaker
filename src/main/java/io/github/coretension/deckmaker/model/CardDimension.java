package io.github.coretension.deckmaker.model;

/**
 * Enumeration of standard card dimensions.
 * Provides measurements in inches, millimeters, and pixels (based on DPI).
 */
public enum CardDimension {
    /** Standard Poker card size (2.5x3.5 inches). */
    POKER(2.5, 3.5, "Standard Poker"),
    /** Standard Tarot card size (2.75x4.75 inches). */
    TAROT(2.75, 4.75, "Standard Tarot"),
    /** Standard Bridge card size (2.25x3.5 inches). */
    BRIDGE(2.25, 3.5, "Standard Bridge"),
    /** Square card size (2.5x2.5 inches). */
    SQUARE(2.5, 2.5, "Square (2.5x2.5)"),
    /** Mini card size (1.75x2.5 inches). */
    MINI(1.75, 2.5, "Mini (1.75x2.5)");

    /** Width in inches. */
    private final double widthInches;
    /** Height in inches. */
    private final double heightInches;
    /** User-friendly display name. */
    private final String displayName;
    /** Current screen DPI for pixel calculations. Default is 96.0. */
    private static double dpi = 96.0;

    /**
     * @param widthInches width in inches
     * @param heightInches height in inches
     * @param displayName display name
     */
    CardDimension(double widthInches, double heightInches, String displayName) {
        this.widthInches = widthInches;
        this.heightInches = heightInches;
        this.displayName = displayName;
    }

    /** @return current DPI value */
    public static double getDpi() { return dpi; }
    /** @param newDpi new DPI value to set */
    public static void setDpi(double newDpi) { dpi = newDpi; }

    /** @return width in pixels based on current DPI */
    public double getWidthPx() { return widthInches * dpi; }
    /** @return height in pixels based on current DPI */
    public double getHeightPx() { return heightInches * dpi; }
    
    /** @return width in millimeters */
    public double getWidthMm() { return widthInches * 25.4; }
    /** @return height in millimeters */
    public double getHeightMm() { return heightInches * 25.4; }
    
    /** @return the display name */
    public String getDisplayName() { return displayName; }

    @Override
    public String toString() { return displayName; }
}
