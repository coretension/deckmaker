# DeckMaker User Guide

Welcome to **DeckMaker**, a powerful tool for designing custom card decks for tabletop games, collectibles, or any other project requiring rapid card prototyping. DeckMaker allows you to design a single template and automatically generate dozens or hundreds of unique cards using data from a spreadsheet.

---

## Getting Started

### Installation & Running

DeckMaker is available as a standalone application or a runnable JAR.

- **Windows:** Download and extract the `deckmaker` distribution. Run `deckmaker.exe` located in the `deckmaker` folder. No Java installation is required as it includes its own runtime.
- **Linux:** Run the `deckmaker.sh` script. This requires Java 21 or higher to be installed on your system.
- **Generic (JAR):** If you have the `deckmaker-1.0-SNAPSHOT.jar`, run it using `java -jar deckmaker-1.0-SNAPSHOT.jar`. This also requires Java 21+.

### Creating and Opening Decks
- **New Deck:** Go to `File > New Deck` to start a fresh project.
- **Open Deck:** Use `File > Open Deck` to load an existing project (`.dm` format). Legacy `.cm` files are still supported for opening.
- **Save Deck:** Save your progress with `File > Save Deck` or `Save Deck As...`. DeckMaker saves deck files with the `.dm` extension.

### The Interface
- **Left Panel (Elements):** A tree view showing all the components on your card. You can reorder them by dragging or delete them using the **Del** button.
- **Center Panel (Canvas):** A live preview of your card design.
- **Right Panel (Properties):** Customize the settings for the currently selected element.
- **Bottom Right (Data Preview):** Navigate through your spreadsheet records to see how different data affects your card design.

---

## Working with Data

DeckMaker's real power comes from its ability to merge external data into your designs.

### Loading Data
- Go to `File > Load CSV` to import a `.csv` or `.ods` (OpenDocument Spreadsheet) file.
- The first row of your spreadsheet should contain headers (e.g., "Name", "Attack", "Description").
- Use `File > View CSV Data` to see a table of your imported records.

### Using Data in Elements
You can insert data into text fields or conditions by using "tags" formatted like this: `{{HeaderName}}`. For example, if your CSV has a column named "Strength", typing `Strength: {{Strength}}` in a text element will display the actual value for each card.

---

## Card Elements

Add elements to your card via the **Insert** menu. All elements share common properties:
- **Enabled:** Toggle whether an element is active and rendered on the card.
- **Position (X, Y):** Control the placement of the element (relative to its parent).
- **Name:** A descriptive label for identifying the element in the tree view.

### 📝 Text
- **Basic Text:** Static text or tags like `{{Name}}`.
- **Formatting:** Customize font size, color, and angle.
- **Outlines:** Add a border to your text for better readability against backgrounds.
- **Wrapping:** Text automatically wraps based on the width of its parent container.
- **Font Config:** Link the text to a specific **Font Configuration** (see below) to maintain consistent styling across multiple text elements.

### Image
- **Static Images:** Provide a file path to an image on your computer.
- **Dynamic Images:** Use tags like `{{ImagePath}}` to load different images for different cards based on your spreadsheet data.

### Containers
Containers help you group and organize elements.
- **Layout Types:**
  - **Positional:** Place elements anywhere inside using X and Y coordinates.
  - **Flow / Vertical / Horizontal:** Automatically align elements in a sequence.
  - **Stack:** Layer elements on top of each other, all centered within the container.
- **Spacing:** Set the pixel spacing between elements in Flow, Vertical, or Horizontal layouts.
- **Backgrounds:** Set a background color (e.g., `#FFFFFF`) and transparency (alpha) for the container area. You can also use 8-digit hex codes (e.g., `#FF000088`) for color and alpha combined.
- **Alignment:** Align child elements to the Left, Center, or Right (applicable to sequential layouts).
- **Resizing & Locking:**
  - **Lock Aspect Ratio:** Maintain the width-to-height ratio while resizing the container.
  - **Locked:** Prevent any changes to the container's properties or children.

### 🔄 Conditions
The **Condition** element allows you to show or hide parts of your card based on data. Place other elements *inside* a Condition to control their visibility.
- **Examples:**
  - `{{Type}} == Spell` (Only shows if the "Type" column is "Spell")
  - `{{Level}} != 0` (Only shows if "Level" is not zero)
  - `{{IsRare}}` (Shows if the "IsRare" column is not empty)

### 🎭 Icons
Icons allow you to display small graphics (like mana symbols or attribute icons) based on a character string.
- Use the **Manage Icon Library** (under **Insert**) to map specific characters (e.g., "A", "B", "C") to image files.
- In the **Icon Element**, set the "Value" (e.g., `{{Cost}}`). If `{{Cost}}` is "AAA", DeckMaker will display the icon mapped to "A" three times in a row.

---

## ⚙️ Advanced Features

### Font Library
Manage reusable font styles via `Insert > Manage Font Library`. Create a named style (e.g., "HeaderStyle") and apply it to multiple text elements. If you update the library, all linked text elements will update automatically.

### Professional Mode and Preview
- **Professional Mode:** (`View > Professional Mode` or `Shortcut+L`) Enables the bleed zone and a red dashed outline (bleed guide). When disabled, the canvas shows only the final card size. This setting also affects PDF export and printing—exports will include bleed only if Professional Mode is enabled.
- **Preview Mode:** (`View > Preview Mode` or `Shortcut+P`) Hides element selection highlights and the bleed guide (if Professional Mode is on) for a clean look at your final card design.
- **Clipped Content:** Use `View > Show Clipped Content` to see parts of elements that extend beyond the card or container boundaries.
- **Zooming:**
    - **Zoom In:** `View > Zoom In` or `Shortcut + =`.
    - **Zoom Out:** `View > Zoom Out` or `Shortcut + -`.
    - **Reset Zoom:** `View > Reset Zoom` or `Shortcut + 0`.
    - **Mouse Wheel:** Hold `Ctrl` (or `Cmd`) and use the mouse wheel to zoom in and out while over the card area.
    - Use the Scroll Bars to navigate the card when zoomed in.

---

## 🛠️ Building from Source

If you want to build DeckMaker yourself, you will need **Java 21** (or higher) and **Maven** installed.

### Standard Build
To compile and create the runnable shaded JAR:
```bash
mvn clean package -DskipTests
```
The resulting JAR will be in the `target/` directory.

### Creating Distributions
You can use the `dist` system property to trigger platform-specific builds using `jpackage`:

- **Windows Distribution:**
  ```bash
  mvn clean package -Ddist=win -DskipTests
  ```
  Look for the bundle in `target/dist-win/`.

- **Linux Distribution:**
  ```bash
  mvn clean package -Ddist=linux -DskipTests
  ```
  Look for the bundle in `target/dist-linux/`.

---

## 💡 Pro Tips
- **Hierarchy Matters:** Reorder elements in the tree view to change their layering. Elements at the bottom of the list appear "on top" of elements higher up.
- **Nesting:** You can place Containers inside other Containers, and Conditions inside Containers, allowing for complex and responsive card layouts.
- **Relative Paths:** If possible, keep your images in the same folder as your deck file to make the project more portable.
