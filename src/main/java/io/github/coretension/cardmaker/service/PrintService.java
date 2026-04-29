package io.github.coretension.cardmaker.service;

import io.github.coretension.cardmaker.model.CardDimension;
import io.github.coretension.cardmaker.model.CardTemplate;
import io.github.coretension.cardmaker.ui.CardMakerController;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.*;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;
import java.util.Map;

/**
 * Service responsible for printing card decks and showing print previews.
 * Handles printer configuration, page layout, and rendering cards onto printable pages.
 */
public class PrintService {

    private final CardTemplate template;
    private final List<Map<String, String>> csvData;
    private final CardMakerController controller;

    /**
     * Constructs a PrintService with necessary dependencies.
     * @param template the card template to use
     * @param csvData the data to merge into the template
     * @param dataMerger the merger service to process the template and data
     * @param controller the main controller for UI interactions
     */
    public PrintService(CardTemplate template, List<Map<String, String>> csvData, DataMerger dataMerger, CardMakerController controller) {
        this.template = template;
        this.csvData = csvData;
        this.controller = controller;
    }

    /**
     * Displays a dialog to configure printing settings and start the print job.
     * @param owner the owner window for the dialog
     */
    public void showPrintDialog(Window owner) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.initOwner(owner);
        dialog.setTitle("Print Deck");

        VBox root = new VBox(10);
        root.setPadding(new Insets(10));

        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            new Alert(Alert.AlertType.ERROR, "Could not create printer job.").show();
            return;
        }

        Printer printer = job.getPrinter();
        
        ComboBox<Paper> paperComboBox = new ComboBox<>(FXCollections.observableArrayList(
                Paper.A4, Paper.NA_LETTER, Paper.LEGAL, Paper.A3, Paper.A5
        ));
        paperComboBox.setValue(Paper.NA_LETTER);
        
        ComboBox<PageOrientation> orientationComboBox = new ComboBox<>(FXCollections.observableArrayList(
                PageOrientation.PORTRAIT, PageOrientation.LANDSCAPE
        ));
        orientationComboBox.setValue(PageOrientation.PORTRAIT);

        Label printerLabel = new Label("Printer: " + printer.getName());
        
        // Use a wrapper to dynamically update suggestions based on paper/orientation
        Label suggestLabel = new Label();
        Spinner<Integer> cardsPerRowSpinner = new Spinner<>(1, 10, 1);
        Spinner<Integer> rowsPerPageSpinner = new Spinner<>(1, 10, 1);
        
        Runnable updateLayout = () -> {
            Paper selectedPaper = paperComboBox.getValue();
            PageOrientation selectedOrientation = orientationComboBox.getValue();
            
            PageLayout layout = printer.createPageLayout(selectedPaper, 
                selectedOrientation, 18, 18, 18, 18); // 0.25 inch margins (18 points)
            
            // If DEFAULT margins fail or are zero/weird, we could try HARDWARE_MINIMUM but the issue 
            // description warns about it. Let's stick with DEFAULT or explicitly define if needed.
            
            double cardWPoints = template.getDimension().getWidthPx() * 72.0 / CardDimension.getDpi();
            double cardHPoints = template.getDimension().getHeightPx() * 72.0 / CardDimension.getDpi();
            
            int cardsPerRow = (int) (layout.getPrintableWidth() / cardWPoints);
            int rowsPerPage = (int) (layout.getPrintableHeight() / cardHPoints);
            
            cardsPerRow = Math.max(1, cardsPerRow);
            rowsPerPage = Math.max(1, rowsPerPage);
            
            suggestLabel.setText(String.format("Suggested cards per page: %d (%dx%d)\nPrintable Area: %.1f x %.1f mm", 
                (cardsPerRow * rowsPerPage), cardsPerRow, rowsPerPage, 
                layout.getPrintableWidth() / 72.0 * 25.4, layout.getPrintableHeight() / 72.0 * 25.4));
            
            cardsPerRowSpinner.getValueFactory().setValue(cardsPerRow);
            rowsPerPageSpinner.getValueFactory().setValue(rowsPerPage);
        };

        paperComboBox.setOnAction(e -> updateLayout.run());
        orientationComboBox.setOnAction(e -> updateLayout.run());
        
        updateLayout.run();

        GridPane settingsGrid = new GridPane();
        settingsGrid.setHgap(10);
        settingsGrid.setVgap(5);
        settingsGrid.add(new Label("Paper:"), 0, 0);
        settingsGrid.add(paperComboBox, 1, 0);
        settingsGrid.add(new Label("Orientation:"), 0, 1);
        settingsGrid.add(orientationComboBox, 1, 1);
        settingsGrid.add(new Label("Cards per row:"), 0, 2);
        settingsGrid.add(cardsPerRowSpinner, 1, 2);
        settingsGrid.add(new Label("Rows per page:"), 0, 3);
        settingsGrid.add(rowsPerPageSpinner, 1, 3);

        Button printButton = new Button("Print");
        printButton.setOnAction(e -> {
            dialog.close();
            PageLayout layout = printer.createPageLayout(paperComboBox.getValue(), 
                orientationComboBox.getValue(), 18, 18, 18, 18);
            performPrint(job, layout, cardsPerRowSpinner.getValue(), rowsPerPageSpinner.getValue());
        });

        Button previewButton = new Button("Preview");
        previewButton.setOnAction(e -> {
            PageLayout layout = printer.createPageLayout(paperComboBox.getValue(), 
                orientationComboBox.getValue(), 18, 18, 18, 18);
            showPreview(cardsPerRowSpinner.getValue(), rowsPerPageSpinner.getValue(), layout);
        });

        HBox buttons = new HBox(10, previewButton, printButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(printerLabel, settingsGrid, suggestLabel, buttons);

        Scene scene = new Scene(root);
        dialog.setScene(scene);
        dialog.show();
    }

    /**
     * Shows a preview of how the cards will be arranged on the printed pages.
     * @param cardsPerRow number of cards horizontally per page
     * @param rowsPerPage number of cards vertically per page
     * @param pageLayout the configured page layout
     */
    private void showPreview(int cardsPerRow, int rowsPerPage, PageLayout pageLayout) {
        Stage previewStage = new Stage();
        previewStage.setTitle("Print Preview - Page 1");

        VBox previewRoot = new VBox(10);
        previewRoot.setPadding(new Insets(10));
        previewRoot.setAlignment(Pos.CENTER);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        
        FlowPane pagesContainer = new FlowPane();
        pagesContainer.setHgap(20);
        pagesContainer.setVgap(20);
        pagesContainer.setPadding(new Insets(20));
        pagesContainer.setStyle("-fx-background-color: #555;");

        // Zoom feature
        Slider zoomSlider = new Slider(0.1, 2.0, 1.0);
        zoomSlider.setShowTickLabels(true);
        zoomSlider.setShowTickMarks(true);
        Label zoomLabel = new Label("Zoom: 100%");
        
        pagesContainer.scaleXProperty().bind(zoomSlider.valueProperty());
        pagesContainer.scaleYProperty().bind(zoomSlider.valueProperty());
        
        // Group pagesContainer to allow scaling without affecting layout of other elements in scrollPane
        Group zoomGroup = new Group(pagesContainer);
        scrollPane.setContent(zoomGroup);

        zoomSlider.valueProperty().addListener((obs, oldVal, newVal) -> zoomLabel.setText(String.format("Zoom: %.0f%%", newVal.doubleValue() * 100)));

        HBox zoomControls = new HBox(10, new Label("Zoom:"), zoomSlider, zoomLabel);
        zoomControls.setAlignment(Pos.CENTER);
        zoomControls.setPadding(new Insets(5));

        int totalCards = csvData.isEmpty() ? 1 : csvData.size();
        int cardsPerPage = cardsPerRow * rowsPerPage;
        int totalPages = (int) Math.ceil((double) totalCards / cardsPerPage);

        for (int p = 0; p < Math.min(totalPages, 5); p++) { // Limit preview to 5 pages
            Pane pagePane = createPagePane(p, cardsPerRow, rowsPerPage, pageLayout, true);
            pagesContainer.getChildren().add(pagePane);
        }

        Label infoLabel = new Label("Showing first " + Math.min(totalPages, 5) + " of " + totalPages + " pages. " +
                "Paper: " + pageLayout.getPaper().getName() + " (" + String.format("%.1f", pageLayout.getPaper().getWidth() / 72.0 * 25.4) + "x" + String.format("%.1f", pageLayout.getPaper().getHeight() / 72.0 * 25.4) + " mm)");
        previewRoot.getChildren().addAll(zoomControls, scrollPane, infoLabel);

        Scene scene = new Scene(previewRoot, 800, 600);
        previewStage.setScene(scene);
        previewStage.show();
    }

    /**
     * Creates a JavaFX Pane representing a single page of printed cards.
     * @param pageIndex the index of the page to create
     * @param cardsPerRow number of cards horizontally per page
     * @param rowsPerPage number of cards vertically per page
     * @param pageLayout the page layout configuration
     * @param forPreview true if creating for preview UI, false if for actual printing
     * @return a Pane containing the rendered page
     */
    private Pane createPagePane(int pageIndex, int cardsPerRow, int rowsPerPage, PageLayout pageLayout, boolean forPreview) {
        Pane page = new Pane();
        page.setPrefSize(pageLayout.getPaper().getWidth(), pageLayout.getPaper().getHeight());
        page.setStyle("-fx-background-color: white; -fx-border-color: black;");

        // Add a printable area indicator for preview
        Pane printableArea = new Pane();
        printableArea.setSnapToPixel(false);
        printableArea.setLayoutX(pageLayout.getLeftMargin());
        printableArea.setLayoutY(pageLayout.getTopMargin());
        printableArea.setPrefSize(pageLayout.getPrintableWidth(), pageLayout.getPrintableHeight());
        if (forPreview) {
            printableArea.setStyle("-fx-border-color: lightgray; -fx-border-style: dashed;");
        }
        page.getChildren().add(printableArea);

        int cardsPerPage = cardsPerRow * rowsPerPage;
        int startIdx = pageIndex * cardsPerPage;
        
        boolean proMode = controller.isProfessionalMode();
        double bleedPx = proMode ? template.getBleedMm() * (CardDimension.getDpi() / 25.4) : 0;
        double cardW = template.getDimension().getWidthPx() + 2 * bleedPx;
        double cardH = template.getDimension().getHeightPx() + 2 * bleedPx;
        double marginBetween = 1.0; // 1px margin between each card
        double marginEdge = 2.0; // 2px margin around the cards (on the edges of the group)
        
        double scale = 72.0 / CardDimension.getDpi();

        for (int i = 0; i < cardsPerPage; i++) {
            int cardIdx = startIdx + i;
            if (cardIdx >= (csvData.isEmpty() ? 1 : csvData.size())) break;

            Map<String, String> record = csvData.isEmpty() ? null : csvData.get(cardIdx);
            
            Pane cardPane = new Pane();
            cardPane.setSnapToPixel(false);
            cardPane.setPrefSize(cardW, cardH);
            cardPane.setMinSize(cardW, cardH);
            cardPane.setMaxSize(cardW, cardH);
            cardPane.setClip(new javafx.scene.shape.Rectangle(cardW, cardH));
            
            // Add a thin dark 1-pixel border around the card to assist with cutting
            cardPane.setStyle("-fx-border-color: #333; -fx-border-width: 0.5; -fx-background-color: white;");
            
            if (forPreview) {
                cardPane.setEffect(new DropShadow(5, Color.BLACK));
            }

            // Render card content
            Pane contentPane = new Pane();
            contentPane.setSnapToPixel(false);
            contentPane.setLayoutX(bleedPx);
            contentPane.setLayoutY(bleedPx);
            cardPane.getChildren().add(contentPane);
            controller.renderElementsExternal(template.getElements(), contentPane, record, true);
            
            // Apply scale to convert from UI DPI to 72 DPI (printing points)
            cardPane.getTransforms().add(new Scale(scale, scale));
            
            int row = i / cardsPerRow;
            int col = i % cardsPerRow;
            
            // Add marginBetween between cards and marginEdge around the group
            double x = (marginEdge + col * (cardW + marginBetween)) * scale;
            double y = (marginEdge + row * (cardH + marginBetween)) * scale;
            
            cardPane.setLayoutX(x);
            cardPane.setLayoutY(y);
            
            printableArea.getChildren().add(cardPane);
        }

        return page;
    }

    /**
     * Executes the actual printing process.
     * @param job the printer job to use
     * @param pageLayout the configured page layout
     * @param cardsPerRow number of cards horizontally per page
     * @param rowsPerPage number of cards vertically per page
     */
    private void performPrint(PrinterJob job, PageLayout pageLayout, int cardsPerRow, int rowsPerPage) {
        int totalCards = csvData.isEmpty() ? 1 : csvData.size();
        int cardsPerPage = cardsPerRow * rowsPerPage;
        int totalPages = (int) Math.ceil((double) totalCards / cardsPerPage);

        boolean success = true;
        for (int p = 0; p < totalPages; p++) {
            Pane pagePane = createPagePane(p, cardsPerRow, rowsPerPage, pageLayout, false);
            // JavaFX printing often needs the node to be in a scene/visible to render correctly if it uses CSS
            new javafx.scene.Scene(new javafx.scene.Group(pagePane));
            success = job.printPage(pageLayout, pagePane);
            if (!success) break;
        }

        if (success) {
            job.endJob();
        } else {
            new Alert(Alert.AlertType.ERROR, "Printing failed.").show();
        }
    }
}
