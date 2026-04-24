package io.github.coretension.cardmaker;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class CardMakerController {

    @FXML private TreeView<CardElement> elementTreeView;
    @FXML private Pane cardCanvas;
    @FXML private VBox propertiesPane;
    @FXML private Label recordLabel;
    @FXML private StackPane displayStack;
    @FXML private StackPane canvasContainer;
    @FXML private Label zoomLabel;
    @FXML private Label zoomToolbarLabel;
    @FXML private ToggleButton previewToolbarBtn;
    @FXML private CheckMenuItem previewMenuItem;
    @FXML private CheckMenuItem proModeMenuItem;
    @FXML private Label sizeLabel;
    @FXML private Label cursorPosLabel;
    @FXML private Label coordinatesLabel;
    @FXML private Label statusLabel;

    private final Map<CardElement, ChangeListener<Number>> xListeners = new HashMap<>();
    private final Map<CardElement, ChangeListener<Number>> yListeners = new HashMap<>();

    private CardTemplate currentTemplate = new CardTemplate();
    private List<Map<String, String>> csvData = new ArrayList<>();
    private List<String> csvHeaders = new ArrayList<>();
    private int currentRecordIndex = -1;
    private final DataMerger dataMerger = new DataMerger();
    private File currentFile;
    private File lastOpenedDirectory;
    private AppSettings settings;
    private boolean previewMode = false;
    private boolean professionalMode = false;
    private boolean showClippedContent = false;
    private double zoomLevel = 1.0;
    private CardElement copiedElement;
    private long lastCsvModificationTime = 0;
    private Stage iconLibraryStage;
    private Stage fontLibraryStage;
    private Stage dataViewerStage;

    @FXML
    public void initialize() {
        loadSettings();
        setupTemplateListeners();
        updateCanvasSize();
        updateSizeLabel();
        setupZoomListeners();
        updateTitleAndStatus();
        setupAutoSaveTimeline();
        setupCsvWatchTimeline();
        
        checkForRecovery();
        
        elementTreeView.setCellFactory(tv -> {
            TreeCell<CardElement> cell = new TreeCell<>() {
                @Override
                protected void updateItem(CardElement item, boolean empty) {
                    super.updateItem(item, empty);
                    textProperty().unbind();
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        setContextMenu(null);
                    } else {
                        final String icon = switch (item) {
                            case TextElement te -> "T";
                            case ImageElement ie -> "🖼";
                            case IconElement ice -> "⭐";
                            case ContainerElement ce -> "📦";
                            case FontElement fe -> "🔤";
                            case ConditionElement ce2 -> "❓";
                            default -> "📄";
                        };
                        
                        Label iconLabel = new Label(icon);
                        iconLabel.setMinWidth(20);
                        iconLabel.setAlignment(Pos.CENTER);
                        setGraphic(iconLabel);

                        textProperty().bind(item.nameProperty());
                        
                        opacityProperty().bind(item.enabledProperty().map(e -> e ? 1.0 : 0.5));
                        
                        ContextMenu contextMenu = new ContextMenu();
                        MenuItem enableDisableItem = new MenuItem();
                        enableDisableItem.textProperty().bind(item.enabledProperty().map(e -> e ? "Disable" : "Enable"));
                        enableDisableItem.setOnAction(e -> {
                            item.setEnabled(!item.isEnabled());
                            renderTemplate();
                        });

                        MenuItem copyItem = new MenuItem("Copy");
                        copyItem.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN));
                        copyItem.setOnAction(e -> handleCopyElement(null));

                        MenuItem pasteItem = new MenuItem("Paste");
                        pasteItem.setAccelerator(new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN));
                        pasteItem.setOnAction(e -> handlePasteElement(null));
                        pasteItem.setDisable(copiedElement == null);

                        MenuItem deleteItem = new MenuItem("Delete");
                        deleteItem.setAccelerator(new KeyCodeCombination(KeyCode.DELETE));
                        deleteItem.setOnAction(e -> {
                            elementTreeView.getSelectionModel().select(getTreeItem());
                            handleDeleteElement(null);
                        });

                        MenuItem lockUnlockItem = new MenuItem();
                        if (item instanceof ContainerElement ce) {
                            lockUnlockItem.textProperty().bind(ce.lockedProperty().map(l -> l ? "Unlock Container" : "Lock Container"));
                            lockUnlockItem.setOnAction(e -> {
                                ce.setLocked(!ce.isLocked());
                                renderTemplate();
                            });
                        }

                        contextMenu.getItems().addAll(enableDisableItem);
                        if (item instanceof ContainerElement) {
                            contextMenu.getItems().add(lockUnlockItem);
                        }
                        contextMenu.getItems().addAll(new SeparatorMenuItem(),
                                copyItem, pasteItem, new SeparatorMenuItem(), 
                                deleteItem);
                        setContextMenu(contextMenu);
                    }
                }
            };

            cell.setOnDragDetected(event -> {
                if (cell.getItem() != null) {
                    Dragboard db = cell.startDragAndDrop(TransferMode.MOVE);
                    ClipboardContent content = new ClipboardContent();
                    content.putString(cell.getItem().getName()); // Use name as a placeholder
                    db.setContent(content);
                    event.consume();
                }
            });

            cell.setOnDragOver(event -> {
                if (event.getGestureSource() != cell && event.getDragboard().hasString()) {
                    event.acceptTransferModes(TransferMode.MOVE);
                    
                    // Visual feedback for drop position
                    TreeItem<CardElement> targetItem = cell.getTreeItem();
                    int depth = 0;
                    TreeItem<CardElement> parent = targetItem;
                    while (parent != null && parent.getParent() != null) {
                        depth++;
                        parent = parent.getParent();
                    }
                    
                    boolean isTopLevel = depth == 0;
                    String color;
                    if (isTopLevel) {
                        color = "#0096C9"; // Blue for top level
                    } else {
                        // High contrast colors based on depth
                        color = switch (depth % 5) {
                            case 1 -> "#4CAF50"; // Green
                            case 2 -> "#FF9800"; // Orange
                            case 3 -> "#E91E63"; // Pink
                            case 4 -> "#9C27B0"; // Purple
                            default -> "#F44336"; // Red (depth 5, 10...)
                        };
                    }
                    int thickness = 3 + depth; // Increases with depth

                    if (event.getY() < cell.getHeight() * 0.25) {
                        cell.setStyle(String.format("-fx-border-color: %s; -fx-border-width: %d 0 0 0;", color, thickness));
                    } else if (event.getY() > cell.getHeight() * 0.75) {
                        cell.setStyle(String.format("-fx-border-color: %s; -fx-border-width: 0 0 %d 0;", color, thickness));
                    } else {
                        cell.setStyle("-fx-background-color: #E9F6FD;");
                    }
                }
                event.consume();
            });

            cell.setOnDragExited(event -> {
                cell.setStyle("");
            });

            cell.setOnDragDropped(event -> {
                Dragboard db = event.getDragboard();
                boolean success = false;
                if (db.hasString()) {
                    TreeItem<CardElement> draggedItem = elementTreeView.getSelectionModel().getSelectedItem();
                    if (draggedItem != null) {
                        CardElement draggedElement = draggedItem.getValue();
                        TreeItem<CardElement> targetItem = cell.getTreeItem();

                        if (draggedElement != null && targetItem != null) {
                            moveElement(draggedElement, targetItem, event.getY() / cell.getHeight());
                            success = true;
                        }
                    }
                }
                event.setDropCompleted(success);
                event.consume();
            });

            return cell;
        });

        elementTreeView.setOnKeyPressed(event -> {
            CardElement selected = getSelectedElement();
            if (selected == null) return;

            if (event.isControlDown() && event.getCode() == KeyCode.UP) {
                saveExpandedState(elementTreeView.getRoot());
                ObservableList<CardElement> parentList = findParentList(selected);
                if (parentList != null) {
                    int index = parentList.indexOf(selected);
                    if (index > 0) {
                        CardElement elementAbove = parentList.get(index - 1);
                        if (elementAbove instanceof ParentCardElement pe) {
                            // Move into container at the bottom
                            parentList.remove(selected);
                            pe.getChildren().add(selected);
                        } else {
                            // Swap with element above
                            parentList.remove(selected);
                            parentList.add(index - 1, selected);
                        }
                    } else {
                        // At the top of the list, move out of container if possible
                        ParentCardElement parentElement = findParentElement(selected);
                        if (parentElement != null) {
                            ObservableList<CardElement> grandparentList = findParentList(parentElement);
                            if (grandparentList != null) {
                                int parentIndex = grandparentList.indexOf(parentElement);
                                parentList.remove(selected);
                                grandparentList.add(parentIndex, selected);
                            }
                        }
                    }
                    renderTemplate();
                    selectElement(selected);
                    event.consume();
                }
            } else if (event.isControlDown() && event.getCode() == KeyCode.DOWN) {
                saveExpandedState(elementTreeView.getRoot());
                ObservableList<CardElement> parentList = findParentList(selected);
                if (parentList != null) {
                    int index = parentList.indexOf(selected);
                    if (index < parentList.size() - 1) {
                        CardElement elementBelow = parentList.get(index + 1);
                        if (elementBelow instanceof ParentCardElement pe) {
                            // Move into container at the top
                            parentList.remove(selected);
                            pe.getChildren().add(0, selected);
                        } else {
                            // Swap with element below
                            parentList.remove(selected);
                            parentList.add(index + 1, selected);
                        }
                    } else {
                        // At the bottom of the list, move out of container if possible
                        ParentCardElement parentElement = findParentElement(selected);
                        if (parentElement != null) {
                            ObservableList<CardElement> grandparentList = findParentList(parentElement);
                            if (grandparentList != null) {
                                int parentIndex = grandparentList.indexOf(parentElement);
                                parentList.remove(selected);
                                grandparentList.add(parentIndex + 1, selected);
                            }
                        }
                    }
                    renderTemplate();
                    selectElement(selected);
                    event.consume();
                }
            }
        });

        // Allow dropping on the TreeView itself (empty space) to move to root
        elementTreeView.setOnDragOver(event -> {
            if (event.getGestureSource() instanceof TreeCell && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        elementTreeView.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasString()) {
                TreeItem<CardElement> draggedItem = elementTreeView.getSelectionModel().getSelectedItem();
                if (draggedItem != null) {
                    CardElement draggedElement = draggedItem.getValue();
                    if (draggedElement != null) {
                        saveExpandedState(elementTreeView.getRoot());
                        // If it wasn't already handled by a cell, it might be a drop on empty space
                        // We move it to the end of the root elements
                        ObservableList<CardElement> sourceParentList = findParentList(draggedElement);
                        if (sourceParentList != null) {
                            sourceParentList.remove(draggedElement);
                            currentTemplate.getElements().add(draggedElement);
                            selectElement(draggedElement);
                            success = true;
                        }
                    }
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });
        
        elementTreeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            CardElement el = (newVal != null) ? newVal.getValue() : null;
            updatePropertiesPane(el);
            highlightOnCanvas(el);
            updateCoordinatesLabel(el);
        });
    }

    private void updateCoordinatesLabel(CardElement el) {
        if (el == null) {
            coordinatesLabel.setVisible(false);
            return;
        }
        coordinatesLabel.setVisible(true);
        coordinatesLabel.setText(String.format("X: %.1f, Y: %.1f", el.getX(), el.getY()));
        if (cursorPosLabel != null) cursorPosLabel.setText(String.format("Selection - X: %.1f, Y: %.1f", el.getX(), el.getY()));
        
        // Ensure we only have one listener per element to avoid leaks and duplicate updates
        if (!xListeners.containsKey(el)) {
            ChangeListener<Number> xListener = (obs, oldX, newX) -> {
                if (getSelectedElement() == el) {
                    coordinatesLabel.setText(String.format("X: %.1f, Y: %.1f", el.getX(), el.getY()));
                    if (cursorPosLabel != null) cursorPosLabel.setText(String.format("Selection - X: %.1f, Y: %.1f", el.getX(), el.getY()));
                }
            };
            el.xProperty().addListener(xListener);
            xListeners.put(el, xListener);
        }
        if (!yListeners.containsKey(el)) {
            ChangeListener<Number> yListener = (obs, oldY, newY) -> {
                if (getSelectedElement() == el) {
                    coordinatesLabel.setText(String.format("X: %.1f, Y: %.1f", el.getX(), el.getY()));
                    if (cursorPosLabel != null) cursorPosLabel.setText(String.format("Selection - X: %.1f, Y: %.1f", el.getX(), el.getY()));
                }
            };
            el.yProperty().addListener(yListener);
            yListeners.put(el, yListener);
        }
    }

    private void moveElement(CardElement element, TreeItem<CardElement> targetItem, double relativeY) {
        saveExpandedState(elementTreeView.getRoot());
        ObservableList<CardElement> sourceParentList = findParentList(element);
        if (sourceParentList == null) return;

        CardElement targetElement = targetItem.getValue();
        ObservableList<CardElement> targetParentList;
        int targetIndex;

        if (targetElement instanceof ParentCardElement pe && relativeY > 0.25 && relativeY < 0.75) {
            // Drop inside the parent element (Container or Condition)
            targetParentList = pe.getChildren();
            targetIndex = targetParentList.size(); // Add to end of children by default
        } else {
            // Drop as sibling
            targetParentList = findParentList(targetElement);
            if (targetParentList == null) return;
            targetIndex = targetParentList.indexOf(targetElement);
            if (relativeY > 0.75) {
                targetIndex++;
            }
        }

        // Avoid dropping an element onto its own descendants
        if (isDescendant(element, targetElement)) return;

        int sourceIndex = sourceParentList.indexOf(element);
        sourceParentList.remove(element);

        // Adjust index if moving within the same list from earlier to later position
        if (sourceParentList == targetParentList && targetIndex > sourceIndex) {
            targetIndex--;
        }

        if (targetIndex < 0) {
            targetParentList.add(element);
        } else if (targetIndex > targetParentList.size()) {
            targetParentList.add(element);
        } else {
            targetParentList.add(targetIndex, element);
        }
        
        selectElement(element);
    }

    private boolean isDescendant(CardElement ancestor, CardElement potentialDescendant) {
        if (ancestor == potentialDescendant) return true;
        if (ancestor instanceof ParentCardElement pe) {
            for (CardElement child : pe.getChildren()) {
                if (isDescendant(child, potentialDescendant)) return true;
            }
        }
        return false;
    }

    private final Set<CardElement> expandedElements = new HashSet<>();
    private final ListChangeListener<CardElement> nestedListener = c -> {
        saveExpandedState(elementTreeView.getRoot());
        rebuildTree();
        renderTemplate();
        saveTempDeck();
    };

    private void saveExpandedState(TreeItem<CardElement> item) {
        if (item == null) return;
        if (item.isExpanded() && item.getValue() != null) {
            expandedElements.add(item.getValue());
        } else {
            expandedElements.remove(item.getValue());
        }
        for (TreeItem<CardElement> child : item.getChildren()) {
            saveExpandedState(child);
        }
    }

    private void setupTemplateListeners() {
        rebuildTree();
        currentTemplate.getElements().addListener((ListChangeListener<CardElement>) c -> {
            saveExpandedState(elementTreeView.getRoot());
            rebuildTree();
            renderTemplate();
            saveTempDeck();
        });
        currentTemplate.getFontLibrary().fontsProperty().addListener((javafx.collections.MapChangeListener<String, FontElement>) change -> saveTempDeck());
        currentTemplate.getIconLibrary().mappingsProperty().addListener((javafx.collections.MapChangeListener<String, Map<String, String>>) change -> saveTempDeck());
    }

    /**
     * Rebuilds the element tree view from the current template.
     * Preserves selection and expansion state where possible.
     */
    private void rebuildTree() {
        CardElement selected = getSelectedElement();
        TreeItem<CardElement> root = new TreeItem<>(null);
        elementTreeView.setRoot(root);
        elementTreeView.setShowRoot(false);
        
        refreshTreeItems(root, currentTemplate.getElements());
        
        if (selected != null) {
            selectElement(selected);
        }
    }

    /**
     * Refreshes the children of a tree item based on a list of card elements.
     *
     * @param parentItem the tree item to refresh
     * @param elements   the list of card elements to display as children
     */
    private void refreshTreeItems(TreeItem<CardElement> parentItem, ObservableList<CardElement> elements) {
        parentItem.getChildren().clear();
        for (CardElement el : elements) {
            parentItem.getChildren().add(createTreeItemRecursive(el));
        }
    }

    /**
     * Creates a tree item for an element and its children recursively.
     *
     * @param el the element to create a tree item for
     * @return the created tree item
     */
    private TreeItem<CardElement> createTreeItemRecursive(CardElement el) {
        TreeItem<CardElement> item = new TreeItem<>(el);
        
        // Add listeners to common properties for auto-save
        el.xProperty().addListener((obs, old, newVal) -> saveTempDeck());
        el.yProperty().addListener((obs, old, newVal) -> saveTempDeck());
        el.nameProperty().addListener((obs, old, newVal) -> saveTempDeck());
        el.enabledProperty().addListener((obs, old, newVal) -> saveTempDeck());

        // Add subclass-specific listeners
        switch (el) {
            case TextElement te -> {
                te.textProperty().addListener((obs, old, newVal) -> saveTempDeck());
                te.fontSizeProperty().addListener((obs, old, newVal) -> saveTempDeck());
                te.colorProperty().addListener((obs, old, newVal) -> saveTempDeck());
                te.angleProperty().addListener((obs, old, newVal) -> saveTempDeck());
                te.outlineWidthProperty().addListener((obs, old, newVal) -> saveTempDeck());
                te.outlineColorProperty().addListener((obs, old, newVal) -> saveTempDeck());
                te.fontConfigNameProperty().addListener((obs, old, newVal) -> saveTempDeck());
            }
            case ImageElement ie -> {
                ie.imagePathProperty().addListener((obs, old, newVal) -> saveTempDeck());
                ie.widthProperty().addListener((obs, old, newVal) -> saveTempDeck());
                ie.heightProperty().addListener((obs, old, newVal) -> saveTempDeck());
                ie.lockAspectRatioProperty().addListener((obs, old, newVal) -> saveTempDeck());
                ie.allowOverflowProperty().addListener((obs, old, newVal) -> saveTempDeck());
            }
            case IconElement ice -> {
                ice.valueProperty().addListener((obs, old, newVal) -> saveTempDeck());
                ice.iconWidthProperty().addListener((obs, old, newVal) -> saveTempDeck());
                ice.iconHeightProperty().addListener((obs, old, newVal) -> saveTempDeck());
                ice.mappingNameProperty().addListener((obs, old, newVal) -> saveTempDeck());
            }
            case ContainerElement ce -> {
                ce.widthProperty().addListener((obs, old, newVal) -> saveTempDeck());
                ce.heightProperty().addListener((obs, old, newVal) -> saveTempDeck());
                ce.alphaProperty().addListener((obs, old, newVal) -> saveTempDeck());
                ce.backgroundColorProperty().addListener((obs, old, newVal) -> saveTempDeck());
                ce.layoutTypeProperty().addListener((obs, old, newVal) -> saveTempDeck());
                ce.alignmentProperty().addListener((obs, old, newVal) -> saveTempDeck());
                ce.spacingProperty().addListener((obs, old, newVal) -> saveTempDeck());
                ce.lockedProperty().addListener((obs, old, newVal) -> saveTempDeck());
                ce.lockAspectRatioProperty().addListener((obs, old, newVal) -> saveTempDeck());
            }
            case ConditionElement ce -> {
                ce.conditionProperty().addListener((obs, old, newVal) -> saveTempDeck());
            }
            case FontElement fe -> {
                fe.fontFamilyProperty().addListener((obs, old, newVal) -> saveTempDeck());
                fe.fontSizeProperty().addListener((obs, old, newVal) -> saveTempDeck());
                fe.fontWeightProperty().addListener((obs, old, newVal) -> saveTempDeck());
                fe.fontPostureProperty().addListener((obs, old, newVal) -> saveTempDeck());
                fe.colorProperty().addListener((obs, old, newVal) -> saveTempDeck());
                fe.angleProperty().addListener((obs, old, newVal) -> saveTempDeck());
                fe.outlineWidthProperty().addListener((obs, old, newVal) -> saveTempDeck());
                fe.outlineColorProperty().addListener((obs, old, newVal) -> saveTempDeck());
            }
            default -> {}
        }

        // Default to expanded if it's the first time or explicitly in the expanded set
        if (expandedElements.isEmpty() || expandedElements.contains(el)) {
            item.setExpanded(true);
        } else if (el instanceof ParentCardElement) {
            item.setExpanded(false);
        }
        
        if (el instanceof ParentCardElement pe) {
            // Listen for children changes to refresh this branch
            pe.getChildren().removeListener(nestedListener); 
            pe.getChildren().addListener(nestedListener);
            
            for (CardElement child : pe.getChildren()) {
                item.getChildren().add(createTreeItemRecursive(child));
            }
        }
        return item;
    }

    private void highlightOnCanvas(CardElement selectedEl) {
        // First clear all effects and hide resize handles
        clearAllHighlights(cardCanvas);

        if (selectedEl == null || previewMode) return;

        // Find the node corresponding to the selected element
        Node found = findNodeForElement(cardCanvas, selectedEl);
        if (found != null) {
            found.setEffect(new DropShadow(10, Color.BLUE));
            
            // Show resize handle if it's a container
            if (found instanceof Pane pane) {
                Node handle = pane.getChildren().stream()
                        .filter(n -> n.getStyleClass().contains("resize-handle"))
                        .findFirst()
                        .orElse(null);
                if (handle != null) {
                    handle.setVisible(true);
                    handle.toFront();
                }
            }
            
            // CRITICAL: Ensure labels in displayStack are NOT obscured by elements or their children
            if (sizeLabel != null) sizeLabel.toFront();
            if (zoomLabel != null) zoomLabel.toFront();
            if (coordinatesLabel != null) coordinatesLabel.toFront();
        }
    }

    private void clearAllHighlights(Pane pane) {
        for (Node node : pane.getChildren()) {
            node.setEffect(null);
            if (node instanceof Pane childPane) {
                childPane.getChildren().stream()
                        .filter(n -> n.getStyleClass().contains("resize-handle"))
                        .forEach(n -> n.setVisible(false));
                clearAllHighlights(childPane);
            }
        }
    }

    private Node findNodeForElement(Pane pane, CardElement el) {
        for (Node node : pane.getChildren()) {
            if (node.getProperties().get("cardElement") == el) {
                return node;
            }
            if (node instanceof Pane childPane) {
                Node found = findNodeForElement(childPane, el);
                if (found != null) return found;
            }
        }
        return null;
    }


    private void updateCanvasSize() {
        double width = currentTemplate.getDimension().getWidthPx();
        double height = currentTemplate.getDimension().getHeightPx();
        double bleedPx = professionalMode ? currentTemplate.getBleedMm() * (CardDimension.getDpi() / 25.4) : 0;
        
        cardCanvas.setMinWidth(width + 2 * bleedPx);
        cardCanvas.setMaxWidth(width + 2 * bleedPx);
        cardCanvas.setMinHeight(height + 2 * bleedPx);
        cardCanvas.setMaxHeight(height + 2 * bleedPx);
        
        updateSizeLabel();
        
        if (!showClippedContent) {
            javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(width + 2 * bleedPx, height + 2 * bleedPx);
            cardCanvas.setClip(clip);
        } else {
            cardCanvas.setClip(null);
        }
    }

    private void renderTemplate() {
        cardCanvas.getChildren().clear();
        double bleedPx = professionalMode ? currentTemplate.getBleedMm() * (CardDimension.getDpi() / 25.4) : 0;
        
        Map<String, String> currentRecord = (currentRecordIndex >= 0 && currentRecordIndex < csvData.size()) 
                ? csvData.get(currentRecordIndex) : null;

        Pane contentPane = new Pane();
        contentPane.setLayoutX(bleedPx);
        contentPane.setLayoutY(bleedPx);
        cardCanvas.getChildren().add(contentPane);

        renderElements(currentTemplate.getElements(), contentPane, currentRecord, null, ContainerElement.LayoutType.POSITIONAL, ContainerElement.Alignment.LEFT, false, false);
        
        // Add bleed guide last so it's always visible
        if (professionalMode && !previewMode) {
            javafx.scene.shape.Rectangle bleedGuide = new javafx.scene.shape.Rectangle(bleedPx, bleedPx, 
                    currentTemplate.getDimension().getWidthPx(), currentTemplate.getDimension().getHeightPx());
            bleedGuide.setFill(Color.TRANSPARENT);
            bleedGuide.setStroke(Color.RED);
            bleedGuide.setStrokeWidth(1);
            bleedGuide.getStrokeDashArray().addAll(5.0, 5.0);
            bleedGuide.setMouseTransparent(true);
            cardCanvas.getChildren().add(bleedGuide);
        }

        highlightOnCanvas(getSelectedElement());
        
        // Ensure status labels in displayStack are always on top
        if (sizeLabel != null) sizeLabel.toFront();
        if (zoomLabel != null) zoomLabel.toFront();
        if (coordinatesLabel != null) coordinatesLabel.toFront();
    }

    public void renderElementsExternal(ObservableList<CardElement> elements, Pane targetPane, Map<String, String> currentRecord, boolean forFinalDesign) {
        renderElements(elements, targetPane, currentRecord, null, ContainerElement.LayoutType.POSITIONAL, ContainerElement.Alignment.LEFT, forFinalDesign, false);
    }

    /**
     * Renders a single card to a BufferedImage at the specified DPI.
     *
     * @param record          the data record for the card
     * @param dpi             the resolution for rendering
     * @param showBleedGuide  whether to show the bleed guide (red dashed outline)
     * @return the rendered image
     */
    public BufferedImage renderCardToImage(Map<String, String> record, double dpi, boolean showBleedGuide) {
        boolean proMode = isProfessionalMode();
        double bleedMm = proMode ? currentTemplate.getBleedMm() : 0;
        double widthPx = (currentTemplate.getDimension().getWidthMm() + 2 * bleedMm) * dpi / 25.4;
        double heightPx = (currentTemplate.getDimension().getHeightMm() + 2 * bleedMm) * dpi / 25.4;

        Pane root = new Pane();
        root.setPrefSize(widthPx, heightPx);
        root.setMinSize(widthPx, heightPx);
        root.setMaxSize(widthPx, heightPx);
        root.setStyle("-fx-background-color: white;");

        // Apply clipping to the root
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(widthPx, heightPx);
        root.setClip(clip);

        double scale = dpi / CardDimension.getDpi();
        Pane contentPane = new Pane();
        double bleedPx = bleedMm * dpi / 25.4;
        contentPane.setLayoutX(bleedPx);
        contentPane.setLayoutY(bleedPx);
        contentPane.setScaleX(scale);
        contentPane.setScaleY(scale);
        // Pivot from top-left
        contentPane.setTranslateX((scale - 1) * currentTemplate.getDimension().getWidthPx() / 2);
        contentPane.setTranslateY((scale - 1) * currentTemplate.getDimension().getHeightPx() / 2);

        root.getChildren().add(contentPane);

        renderElementsExternal(currentTemplate.getElements(), contentPane, record, true);

        // Add bleed guide (outline) if requested
        if (showBleedGuide && proMode) {
            double cardWidthPx = currentTemplate.getDimension().getWidthMm() * dpi / 25.4;
            double cardHeightPx = currentTemplate.getDimension().getHeightMm() * dpi / 25.4;
            javafx.scene.shape.Rectangle bleedGuide = new javafx.scene.shape.Rectangle(bleedPx, bleedPx, cardWidthPx, cardHeightPx);
            bleedGuide.setFill(Color.TRANSPARENT);
            bleedGuide.setStroke(Color.RED);
            bleedGuide.setStrokeWidth(1);
            bleedGuide.getStrokeDashArray().addAll(5.0, 5.0);
            root.getChildren().add(bleedGuide);
        }

        new Scene(root);
        javafx.scene.image.WritableImage snapshot = root.snapshot(null, null);
        return SwingFXUtils.fromFXImage(snapshot, null);
    }

    private void renderElements(ObservableList<CardElement> elements, Pane targetPane, Map<String, String> currentRecord, FontElement inheritedFont, ContainerElement.LayoutType containerLayout, ContainerElement.Alignment containerAlignment, boolean forFinalDesign, boolean isLocked) {
        FontElement currentFont = inheritedFont;
        for (CardElement el : elements) {
            if (!el.isEnabled()) continue;
            
            if (el instanceof ConditionElement ce) {
                if (dataMerger.evaluateCondition(ce.getCondition(), currentRecord)) {
                    renderElements(ce.getChildren(), targetPane, currentRecord, currentFont, containerLayout, containerAlignment, forFinalDesign, isLocked);
                }
            } else if (el instanceof FontElement fe) {
                currentFont = fe;
            } else if (el instanceof IconElement ice && containerLayout != ContainerElement.LayoutType.POSITIONAL) {
                // Special handling for IconElement in layout containers: render individual icons as separate nodes
                List<Node> iconNodes = createIconNodes(ice, currentRecord);
                for (Node node : iconNodes) {
                    targetPane.getChildren().add(node);
                    if (!forFinalDesign) {
                        if (isLocked) {
                            node.setMouseTransparent(true);
                        } else {
                            makeDraggable(node, ice);
                        }
                    }
                    node.getProperties().put("cardElement", ice);
                }
            } else {
                Node node = createNodeForElement(el, currentRecord, currentFont, containerLayout, containerAlignment, forFinalDesign, isLocked, targetPane);
                if (node != null) {
                    targetPane.getChildren().add(node);
                    if (el instanceof ParentCardElement pe && node instanceof Pane childPane) {
                        ContainerElement.LayoutType childLayout = ContainerElement.LayoutType.POSITIONAL;
                        ContainerElement.Alignment childAlign = ContainerElement.Alignment.LEFT;
                        boolean nextLocked = isLocked;
                        
                        if (pe instanceof ContainerElement ce) {
                            childLayout = ce.getLayoutType();
                            childAlign = ce.getAlignment();
                            nextLocked |= ce.isLocked();
                        }
                        
                        renderElements(pe.getChildren(), childPane, currentRecord, currentFont, childLayout, childAlign, forFinalDesign, nextLocked);
                    }
                    if (node instanceof Pane pane) {
                        ensureResizeHandleOnTop(pane);
                    }
                }
            }
        }
    }

    private List<Node> createIconNodes(IconElement ice, Map<String, String> currentRecord) {
        List<Node> nodes = new ArrayList<>();
        String val = (currentRecord != null) ? dataMerger.merge(ice.getValue(), currentRecord) : ice.getValue();
        if (val != null) {
            Map<String, String> iconMap = currentTemplate.getIconLibrary().getMappings().get(ice.getMappingName());
            if (iconMap != null) {
                List<String> sortedKeys = iconMap.keySet().stream()
                        .filter(k -> !k.isEmpty())
                        .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                        .toList();

                String remaining = val;
                while (!remaining.isEmpty()) {
                    boolean found = false;
                    for (String key : sortedKeys) {
                        if (remaining.startsWith(key)) {
                            String iconPath = iconMap.get(key);
                            if (iconPath != null && !iconPath.isEmpty()) {
                                ImageView iv = new ImageView();
                                String baseDir = (currentFile != null) ? currentFile.getParent() : null;
                                iv.setImage(loadSafeImage(iconPath, baseDir));
                                if (iv.getImage() != null) {
                                    iv.fitWidthProperty().bind(ice.iconWidthProperty());
                                    iv.fitHeightProperty().bind(ice.iconHeightProperty());
                                    iv.setPreserveRatio(true);
                                    iv.setPickOnBounds(true);
                                    nodes.add(iv);
                                }
                            }
                            remaining = remaining.substring(key.length());
                            found = true;
                            break;
                        }
                    }
                    if (!found) remaining = remaining.substring(1);
                }
            }
        }
        return nodes;
    }

    /**
     * Creates a JavaFX Node for a given CardElement.
     */
    private Node createNodeForElement(CardElement el, Map<String, String> currentRecord, FontElement fontConfig, ContainerElement.LayoutType parentLayout, ContainerElement.Alignment parentAlignment, boolean forFinalDesign, boolean isLocked, Pane parentPane) {
        Node node = switch (el) {
            case TextElement te -> createTextNode(te, currentRecord, fontConfig, parentAlignment, parentPane);
            case ImageElement ie -> createImageNode(ie, currentRecord);
            case ContainerElement ce -> createContainerNode(ce, parentAlignment, forFinalDesign);
            case IconElement ice -> createIconFlowPane(ice, currentRecord, parentAlignment);
            default -> null;
        };

        if (node != null) {
            boolean isPositional = parentLayout == null || parentLayout == ContainerElement.LayoutType.POSITIONAL;
            if (isPositional) {
                node.layoutXProperty().bind(el.xProperty());
                if (el instanceof TextElement te) {
                    node.layoutYProperty().bind(el.yProperty().add(te.fontSizeProperty()));
                } else {
                    node.layoutYProperty().bind(el.yProperty());
                }
            }

            if (!forFinalDesign) {
                if (isLocked || (el instanceof ContainerElement ce && ce.isLocked())) {
                    node.setMouseTransparent(true);
                } else {
                    makeDraggable(node, el);
                    if (el instanceof ContainerElement ce) {
                        makeResizable((Pane) node, ce);
                    } else if (el instanceof ImageElement ie) {
                        makeResizable((Pane) node, ie);
                    }
                }
            }
            node.getProperties().put("cardElement", el);
        }
        return node;
    }

    private Text createTextNode(TextElement te, Map<String, String> currentRecord, FontElement fontConfig, ContainerElement.Alignment parentAlignment, Pane parentPane) {
        Text text = new Text();
        if (parentPane != null) {
            text.wrappingWidthProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                    () -> Math.max(0, parentPane.getWidth() - te.getX()),
                    parentPane.widthProperty(), te.xProperty()
            ));
        } else {
            // Root element, use card width
            text.wrappingWidthProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                    () -> Math.max(0, currentTemplate.getDimension().getWidthPx() - te.getX()),
                    te.xProperty()
            ));
        }
        text.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(
                () -> (currentRecord != null) ? dataMerger.merge(te.getText(), currentRecord) : te.getText(),
                te.textProperty()
        ));
        text.getStyleClass().add("text-element");

        FontElement resolvedFont = fontConfig;
        if (te.getFontConfigName() != null && !te.getFontConfigName().equals("Default")) {
            FontElement libFont = currentTemplate.getFontLibrary().getFonts().get(te.getFontConfigName());
            if (libFont != null) {
                resolvedFont = libFont;
            }
        }
        final FontElement effectiveFont = resolvedFont;

        if (effectiveFont != null) {
            text.fontProperty().bind(javafx.beans.binding.Bindings.createObjectBinding(
                    () -> Font.font(effectiveFont.getFontFamily(), effectiveFont.getFontWeight(), effectiveFont.getFontPosture(), effectiveFont.getFontSize()),
                    effectiveFont.fontFamilyProperty(), effectiveFont.fontWeightProperty(), effectiveFont.fontPostureProperty(), effectiveFont.fontSizeProperty()
            ));
            text.fillProperty().bind(javafx.beans.binding.Bindings.createObjectBinding(
                    () -> {
                        try { return Color.web(effectiveFont.getColor()); } catch (Exception e) { return Color.BLACK; }
                    }, effectiveFont.colorProperty()
            ));
            text.rotateProperty().bind(effectiveFont.angleProperty());
            text.strokeWidthProperty().bind(effectiveFont.outlineWidthProperty());
            text.strokeProperty().bind(javafx.beans.binding.Bindings.createObjectBinding(
                    () -> {
                        try { return Color.web(effectiveFont.getOutlineColor()); } catch (Exception e) { return Color.TRANSPARENT; }
                    }, effectiveFont.outlineColorProperty()
            ));
        } else {
            text.fontProperty().bind(javafx.beans.binding.Bindings.createObjectBinding(
                    () -> Font.font(te.getFontSize()), te.fontSizeProperty()
            ));
            text.fillProperty().bind(javafx.beans.binding.Bindings.createObjectBinding(
                    () -> {
                        try { return Color.web(te.getColor()); } catch (Exception e) { return Color.BLACK; }
                    }, te.colorProperty()
            ));
            text.rotateProperty().bind(te.angleProperty());
            text.strokeWidthProperty().bind(te.outlineWidthProperty());
            text.strokeProperty().bind(javafx.beans.binding.Bindings.createObjectBinding(
                    () -> {
                        try { return Color.web(te.getOutlineColor()); } catch (Exception e) { return Color.TRANSPARENT; }
                    }, te.outlineColorProperty()
            ));
        }
        text.setTextAlignment(mapAlignmentToTextAlignment(parentAlignment));
        return text;
    }

    private Node createImageNode(ImageElement ie, Map<String, String> currentRecord) {
        ImageView imageView = new ImageView();
        imageView.getStyleClass().add("image-element");

        javafx.beans.value.ChangeListener<String> pathListener = (obs, old, newVal) -> {
            String p = (currentRecord != null) ? dataMerger.merge(newVal, currentRecord) : newVal;
            if (p != null && !p.isEmpty()) {
                String baseDir = null;
                if (newVal != null && newVal.contains("{{") && currentTemplate.getCsvPath() != null) {
                    baseDir = new File(currentTemplate.getCsvPath()).getParent();
                } else if (currentFile != null) {
                    baseDir = currentFile.getParent();
                }
                imageView.setImage(loadSafeImage(p, baseDir));
            } else {
                imageView.setImage(null);
            }
        };
        ie.imagePathProperty().addListener(pathListener);
        pathListener.changed(null, null, ie.getImagePath());

        // Use a StackPane and bind its size to the actual scaled image dimensions
        // This ensures the bounding box (and dragging area) matches the visible image
        imageView.preserveRatioProperty().bind(ie.lockAspectRatioProperty());

        imageView.fitWidthProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(() -> {
            Image img = imageView.getImage();
            if (img == null || !ie.isLockAspectRatio() || img.getWidth() == 0 || img.getHeight() == 0) return ie.getWidth();
            double imgAR = img.getWidth() / img.getHeight();
            double boxAR = ie.getWidth() / ie.getHeight();
            return (imgAR > boxAR) ? ie.getWidth() : ie.getHeight() * imgAR;
        }, ie.widthProperty(), ie.heightProperty(), ie.lockAspectRatioProperty(), imageView.imageProperty()));

        imageView.fitHeightProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(() -> {
            Image img = imageView.getImage();
            if (img == null || !ie.isLockAspectRatio() || img.getWidth() == 0 || img.getHeight() == 0) return ie.getHeight();
            double imgAR = img.getWidth() / img.getHeight();
            double boxAR = ie.getWidth() / ie.getHeight();
            return (imgAR > boxAR) ? ie.getWidth() / imgAR : ie.getHeight();
        }, ie.widthProperty(), ie.heightProperty(), ie.lockAspectRatioProperty(), imageView.imageProperty()));

        Pane pane = new Pane(imageView);
        pane.getStyleClass().add("image-container");
        pane.minWidthProperty().bind(imageView.fitWidthProperty());
        pane.maxWidthProperty().bind(imageView.fitWidthProperty());
        pane.minHeightProperty().bind(imageView.fitHeightProperty());
        pane.maxHeightProperty().bind(imageView.fitHeightProperty());
        pane.prefWidthProperty().bind(imageView.fitWidthProperty());
        pane.prefHeightProperty().bind(imageView.fitHeightProperty());

        if (ie.isAllowOverflow()) {
            pane.setManaged(false);
        }
        return pane;
    }

    private Pane createContainerNode(ContainerElement ce, ContainerElement.Alignment parentAlignment, boolean forFinalDesign) {
        Pane pane = switch (ce.getLayoutType()) {
            case VERTICAL -> {
                VBox vbox = new VBox();
                vbox.setAlignment(mapAlignmentToPos(ce.getAlignment(), true));
                ce.alignmentProperty().addListener((obs, old, newVal) -> vbox.setAlignment(mapAlignmentToPos(newVal, true)));
                vbox.spacingProperty().bind(ce.spacingProperty());
                yield vbox;
            }
            case HORIZONTAL -> {
                HBox hbox = new HBox();
                hbox.setAlignment(mapAlignmentToPos(ce.getAlignment(), false));
                ce.alignmentProperty().addListener((obs, old, newVal) -> hbox.setAlignment(mapAlignmentToPos(newVal, false)));
                hbox.spacingProperty().bind(ce.spacingProperty());
                yield hbox;
            }
            case FLOW -> {
                FlowPane flowPane = new FlowPane();
                flowPane.setAlignment(mapAlignmentToPos(ce.getAlignment(), false));
                ce.alignmentProperty().addListener((obs, old, newVal) -> flowPane.setAlignment(mapAlignmentToPos(newVal, false)));
                flowPane.hgapProperty().bind(ce.spacingProperty());
                flowPane.vgapProperty().bind(ce.spacingProperty());
                yield flowPane;
            }
            case STACK -> {
                StackPane stackPane = new StackPane();
                stackPane.setAlignment(mapAlignmentToPos(ce.getAlignment(), false));
                ce.alignmentProperty().addListener((obs, old, newVal) -> stackPane.setAlignment(mapAlignmentToPos(newVal, false)));
                yield stackPane;
            }
            default -> new Pane();
        };

        pane.getStyleClass().add("container-element");
        pane.minWidthProperty().bind(ce.widthProperty());
        pane.maxWidthProperty().bind(ce.widthProperty());
        pane.minHeightProperty().bind(ce.heightProperty());
        pane.maxHeightProperty().bind(ce.heightProperty());
        pane.prefWidthProperty().bind(ce.widthProperty());
        pane.prefHeightProperty().bind(ce.heightProperty());

        updatePaneStyle(pane, ce.getBackgroundColor(), ce.getAlpha(), forFinalDesign);
        ce.backgroundColorProperty().addListener((obs, old, newVal) -> updatePaneStyle(pane, newVal, ce.getAlpha(), forFinalDesign));
        ce.alphaProperty().addListener((obs, old, newVal) -> updatePaneStyle(pane, ce.getBackgroundColor(), newVal.doubleValue(), forFinalDesign));

        if (!showClippedContent) {
            javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
            clip.widthProperty().bind(pane.widthProperty());
            clip.heightProperty().bind(pane.heightProperty());
            pane.setClip(clip);
        }
        pane.setPickOnBounds(true);
        return pane;
    }

    private FlowPane createIconFlowPane(IconElement ice, Map<String, String> currentRecord, ContainerElement.Alignment parentAlignment) {
        FlowPane flowPane = new FlowPane();
        flowPane.getStyleClass().add("icon-element");
        flowPane.setAlignment(mapAlignmentToPos(parentAlignment, false));
        flowPane.setPickOnBounds(false);
        flowPane.setMaxWidth(Region.USE_PREF_SIZE);
        flowPane.setMaxHeight(Region.USE_PREF_SIZE);

        javafx.beans.value.ChangeListener<Object> rebuildIcons = (obs, old, newVal) -> {
            flowPane.getChildren().clear();
            List<Node> iconNodes = createIconNodes(ice, currentRecord);
            flowPane.getChildren().addAll(iconNodes);
        };
        ice.valueProperty().addListener(rebuildIcons);
        ice.mappingNameProperty().addListener(rebuildIcons);
        rebuildIcons.changed(null, null, null);
        return flowPane;
    }

    private Image loadSafeImage(String path, String baseDir) {
        if (path == null || path.isEmpty()) return null;
        try {
            File file = new File(path);
            if (!file.isAbsolute() && baseDir != null) {
                file = new File(baseDir, path);
            }
            if (!file.exists()) return null;

            if (path.toLowerCase().endsWith(".svg")) {
                try {
                    BufferedImage bufferedImage = ImageIO.read(file);
                    if (bufferedImage != null) {
                        return SwingFXUtils.toFXImage(bufferedImage, null);
                    }
                } catch (Exception e) {
                    // fall back to default loader
                }
            }
            return new Image(file.toURI().toString());
        } catch (Exception e) {
            return null;
        }
    }

    private javafx.geometry.Pos mapAlignmentToPos(ContainerElement.Alignment alignment, boolean vertical) {
        if (alignment == null) alignment = ContainerElement.Alignment.LEFT;
        return switch (alignment) {
            case LEFT -> vertical ? Pos.TOP_LEFT : Pos.CENTER_LEFT;
            case CENTER -> vertical ? Pos.TOP_CENTER : Pos.CENTER;
            case RIGHT -> vertical ? Pos.TOP_RIGHT : Pos.CENTER_RIGHT;
        };
    }

    private javafx.scene.text.TextAlignment mapAlignmentToTextAlignment(ContainerElement.Alignment alignment) {
        if (alignment == null) return javafx.scene.text.TextAlignment.LEFT;
        return switch (alignment) {
            case LEFT -> javafx.scene.text.TextAlignment.LEFT;
            case CENTER -> javafx.scene.text.TextAlignment.CENTER;
            case RIGHT -> javafx.scene.text.TextAlignment.RIGHT;
        };
    }

    private void updatePaneStyle(Pane pane, String color, double alpha, boolean forFinalDesign) {
        try {
            Color c = Color.web(color);
            String alphaColor = String.format("rgba(%d, %d, %d, %.2f)", 
                (int)(c.getRed() * 255), 
                (int)(c.getGreen() * 255), 
                (int)(c.getBlue() * 255), 
                alpha);
            
            // Ensure container is visible with a subtle dashed border even if background is transparent
            StringBuilder style = new StringBuilder("-fx-background-color: " + alphaColor + "; ");
            if (!previewMode && !forFinalDesign) {
                style.append("-fx-border-color: #888888; "); // Stronger border color
                style.append("-fx-border-style: dashed; ");
                style.append("-fx-border-width: 1; ");
            }
            pane.setStyle(style.toString());
        } catch (Exception e) {
            // Ignore styling errors
        }
    }

    private void ensureResizeHandleOnTop(Pane pane) {
        pane.getChildren().stream()
                .filter(n -> n.getStyleClass().contains("resize-handle"))
                .findFirst()
                .ifPresent(Node::toFront);
    }

    private void makeResizable(Pane pane, CardElement el) {
        javafx.beans.property.DoubleProperty widthProperty;
        javafx.beans.property.DoubleProperty heightProperty;
        javafx.beans.property.BooleanProperty lockAspectRatioProperty;
        
        if (el instanceof ContainerElement ce) {
            widthProperty = ce.widthProperty();
            heightProperty = ce.heightProperty();
            lockAspectRatioProperty = ce.lockAspectRatioProperty();
        } else if (el instanceof ImageElement ie) {
            widthProperty = ie.widthProperty();
            heightProperty = ie.heightProperty();
            lockAspectRatioProperty = ie.lockAspectRatioProperty();
        } else {
            return;
        }

        javafx.scene.shape.Rectangle handle = new javafx.scene.shape.Rectangle(10, 10, Color.BLUE);
        handle.getStyleClass().add("resize-handle");
        handle.setVisible(false); // Only show when selected
        handle.setCursor(javafx.scene.Cursor.SE_RESIZE);
        handle.setManaged(false); // Do not let layout managers (VBox, HBox, etc.) position this

        // Position the handle at the bottom right of the pane
        handle.layoutXProperty().bind(pane.widthProperty().subtract(10));
        handle.layoutYProperty().bind(pane.heightProperty().subtract(10));
        
        if (pane instanceof StackPane) {
            StackPane.setAlignment(handle, Pos.TOP_LEFT);
        }
        
        handle.toFront();

        final Delta dragDelta = new Delta();
        handle.setOnMousePressed(mouseEvent -> {
            dragDelta.x = mouseEvent.getSceneX();
            dragDelta.y = mouseEvent.getSceneY();
            mouseEvent.consume();
        });

        handle.setOnMouseDragged(mouseEvent -> {
            double deltaX = mouseEvent.getSceneX() - dragDelta.x;
            double deltaY = mouseEvent.getSceneY() - dragDelta.y;

            // Apply scaling if parent is scaled
            if (handle.getParent() != null) {
                deltaX /= handle.getParent().getLocalToSceneTransform().getMxx();
                deltaY /= handle.getParent().getLocalToSceneTransform().getMyy();
            }
            
            double newWidth = widthProperty.get() + deltaX;
            double newHeight = heightProperty.get() + deltaY;

            // Constrain new dimensions
            newWidth = Math.max(10, newWidth);
            newHeight = Math.max(10, newHeight);

            // Prevent extending outside card bounds
            double cardWidth = currentTemplate.getDimension().getWidthPx();
            double cardHeight = currentTemplate.getDimension().getHeightPx();
            
            if (el.getX() + newWidth > cardWidth) {
                newWidth = cardWidth - el.getX();
            }
            if (el.getY() + newHeight > cardHeight) {
                newHeight = cardHeight - el.getY();
            }

            if (lockAspectRatioProperty.get()) {
                double ratio = widthProperty.get() / heightProperty.get();
                if (Math.abs(deltaX) > Math.abs(deltaY)) {
                    newHeight = newWidth / ratio;
                    // Re-check bounds after aspect ratio adjustment
                    if (el.getY() + newHeight > cardHeight) {
                        newHeight = cardHeight - el.getY();
                        newWidth = newHeight * ratio;
                    }
                } else {
                    newWidth = newHeight * ratio;
                    // Re-check bounds after aspect ratio adjustment
                    if (el.getX() + newWidth > cardWidth) {
                        newWidth = cardWidth - el.getX();
                        newHeight = newWidth / ratio;
                    }
                }
            }

            widthProperty.set(newWidth);
            heightProperty.set(newHeight);

            dragDelta.x = mouseEvent.getSceneX();
            dragDelta.y = mouseEvent.getSceneY();
            
            mouseEvent.consume();
        });

        handle.setOnMouseClicked(javafx.scene.input.MouseEvent::consume);

        pane.getChildren().add(handle);
    }

    private void makeDraggable(Node node, CardElement el) {
        final Delta dragDelta = new Delta();
        node.setOnMousePressed(mouseEvent -> {
            dragDelta.x = el.getX() - mouseEvent.getSceneX();
            dragDelta.y = el.getY() - mouseEvent.getSceneY();
            
            // Try to select in list; if not there, still update properties pane
            if (!selectElement(el)) {
                elementTreeView.getSelectionModel().clearSelection();
                updatePropertiesPane(el);
                highlightOnCanvas(el);
            }
            
            mouseEvent.consume();
        });
        node.setOnMouseDragged(mouseEvent -> {
            double newX = mouseEvent.getSceneX() + dragDelta.x;
            double newY = mouseEvent.getSceneY() + dragDelta.y;
            
            double cardWidth = currentTemplate.getDimension().getWidthPx();
            double cardHeight = currentTemplate.getDimension().getHeightPx();
            
            double width = node.getLayoutBounds().getWidth();
            double height = node.getLayoutBounds().getHeight();
            
            if (node instanceof Text text) {
                // For Text nodes, layoutBounds includes wrappingWidth which might be bound to the card width.
                // To get the actual visual width of the text content without the wrapping constraint,
                // we use a temporary Text node with the same properties.
                Text temp = new Text(text.getText());
                temp.setFont(text.getFont());
                temp.setStrokeWidth(text.getStrokeWidth());
                width = temp.getLayoutBounds().getWidth();
                height = temp.getLayoutBounds().getHeight();
            }
            
            // Constrain X
            if (!(el instanceof ImageElement ie && ie.isAllowOverflow())) {
                newX = Math.max(0, newX);
                if (newX + width > cardWidth) {
                    newX = Math.max(0, cardWidth - width);
                }
            }
            
            // Constrain Y
            if (!(el instanceof ImageElement ie && ie.isAllowOverflow())) {
                newY = Math.max(0, newY);
                if (newY + height > cardHeight) {
                    newY = Math.max(0, cardHeight - height);
                }
            }

            el.setX(newX);
            el.setY(newY);
            mouseEvent.consume();
        });
    }

    private static class Delta { double x, y; }

    private boolean isUpdatingOtherAxis = false;
    private final List<ChangeListener<?>> activeListeners = new ArrayList<>();
    private final List<javafx.beans.value.ObservableValue<?>> activeProperties = new ArrayList<>();

    private <T> void addManagedListener(javafx.beans.value.ObservableValue<T> property, ChangeListener<T> listener) {
        property.addListener(listener);
        activeProperties.add(property);
        activeListeners.add(listener);
    }

    private void clearActiveListeners() {
        for (int i = 0; i < activeProperties.size(); i++) {
            @SuppressWarnings("unchecked")
            javafx.beans.value.ObservableValue<Object> prop = (javafx.beans.value.ObservableValue<Object>) activeProperties.get(i);
            @SuppressWarnings("unchecked")
            ChangeListener<Object> listener = (ChangeListener<Object>) activeListeners.get(i);
            prop.removeListener(listener);
        }
        activeProperties.clear();
        activeListeners.clear();
    }

    private void addSectionLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold; -fx-padding: 5 0 2 0; -fx-text-fill: #555;");
        propertiesPane.getChildren().add(label);
    }

    private void addProperty(String label, Node control) {
        propertiesPane.getChildren().add(new Label(label));
        propertiesPane.getChildren().add(control);
    }

    private void updatePropertiesPane(CardElement el) {
        clearActiveListeners();
        propertiesPane.getChildren().clear();
        if (el == null) return;

        addSectionLabel("Element Settings");
        TextField nameField = new TextField(el.getName());
        nameField.textProperty().bindBidirectional(el.nameProperty());
        addProperty("Name", nameField);

        if (el instanceof ConditionElement ce) {
            addSectionLabel("Condition");
            TextField conditionField = new TextField(ce.getCondition());
            conditionField.textProperty().bindBidirectional(ce.conditionProperty());
            addManagedListener(ce.conditionProperty(), (obs, old, newVal) -> renderTemplate());
            addProperty("CSV Column or Expression", conditionField);
        }

        if (el instanceof TextElement te) {
            addSectionLabel("Text Content");
            TextArea textArea = new TextArea(te.getText());
            textArea.setPrefRowCount(3);
            textArea.textProperty().bindBidirectional(te.textProperty());
            addManagedListener(te.textProperty(), (obs, old, newVal) -> renderTemplate());
            addProperty("Content (use {{header}} for merge)", textArea);

            addSectionLabel("Appearance");
            ComboBox<String> fontConfigCombo = new ComboBox<>();
            fontConfigCombo.getItems().add("Default");
            fontConfigCombo.getItems().addAll(currentTemplate.getFontLibrary().getFonts().keySet());
            fontConfigCombo.setValue(te.getFontConfigName());
            te.fontConfigNameProperty().bind(fontConfigCombo.valueProperty());
            addManagedListener(te.fontConfigNameProperty(), (obs, old, newVal) -> renderTemplate());
            addProperty("Font Configuration", fontConfigCombo);

            javafx.beans.binding.BooleanBinding isNotDefault = te.fontConfigNameProperty().isNotEqualTo("Default");

            HBox sizeBox = UIUtils.createSliderWithNumericField(te.fontSizeProperty(), 8, 72);
            sizeBox.disableProperty().bind(isNotDefault);
            addManagedListener(te.fontSizeProperty(), (obs, old, newVal) -> renderTemplate());
            addProperty("Size", sizeBox);

            ColorPicker colorPicker = new ColorPicker(Color.web(te.getColor()));
            colorPicker.setStyle("-fx-color-label-visible: true;");
            colorPicker.setMaxWidth(Double.MAX_VALUE);
            colorPicker.disableProperty().bind(isNotDefault);
            colorPicker.setOnAction(e -> {
                te.setColor(UIUtils.toHexString(colorPicker.getValue()));
                renderTemplate();
            });
            addProperty("Color", colorPicker);

            addSectionLabel("Layout");
            HBox angleBox = UIUtils.createSliderWithNumericField(te.angleProperty(), -360, 360);
            angleBox.disableProperty().bind(isNotDefault);
            addManagedListener(te.angleProperty(), (obs, old, newVal) -> renderTemplate());
            addProperty("Angle", angleBox);

        } else if (el instanceof ImageElement ie) {
            addSectionLabel("Source");
            TextField pathField = new TextField(ie.getImagePath());
            pathField.textProperty().bindBidirectional(ie.imagePathProperty());
            addManagedListener(ie.imagePathProperty(), (obs, old, newVal) -> {
                if (newVal != null && !newVal.isEmpty() && !newVal.contains("{{")) {
                    try {
                        File file = new File(newVal);
                        if (file.exists()) {
                            javafx.scene.image.Image img = new javafx.scene.image.Image(file.toURI().toString());
                            if (img.getWidth() > 0 && img.getHeight() > 0) {
                                double ratio = img.getHeight() / img.getWidth();
                                isUpdatingOtherAxis = true;
                                ie.setHeight(ie.getWidth() * ratio);
                                isUpdatingOtherAxis = false;
                            }
                        }
                    } catch (Exception e) {
                        // Ignore
                    }
                }
                renderTemplate();
            });
            
            Button browseBtn = new Button("Browse...");
            browseBtn.setMaxWidth(Double.MAX_VALUE);
            browseBtn.setOnAction(e -> {
                FileChooser fileChooser = new FileChooser();
                if (lastOpenedDirectory != null && lastOpenedDirectory.exists()) {
                    fileChooser.setInitialDirectory(lastOpenedDirectory);
                }
                fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.svg"),
                    new FileChooser.ExtensionFilter("All Files", "*.*")
                );
                File file = fileChooser.showOpenDialog(propertiesPane.getScene().getWindow());
                if (file != null) {
                    lastOpenedDirectory = file.getParentFile();
                    pathField.setText(UIUtils.relativizePath(file, currentFile != null ? currentFile.getParentFile() : null));
                }
            });

            HBox widthBox = UIUtils.createSliderWithNumericField(ie.widthProperty(), 10, 500);
            widthBox.disableProperty().bind(ie.imagePathProperty().isEmpty());
            addManagedListener(ie.widthProperty(), (obs, old, newVal) -> {
                if (!isUpdatingOtherAxis && ie.isLockAspectRatio() && old.doubleValue() > 0) {
                    isUpdatingOtherAxis = true;
                    double ratio = ie.getHeight() / old.doubleValue();
                    ie.setHeight(newVal.doubleValue() * ratio);
                    isUpdatingOtherAxis = false;
                }
            });

            HBox heightBox = UIUtils.createSliderWithNumericField(ie.heightProperty(), 10, 500);
            heightBox.disableProperty().bind(ie.imagePathProperty().isEmpty());
            addManagedListener(ie.heightProperty(), (obs, old, newVal) -> {
                if (!isUpdatingOtherAxis && ie.isLockAspectRatio() && old.doubleValue() > 0) {
                    isUpdatingOtherAxis = true;
                    double ratio = ie.getWidth() / old.doubleValue();
                    ie.setWidth(newVal.doubleValue() * ratio);
                    isUpdatingOtherAxis = false;
                }
            });

            CheckBox lockAspectBox = new CheckBox("Lock Aspect Ratio");
            lockAspectBox.selectedProperty().bindBidirectional(ie.lockAspectRatioProperty());

            CheckBox allowOverflowBox = new CheckBox("Allow Overflow (goes out of bounds)");
            allowOverflowBox.selectedProperty().bindBidirectional(ie.allowOverflowProperty());
            addManagedListener(ie.allowOverflowProperty(), (obs, old, newVal) -> {
                updateCanvasSize();
                renderTemplate();
            });

            addProperty("Path (use {{header}} for merge)", new HBox(5, pathField, browseBtn));
            addProperty("Width", widthBox);
            addProperty("Height", heightBox);
            propertiesPane.getChildren().addAll(lockAspectBox, allowOverflowBox);

        } else if (el instanceof ContainerElement ce) {
            addSectionLabel("Dimensions");
            HBox widthBox = UIUtils.createSliderWithNumericField(ce.widthProperty(), 10, 500);
            addManagedListener(ce.widthProperty(), (obs, old, newVal) -> {
                if (!isUpdatingOtherAxis && ce.isLockAspectRatio() && old.doubleValue() > 0) {
                    isUpdatingOtherAxis = true;
                    double ratio = ce.getHeight() / old.doubleValue();
                    ce.setHeight(newVal.doubleValue() * ratio);
                    isUpdatingOtherAxis = false;
                }
            });

            HBox heightBox = UIUtils.createSliderWithNumericField(ce.heightProperty(), 10, 500);
            addManagedListener(ce.heightProperty(), (obs, old, newVal) -> {
                if (!isUpdatingOtherAxis && ce.isLockAspectRatio() && old.doubleValue() > 0) {
                    isUpdatingOtherAxis = true;
                    double ratio = ce.getWidth() / old.doubleValue();
                    ce.setWidth(newVal.doubleValue() * ratio);
                    isUpdatingOtherAxis = false;
                }
            });

            CheckBox lockAspectBox = new CheckBox("Lock Aspect Ratio");
            lockAspectBox.selectedProperty().bindBidirectional(ce.lockAspectRatioProperty());

            addSectionLabel("Appearance");
            HBox alphaBox = UIUtils.createSliderWithNumericField(ce.alphaProperty(), 0.0, 1.0);
            ColorPicker colorPicker = new ColorPicker(Color.TRANSPARENT);
            colorPicker.setStyle("-fx-color-label-visible: true;");
            colorPicker.setMaxWidth(Double.MAX_VALUE);
            try {
                colorPicker.setValue(Color.web(ce.getBackgroundColor()));
            } catch (Exception e) {
                // Ignore
            }
            colorPicker.setOnAction(e -> {
                ce.setBackgroundColor(UIUtils.toHexString(colorPicker.getValue()));
            });

            addSectionLabel("Layout");
            ComboBox<ContainerElement.LayoutType> layoutBox = new ComboBox<>(javafx.collections.FXCollections.observableArrayList(ContainerElement.LayoutType.values()));
            layoutBox.valueProperty().bindBidirectional(ce.layoutTypeProperty());
            addManagedListener(ce.layoutTypeProperty(), (obs, old, newVal) -> {
                if (newVal == ContainerElement.LayoutType.POSITIONAL && (old != ContainerElement.LayoutType.POSITIONAL && old != ContainerElement.LayoutType.STACK)) {
                    for (Node node : cardCanvas.lookupAll(".text-element, .image-element, .container-element")) {
                        CardElement childEl = (CardElement) node.getProperties().get("cardElement");
                        if (childEl != null && ce.getChildren().contains(childEl)) {
                            childEl.setX(node.getLayoutX());
                            childEl.setY(node.getLayoutY());
                            if (childEl instanceof TextElement te) {
                                childEl.setY(node.getLayoutY() - te.getFontSize());
                            }
                        }
                    }
                }
                renderTemplate();
            });

            ComboBox<ContainerElement.Alignment> alignBox = new ComboBox<>(javafx.collections.FXCollections.observableArrayList(ContainerElement.Alignment.values()));
            alignBox.valueProperty().bindBidirectional(ce.alignmentProperty());
            addManagedListener(ce.alignmentProperty(), (obs, old, newVal) -> renderTemplate());

            HBox spacingBox = UIUtils.createSliderWithNumericField(ce.spacingProperty(), 0, 100);
            addManagedListener(ce.spacingProperty(), (obs, old, newVal) -> renderTemplate());

            CheckBox lockedBox = new CheckBox("Locked (Children non-editable)");
            lockedBox.selectedProperty().bindBidirectional(ce.lockedProperty());
            addManagedListener(ce.lockedProperty(), (obs, old, newVal) -> renderTemplate());

            addProperty("Width", widthBox);
            addProperty("Height", heightBox);
            propertiesPane.getChildren().add(lockAspectBox);
            addProperty("Alpha", alphaBox);
            addProperty("Color", colorPicker);
            addProperty("Layout", layoutBox);
            addProperty("Alignment", alignBox);
            addProperty("Spacing", spacingBox);
            propertiesPane.getChildren().add(lockedBox);

        } else if (el instanceof IconElement ice) {
            TextField valueField = new TextField(ice.getValue());
            valueField.textProperty().bindBidirectional(ice.valueProperty());
            
            HBox iconWidthBox = UIUtils.createSliderWithNumericField(ice.iconWidthProperty(), 8, 200);
            HBox iconHeightBox = UIUtils.createSliderWithNumericField(ice.iconHeightProperty(), 8, 200);

            ComboBox<String> mappingBox = new ComboBox<>();
            mappingBox.getItems().addAll(currentTemplate.getIconLibrary().getMappings().keySet());
            mappingBox.valueProperty().bindBidirectional(ice.mappingNameProperty());

            addProperty("Value (supports {{header}})", valueField);
            addProperty("Icon Mapping", mappingBox);
            addSectionLabel("Icon Dimensions");
            addProperty("Width", iconWidthBox);
            addProperty("Height", iconHeightBox);

        } else if (el instanceof FontElement fe) {
            ComboBox<String> familyBox = new ComboBox<>(FXCollections.observableArrayList(Font.getFamilies()));
            familyBox.setEditable(true);
            familyBox.setMaxWidth(Double.MAX_VALUE);
            familyBox.valueProperty().bindBidirectional(fe.fontFamilyProperty());
            addManagedListener(fe.fontFamilyProperty(), (obs, old, newVal) -> renderTemplate());

            HBox sizeBox = UIUtils.createSliderWithNumericField(fe.fontSizeProperty(), 8, 72);
            addManagedListener(fe.fontSizeProperty(), (obs, old, newVal) -> renderTemplate());

            ChoiceBox<FontWeight> weightBox = new ChoiceBox<>(FXCollections.observableArrayList(FontWeight.values()));
            weightBox.setMaxWidth(Double.MAX_VALUE);
            weightBox.valueProperty().bindBidirectional(fe.fontWeightProperty());
            addManagedListener(fe.fontWeightProperty(), (obs, old, newVal) -> renderTemplate());

            ChoiceBox<FontPosture> postureBox = new ChoiceBox<>(FXCollections.observableArrayList(FontPosture.values()));
            postureBox.setMaxWidth(Double.MAX_VALUE);
            postureBox.valueProperty().bindBidirectional(fe.fontPostureProperty());
            addManagedListener(fe.fontPostureProperty(), (obs, old, newVal) -> renderTemplate());

            addManagedListener(fe.colorProperty(), (obs, old, newVal) -> renderTemplate());
            ColorPicker colorPicker = new ColorPicker(Color.web(fe.getColor()));
            colorPicker.setStyle("-fx-color-label-visible: true;");
            colorPicker.setMaxWidth(Double.MAX_VALUE);
            colorPicker.setOnAction(e -> {
                fe.setColor(UIUtils.toHexString(colorPicker.getValue()));
                renderTemplate();
            });

            HBox angleBox = UIUtils.createSliderWithNumericField(fe.angleProperty(), -360, 360);
            addManagedListener(fe.angleProperty(), (obs, old, newVal) -> renderTemplate());

            HBox outlineWidthBox = UIUtils.createSliderWithNumericField(fe.outlineWidthProperty(), 0, 20);
            addManagedListener(fe.outlineWidthProperty(), (obs, old, newVal) -> renderTemplate());

            ColorPicker outlineColorPicker = new ColorPicker(Color.web(fe.getOutlineColor()));
            outlineColorPicker.setStyle("-fx-color-label-visible: true;");
            outlineColorPicker.setMaxWidth(Double.MAX_VALUE);
            outlineColorPicker.setOnAction(e -> {
                fe.setOutlineColor(UIUtils.toHexString(outlineColorPicker.getValue()));
                renderTemplate();
            });

            addProperty("Family", familyBox);
            addProperty("Size", sizeBox);
            addProperty("Weight", weightBox);
            addProperty("Posture", postureBox);
            addProperty("Color", colorPicker);
            addProperty("Angle", angleBox);
            addProperty("Outline Width", outlineWidthBox);
            addProperty("Outline Color", outlineColorPicker);
        }
    }




    private void addMappingRow(Map<String, String> iconMap, String charStr, VBox container) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(2, 0, 2, 0));
        
        Label label = new Label(charStr + ":");
        label.setMinWidth(45);
        label.setStyle("-fx-font-family: monospace; -fx-font-weight: bold;");
        
        TextField pathField = new TextField(iconMap.getOrDefault(charStr, ""));
        pathField.setPromptText("Select image path...");
        HBox.setHgrow(pathField, Priority.ALWAYS);
        pathField.textProperty().addListener((obs, old, newVal) -> {
            iconMap.put(charStr, newVal == null ? "" : newVal);
            renderTemplate();
        });

        Button browseBtn = new Button("...");
        browseBtn.setTooltip(new Tooltip("Browse for image"));
        browseBtn.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            if (lastOpenedDirectory != null && lastOpenedDirectory.exists()) {
                fileChooser.setInitialDirectory(lastOpenedDirectory);
            }
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.svg"),
                    new FileChooser.ExtensionFilter("All Files", "*.*")
            );
            File file = fileChooser.showOpenDialog(propertiesPane.getScene().getWindow());
            if (file != null) {
                lastOpenedDirectory = file.getParentFile();
                pathField.setText(UIUtils.relativizePath(file, currentFile != null ? currentFile.getParentFile() : null));
            }
        });

        Button removeBtn = new Button("X");
        removeBtn.setTooltip(new Tooltip("Remove key"));
        removeBtn.setStyle("-fx-text-fill: red;");
        removeBtn.setOnAction(e -> {
            iconMap.remove(charStr);
            container.getChildren().remove(row);
            renderTemplate();
        });

        row.getChildren().addAll(label, pathField, browseBtn, removeBtn);
        container.getChildren().add(row);
    }

    @FXML
    void handleNewDeck(ActionEvent event) {
        ChoiceDialog<CardDimension> dialog = new ChoiceDialog<>(CardDimension.POKER, CardDimension.values());
        dialog.setTitle("New Deck");
        dialog.setHeaderText("Select Physical Dimensions");
        dialog.setContentText("Card Size:");
        Optional<CardDimension> result = dialog.showAndWait();
        result.ifPresent(dimension -> {
            currentTemplate = new CardTemplate();
            currentTemplate.setDimension(dimension);
            currentFile = null;
            setupTemplateListeners();
            updateCanvasSize();
            renderTemplate();
            updateTitleAndStatus();
            deleteTempDeck();
        });
    }

    @FXML
    void handleLoadCsv(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        if (lastOpenedDirectory != null && lastOpenedDirectory.exists()) {
            fileChooser.setInitialDirectory(lastOpenedDirectory);
        }
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Data Files", "*.csv", "*.ods", "*.xlsx"),
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"),
                new FileChooser.ExtensionFilter("ODS Files", "*.ods"),
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
        );
        File file = fileChooser.showOpenDialog(propertiesPane.getScene().getWindow());
        if (file != null) {
            lastOpenedDirectory = file.getParentFile();
            loadCsvFile(file);
        }
    }

    private void loadCsvFile(File file) {
        try {
            DataMerger.CsvResult result = dataMerger.loadCsv(file.getAbsolutePath());
            csvData = result.records;
            csvHeaders = result.headers;
            currentTemplate.setCsvPath(file.getAbsolutePath());
            lastCsvModificationTime = file.lastModified();
            if (!csvData.isEmpty()) {
                if (currentRecordIndex >= csvData.size()) {
                    currentRecordIndex = 0;
                } else if (currentRecordIndex < 0) {
                    currentRecordIndex = 0;
                }
            } else {
                currentRecordIndex = -1;
            }
            updateRecordLabel();
            renderTemplate();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setContentText("Error loading CSV: " + e.getMessage());
            alert.show();
        }
    }

    @FXML
    void handleAddText(ActionEvent event) {
        addElement(new TextElement());
    }

    @FXML
    void handleAddImage(ActionEvent event) {
        addElement(new ImageElement());
    }

    @FXML
    void handleAddContainer(ActionEvent event) {
        addElement(new ContainerElement());
    }

    @FXML
    void handleAddFont(ActionEvent event) {
        addElement(new FontElement());
    }

    @FXML
    void handleAddIcon(ActionEvent event) {
        addElement(new IconElement());
    }

    @FXML
    void handleAddCondition(ActionEvent event) {
        addElement(new ConditionElement());
    }

    @FXML
    public void handleManageIconLibrary(ActionEvent event) {
        if (iconLibraryStage != null && iconLibraryStage.isShowing()) {
            iconLibraryStage.toFront();
            return;
        }

        iconLibraryStage = new Stage();
        iconLibraryStage.setTitle("Manage Icon Library");

        VBox content = new VBox(15);
        content.setPadding(new Insets(10));
        content.setMinWidth(450);
        content.setPrefHeight(650);

        // Section: Select/Manage Mappings
        VBox mappingSection = new VBox(8);
        Label mappingLabel = new Label("Icon Mappings");
        mappingLabel.setStyle("-fx-font-weight: bold;");

        ListView<String> mappingList = new ListView<>();
        mappingList.getItems().addAll(currentTemplate.getIconLibrary().getMappings().keySet());
        mappingList.setPrefHeight(120);

        HBox mappingActions = new HBox(8);
        mappingActions.setAlignment(Pos.CENTER_LEFT);
        TextField newMapNameField = new TextField();
        newMapNameField.setPromptText("New Mapping Name");
        HBox.setHgrow(newMapNameField, Priority.ALWAYS);
        Button addMapBtn = new Button("Add Mapping");
        Button removeMapBtn = new Button("Remove Selected");
        mappingActions.getChildren().addAll(newMapNameField, addMapBtn, removeMapBtn);

        mappingSection.getChildren().addAll(mappingLabel, mappingList, mappingActions);

        // Section: Editor for selected mapping
        VBox editorSection = new VBox(8);
        VBox.setVgrow(editorSection, Priority.ALWAYS);
        Label editorLabel = new Label("Mapping Editor");
        editorLabel.setStyle("-fx-font-weight: bold;");

        VBox editorContainer = new VBox(10);
        editorContainer.setPadding(new Insets(5));
        ScrollPane editorScroll = new ScrollPane(editorContainer);
        editorScroll.setFitToWidth(true);
        VBox.setVgrow(editorScroll, Priority.ALWAYS);
        editorScroll.setStyle("-fx-background-color:transparent;");

        mappingList.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            editorContainer.getChildren().clear();
            if (newVal != null) {
                Map<String, String> iconMap = currentTemplate.getIconLibrary().getMappings().get(newVal);
                if (iconMap != null) {
                    HBox addKeyRow = new HBox(8);
                    addKeyRow.setAlignment(Pos.CENTER_LEFT);
                    TextField newKeyField = new TextField();
                    newKeyField.setPromptText("New Key (e.g., F, FF, HP)");
                    HBox.setHgrow(newKeyField, Priority.ALWAYS);
                    Button addKeyBtn = new Button("Add Key");
                    addKeyRow.getChildren().addAll(newKeyField, addKeyBtn);

                    VBox rowsContainer = new VBox(8);

                    addKeyBtn.setOnAction(ak -> {
                        String key = newKeyField.getText().trim();
                        if (!key.isEmpty() && !iconMap.containsKey(key)) {
                            iconMap.put(key, "");
                            addMappingRow(iconMap, key, rowsContainer);
                            newKeyField.clear();
                            renderTemplate();
                            updatePropertiesPane(getSelectedElement());
                        }
                    });

                    iconMap.keySet().stream().sorted().forEach(key -> addMappingRow(iconMap, key, rowsContainer));

                    editorContainer.getChildren().addAll(addKeyRow, new Separator(), rowsContainer);
                }
            } else {
                editorContainer.getChildren().add(new Label("Select a mapping to edit its keys."));
            }
        });

        addMapBtn.setOnAction(e -> {
            String name = newMapNameField.getText().trim();
            if (!name.isEmpty() && !currentTemplate.getIconLibrary().getMappings().containsKey(name)) {
                currentTemplate.getIconLibrary().getMappings().put(name, new java.util.HashMap<>());
                mappingList.getItems().add(name);
                mappingList.getSelectionModel().select(name);
                newMapNameField.clear();
                renderTemplate();
                updatePropertiesPane(getSelectedElement());
            }
        });

        removeMapBtn.setOnAction(e -> {
            String selected = mappingList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                currentTemplate.getIconLibrary().getMappings().remove(selected);
                mappingList.getItems().remove(selected);
                editorContainer.getChildren().clear();
                renderTemplate();
                updatePropertiesPane(getSelectedElement());
            }
        });

        // Initial state
        if (mappingList.getItems().isEmpty()) {
            editorContainer.getChildren().add(new Label("No mappings defined. Add one above."));
        } else {
            mappingList.getSelectionModel().selectFirst();
        }

        editorSection.getChildren().addAll(new Separator(), editorLabel, editorScroll);
        content.getChildren().addAll(mappingSection, editorSection);

        Scene scene = new Scene(content);
        iconLibraryStage.setScene(scene);
        iconLibraryStage.show();
    }

    @FXML
    public void handleManageFontLibrary(ActionEvent event) {
        if (fontLibraryStage != null && fontLibraryStage.isShowing()) {
            fontLibraryStage.toFront();
            return;
        }

        fontLibraryStage = new Stage();
        fontLibraryStage.setTitle("Manage Font Library");

        VBox content = new VBox(15);
        content.setPadding(new Insets(10));
        content.setMinWidth(450);
        content.setPrefHeight(650);

        // Section: Select/Manage Font Configs
        VBox fontSection = new VBox(8);
        Label fontLabel = new Label("Font Configurations");
        fontLabel.setStyle("-fx-font-weight: bold;");

        ListView<String> fontList = new ListView<>();
        fontList.getItems().addAll(currentTemplate.getFontLibrary().getFonts().keySet());
        fontList.setPrefHeight(120);

        HBox fontActions = new HBox(8);
        fontActions.setAlignment(Pos.CENTER_LEFT);
        TextField newFontNameField = new TextField();
        newFontNameField.setPromptText("New Font Config Name");
        HBox.setHgrow(newFontNameField, Priority.ALWAYS);
        Button addFontBtn = new Button("Add Font");
        Button removeFontBtn = new Button("Remove Selected");
        fontActions.getChildren().addAll(newFontNameField, addFontBtn, removeFontBtn);

        fontSection.getChildren().addAll(fontLabel, fontList, fontActions);

        // Section: Editor for selected font config
        VBox editorSection = new VBox(8);
        VBox.setVgrow(editorSection, Priority.ALWAYS);
        Label editorLabel = new Label("Font Editor");
        editorLabel.setStyle("-fx-font-weight: bold;");

        VBox editorContainer = new VBox(10);
        editorContainer.setPadding(new Insets(5));
        ScrollPane editorScroll = new ScrollPane(editorContainer);
        editorScroll.setFitToWidth(true);
        VBox.setVgrow(editorScroll, Priority.ALWAYS);
        editorScroll.setStyle("-fx-background-color:transparent;");

        fontList.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            editorContainer.getChildren().clear();
            if (newVal != null) {
                FontElement fontEl = currentTemplate.getFontLibrary().getFonts().get(newVal);
                if (fontEl != null) {
                    VBox props = new VBox(5);

                    ComboBox<String> familyBox = new ComboBox<>(FXCollections.observableArrayList(Font.getFamilies()));
                    familyBox.setValue(fontEl.getFontFamily());
                    familyBox.setMaxWidth(Double.MAX_VALUE);
                    fontEl.fontFamilyProperty().bind(familyBox.valueProperty());
                    familyBox.valueProperty().addListener((o, ov, nv) -> {
                        renderTemplate();
                        updatePropertiesPane(getSelectedElement());
                    });

                    HBox sizeBox = UIUtils.createSliderWithNumericField(fontEl.fontSizeProperty(), 8, 120);
                    fontEl.fontSizeProperty().addListener((o, ov, nv) -> {
                        renderTemplate();
                        updatePropertiesPane(getSelectedElement());
                    });

                    ComboBox<FontWeight> weightBox = new ComboBox<>(FXCollections.observableArrayList(FontWeight.values()));
                    weightBox.setValue(fontEl.getFontWeight());
                    weightBox.setMaxWidth(Double.MAX_VALUE);
                    fontEl.fontWeightProperty().bind(weightBox.valueProperty());
                    weightBox.valueProperty().addListener((o, ov, nv) -> {
                        renderTemplate();
                        updatePropertiesPane(getSelectedElement());
                    });

                    ComboBox<FontPosture> postureBox = new ComboBox<>(FXCollections.observableArrayList(FontPosture.values()));
                    postureBox.setValue(fontEl.getFontPosture());
                    postureBox.setMaxWidth(Double.MAX_VALUE);
                    fontEl.fontPostureProperty().bind(postureBox.valueProperty());
                    postureBox.valueProperty().addListener((o, ov, nv) -> {
                        renderTemplate();
                        updatePropertiesPane(getSelectedElement());
                    });

                    ColorPicker colorPicker = new ColorPicker(Color.web(fontEl.getColor()));
                    colorPicker.setStyle("-fx-color-label-visible: true;");
                    colorPicker.setMaxWidth(Double.MAX_VALUE);
                    colorPicker.setOnAction(ce -> {
                        fontEl.setColor(UIUtils.toHexString(colorPicker.getValue()));
                        renderTemplate();
                        updatePropertiesPane(getSelectedElement());
                    });

                    HBox angleBox = UIUtils.createSliderWithNumericField(fontEl.angleProperty(), -360, 360);
                    fontEl.angleProperty().addListener((o, ov, nv) -> {
                        renderTemplate();
                        updatePropertiesPane(getSelectedElement());
                    });

                    HBox outlineWidthBox = UIUtils.createSliderWithNumericField(fontEl.outlineWidthProperty(), 0, 20);
                    fontEl.outlineWidthProperty().addListener((o, ov, nv) -> {
                        renderTemplate();
                        updatePropertiesPane(getSelectedElement());
                    });

                    ColorPicker outlineColorPicker = new ColorPicker(Color.web(fontEl.getOutlineColor()));
                    outlineColorPicker.setStyle("-fx-color-label-visible: true;");
                    outlineColorPicker.setMaxWidth(Double.MAX_VALUE);
                    outlineColorPicker.setOnAction(ce -> {
                        fontEl.setOutlineColor(UIUtils.toHexString(outlineColorPicker.getValue()));
                        renderTemplate();
                        updatePropertiesPane(getSelectedElement());
                    });

                    props.getChildren().addAll(
                        new Label("Font Family"), familyBox,
                        new Label("Font Size"), sizeBox,
                        new Label("Font Weight"), weightBox,
                        new Label("Font Posture"), postureBox,
                        new Label("Color"), colorPicker,
                        new Label("Angle"), angleBox,
                        new Label("Outline Width"), outlineWidthBox,
                        new Label("Outline Color"), outlineColorPicker
                    );
                    editorContainer.getChildren().add(props);
                }
            } else {
                editorContainer.getChildren().add(new Label("Select a font config to edit."));
            }
        });

        addFontBtn.setOnAction(e -> {
            String name = newFontNameField.getText().trim();
            if (!name.isEmpty() && !currentTemplate.getFontLibrary().getFonts().containsKey(name)) {
                currentTemplate.getFontLibrary().getFonts().put(name, new FontElement(name));
                fontList.getItems().add(name);
                fontList.getSelectionModel().select(name);
                newFontNameField.clear();
                renderTemplate();
                updatePropertiesPane(getSelectedElement());
            }
        });

        removeFontBtn.setOnAction(e -> {
            String selected = fontList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                currentTemplate.getFontLibrary().getFonts().remove(selected);
                fontList.getItems().remove(selected);
                editorContainer.getChildren().clear();
                renderTemplate();
                updatePropertiesPane(getSelectedElement());
            }
        });

        // Initial state
        if (fontList.getItems().isEmpty()) {
            editorContainer.getChildren().add(new Label("No font configs defined. Add one above."));
        } else {
            fontList.getSelectionModel().selectFirst();
        }

        editorSection.getChildren().addAll(new Separator(), editorLabel, editorScroll);
        content.getChildren().addAll(fontSection, editorSection);

        Scene scene = new Scene(content);
        fontLibraryStage.setScene(scene);
        fontLibraryStage.show();
    }

    private void addElement(CardElement newEl) {
        CardElement selected = getSelectedElement();
        if (selected instanceof ParentCardElement pe) {
            pe.getChildren().add(newEl);
        } else if (selected != null) {
            ObservableList<CardElement> parentList = findParentList(selected);
            if (parentList != null) {
                int index = parentList.indexOf(selected);
                parentList.add(index + 1, newEl);
            } else {
                currentTemplate.getElements().add(newEl);
            }
        } else {
            currentTemplate.getElements().add(newEl);
        }
        selectElement(newEl);
    }

    private CardElement getSelectedElement() {
        TreeItem<CardElement> selectedItem = elementTreeView.getSelectionModel().getSelectedItem();
        return (selectedItem != null) ? selectedItem.getValue() : null;
    }

    private boolean selectElement(CardElement el) {
        if (el == null) return false;
        TreeItem<CardElement> item = findTreeItem(elementTreeView.getRoot(), el);
        if (item != null) {
            elementTreeView.getSelectionModel().select(item);
            return true;
        }
        return false;
    }

    private TreeItem<CardElement> findTreeItem(TreeItem<CardElement> root, CardElement el) {
        if (root == null) return null;
        if (root.getValue() == el) return root;
        for (TreeItem<CardElement> child : root.getChildren()) {
            TreeItem<CardElement> found = findTreeItem(child, el);
            if (found != null) return found;
        }
        return null;
    }

    private void moveSelectedElement(java.util.function.BiConsumer<ObservableList<CardElement>, CardElement> moveAction) {
        CardElement selected = getSelectedElement();
        if (selected != null) {
            saveExpandedState(elementTreeView.getRoot());
            ObservableList<CardElement> parentList = findParentList(selected);
            if (parentList != null) {
                moveAction.accept(parentList, selected);
                // No need to call renderTemplate() here as nestedListener handles it
                selectElement(selected);
            }
        }
    }

    private ParentCardElement findParentElement(CardElement el) {
        return findParentElementRecursive(currentTemplate.getElements(), el);
    }

    private ParentCardElement findParentElementRecursive(ObservableList<CardElement> elements, CardElement target) {
        for (CardElement el : elements) {
            if (el instanceof ParentCardElement pe) {
                if (pe.getChildren().contains(target)) {
                    return pe;
                }
                ParentCardElement found = findParentElementRecursive(pe.getChildren(), target);
                if (found != null) return found;
            }
        }
        return null;
    }

    @FXML
    void handleDeleteElement(ActionEvent event) {
        CardElement selected = getSelectedElement();
        if (selected != null) {
            ObservableList<CardElement> parentList = findParentList(selected);
            if (parentList != null) {
                parentList.remove(selected);
            }
        }
    }

    @FXML
    void handleCopyElement(ActionEvent event) {
        CardElement selected = getSelectedElement();
        if (selected != null) {
            try {
                copiedElement = DeckStorage.clone(selected, CardElement.class);
            } catch (IOException e) {
                System.err.println("Failed to copy element: " + e.getMessage());
            }
        }
    }

    @FXML
    void handlePasteElement(ActionEvent event) {
        if (copiedElement == null) return;

        try {
            CardElement newEl = DeckStorage.clone(copiedElement, CardElement.class);
            // Offsetting the pasted element slightly so it's visible if pasted in the same place
            newEl.setX(newEl.getX() + 10);
            newEl.setY(newEl.getY() + 10);
            newEl.setName(newEl.getName() + " (Copy)");

            CardElement selected = getSelectedElement();
            if (selected instanceof ParentCardElement pe) {
                pe.getChildren().add(newEl);
            } else if (selected != null) {
                ObservableList<CardElement> parentList = findParentList(selected);
                if (parentList != null) {
                    int index = parentList.indexOf(selected);
                    parentList.add(index + 1, newEl);
                }
            } else {
                currentTemplate.getElements().add(newEl);
            }
            selectElement(newEl);
        } catch (IOException e) {
            System.err.println("Failed to paste element: " + e.getMessage());
        }
    }

    private ObservableList<CardElement> findParentList(CardElement el) {
        if (currentTemplate.getElements().contains(el)) {
            return currentTemplate.getElements();
        }
        return findParentListRecursive(currentTemplate.getElements(), el);
    }

    private ObservableList<CardElement> findParentListRecursive(ObservableList<CardElement> elements, CardElement target) {
        for (CardElement el : elements) {
            if (el instanceof ParentCardElement pe) {
                if (pe.getChildren().contains(target)) {
                    return pe.getChildren();
                }
                ObservableList<CardElement> found = findParentListRecursive(pe.getChildren(), target);
                if (found != null) return found;
            }
        }
        return null;
    }

    @FXML
    void handlePrevRecord(ActionEvent event) {
        if (currentRecordIndex > 0) {
            currentRecordIndex--;
            updateRecordLabel();
            renderTemplate();
        }
    }

    @FXML
    void handleNextRecord(ActionEvent event) {
        if (currentRecordIndex < csvData.size() - 1) {
            currentRecordIndex++;
            updateRecordLabel();
            renderTemplate();
        }
    }

    private static class TextAreaTableCell<S> extends TableCell<S, String> {
        private TextArea textArea;

        public TextAreaTableCell() {
            this.getStyleClass().add("text-area-table-cell");
        }

        @Override
        public void startEdit() {
            if (!isEditable() || !getTableView().isEditable() || !getTableColumn().isEditable()) {
                return;
            }
            super.startEdit();
            if (textArea == null) {
                createTextArea();
            }
            setText(null);
            setGraphic(textArea);
            textArea.setText(getItem());
            textArea.selectAll();
            textArea.requestFocus();
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            setText(getItem());
            setGraphic(null);
        }

        @Override
        public void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setText(null);
                setGraphic(null);
            } else {
                if (isEditing()) {
                    if (textArea != null) {
                        textArea.setText(item);
                    }
                    setText(null);
                    setGraphic(textArea);
                } else {
                    setText(item);
                    setGraphic(null);
                }
            }
        }

        private void createTextArea() {
            textArea = new TextArea(getItem());
            textArea.setWrapText(true);
            textArea.setPrefRowCount(3);
            // Limit height so it doesn't take over the whole dialog
            textArea.setMaxHeight(100);
            textArea.setOnKeyPressed(t -> {
                if (t.getCode() == KeyCode.ENTER) {
                    if (t.isControlDown()) {
                        textArea.insertText(textArea.getCaretPosition(), "\n");
                        t.consume();
                    }
                } else if (t.getCode() == KeyCode.ESCAPE) {
                    cancelEdit();
                    t.consume();
                }
            });
            textArea.focusedProperty().addListener((observable, oldValue, newValue) -> {
                if (!newValue) {
                    commitEdit(textArea.getText());
                }
            });

            // Prevent ENTER from being propagated to the Dialog if it should commit the edit
            textArea.addEventFilter(KeyEvent.KEY_PRESSED, t -> {
                if (t.getCode() == KeyCode.ENTER && !t.isControlDown()) {
                    commitEdit(textArea.getText());
                    t.consume();
                }
            });
        }
    }

    @FXML
    void handleViewData(ActionEvent event) {
        if (csvData.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("CSV Data Viewer");
            alert.setHeaderText(null);
            alert.setContentText("No CSV data loaded. Please load a CSV file first via File -> Load CSV.");
            alert.show();
            return;
        }

        if (dataViewerStage != null && dataViewerStage.isShowing()) {
            dataViewerStage.toFront();
            return;
        }

        dataViewerStage = new Stage();
        dataViewerStage.setTitle("CSV Data Viewer");

        VBox root = new VBox(10);
        root.setPadding(new Insets(10));

        Label headerLabel = new Label("Available Columns for Merge: " +
                String.join(", ", csvHeaders.stream().map(h -> "{{" + h + "}}").toList()));
        headerLabel.setWrapText(true);

        TableView<Map<String, String>> tableView = new TableView<>();
        tableView.setEditable(true);
        VBox.setVgrow(tableView, Priority.ALWAYS);

        for (String header : csvHeaders) {
            TableColumn<Map<String, String>, String> column = new TableColumn<>(header);
            column.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().get(header)));
            column.setCellFactory(tc -> new TextAreaTableCell<>());
            column.setOnEditCommit(t -> {
                t.getTableView().getItems().get(t.getTablePosition().getRow()).put(header, t.getNewValue());
                csvData = new ArrayList<>(t.getTableView().getItems());
                renderTemplate(); // Update canvas immediately
                t.getTableView().refresh();
            });
            tableView.getColumns().add(column);
        }

        ObservableList<Map<String, String>> data = FXCollections.observableArrayList(csvData);
        tableView.setItems(data);

        // Update cell on Enter
        tableView.addEventFilter(KeyEvent.KEY_PRESSED, ke -> {
            if (ke.getCode() == KeyCode.ENTER) {
                if (tableView.getEditingCell() == null) {
                    TablePosition pos = tableView.getFocusModel().getFocusedCell();
                    if (pos != null && pos.getColumn() != -1) {
                        tableView.edit(pos.getRow(), pos.getTableColumn());
                    }
                    ke.consume();
                }
            }
        });

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        Button saveButton = new Button("Save to File");
        Button closeButton = new Button("Close");

        saveButton.setOnAction(e -> {
            csvData = new ArrayList<>(data);
            if (currentTemplate.getCsvPath() != null) {
                try {
                    dataMerger.saveData(currentTemplate.getCsvPath(), csvHeaders, csvData);
                    Alert alert = new Alert(Alert.AlertType.INFORMATION, "Data saved to " + currentTemplate.getCsvPath());
                    alert.initOwner(dataViewerStage);
                    alert.show();
                } catch (IOException ex) {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to save data: " + ex.getMessage());
                    alert.initOwner(dataViewerStage);
                    alert.show();
                }
            } else {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Save Data As");
                fileChooser.getExtensionFilters().addAll(
                        new FileChooser.ExtensionFilter("CSV Files", "*.csv"),
                        new FileChooser.ExtensionFilter("ODS Files", "*.ods")
                );
                File file = fileChooser.showSaveDialog(dataViewerStage);
                if (file != null) {
                    try {
                        dataMerger.saveData(file.getAbsolutePath(), csvHeaders, csvData);
                        currentTemplate.setCsvPath(file.getAbsolutePath());
                    } catch (IOException ex) {
                        Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to save data: " + ex.getMessage());
                        alert.initOwner(dataViewerStage);
                        alert.show();
                    }
                }
            }
        });

        closeButton.setOnAction(e -> dataViewerStage.close());

        buttonBox.getChildren().addAll(saveButton, closeButton);

        root.getChildren().addAll(headerLabel, tableView, buttonBox);

        Scene scene = new Scene(root, 800, 600);
        dataViewerStage.setScene(scene);
        dataViewerStage.show();
    }

    @FXML
    void handlePrintDeck(ActionEvent event) {
        PrintService printService = new PrintService(currentTemplate, csvData, dataMerger, this);
        printService.showPrintDialog(propertiesPane.getScene().getWindow());
    }

    @FXML
    void handleExportPdf(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export to PDF");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        File file = fileChooser.showSaveDialog(propertiesPane.getScene().getWindow());
        if (file != null) {
            PdfExportService exportService = new PdfExportService(currentTemplate, csvData, this);
            try {
                exportService.exportToPdf(file);
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "PDF exported successfully!");
                alert.show();
            } catch (IOException e) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to export PDF: " + e.getMessage());
                alert.show();
            }
        }
    }

    @FXML
    void handleExportTts(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export to TTS Deck Sheet");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("JPEG Image", "*.jpg"),
                new FileChooser.ExtensionFilter("PNG Image", "*.png")
        );
        File file = fileChooser.showSaveDialog(propertiesPane.getScene().getWindow());
        if (file != null) {
            Dialog<int[]> dialog = new Dialog<>();
            dialog.setTitle("TTS Export Settings");
            dialog.setHeaderText("Configure Deck Sheet Grid (Max 10x7)");

            ButtonType exportButtonType = new ButtonType("Export", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(exportButtonType, ButtonType.CANCEL);

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 150, 10, 10));

            TextField colsField = new TextField("10");
            TextField rowsField = new TextField("7");

            grid.add(new Label("Cards Per Row:"), 0, 0);
            grid.add(colsField, 1, 0);
            grid.add(new Label("Cards Per Column:"), 0, 1);
            grid.add(rowsField, 1, 1);

            dialog.getDialogPane().setContent(grid);

            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == exportButtonType) {
                    try {
                        int cols = Integer.parseInt(colsField.getText());
                        int rows = Integer.parseInt(rowsField.getText());
                        return new int[]{cols, rows};
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
                return null;
            });

            Optional<int[]> result = dialog.showAndWait();
            result.ifPresent(res -> {
                TtsExportService exportService = new TtsExportService(currentTemplate, csvData, this);
                try {
                    exportService.exportToTts(file, res[0], res[1]);
                    Alert alert = new Alert(Alert.AlertType.INFORMATION, "TTS Deck Sheet exported successfully!");
                    alert.show();
                } catch (IOException e) {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to export TTS image: " + e.getMessage());
                    alert.show();
                }
            });
        }
    }

    @FXML
    void handleSettings(ActionEvent event) {
        Dialog<AppSettings> dialog = new Dialog<>();
        dialog.setTitle("Settings");
        dialog.setHeaderText("Global Application Settings");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField pathField = new TextField();
        pathField.setPrefWidth(300);
        pathField.setText(settings.getLastOpenedDeckPath() != null ? settings.getLastOpenedDeckPath() : "");

        Button browseButton = new Button("Browse");
        browseButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CardMaker Files", "*.cm"));
            File file = fileChooser.showOpenDialog(dialog.getDialogPane().getScene().getWindow());
            if (file != null) {
                pathField.setText(file.getAbsolutePath());
            }
        });

        TextField bleedField = new TextField(String.valueOf(currentTemplate.getBleedMm()));
        bleedField.setPrefWidth(50);

        grid.add(new Label("Last Opened/Saved Deck:"), 0, 0);
        grid.add(pathField, 1, 0);
        grid.add(browseButton, 2, 0);
        grid.add(new Label("Card Bleed (mm):"), 0, 1);
        grid.add(bleedField, 1, 1);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                settings.setLastOpenedDeckPath(pathField.getText().isEmpty() ? null : pathField.getText());
                try {
                    double bleed = Double.parseDouble(bleedField.getText());
                    currentTemplate.setBleedMm(bleed);
                } catch (NumberFormatException e) {
                    // Ignore or show alert
                }
                return settings;
            }
            return null;
        });

        Optional<AppSettings> result = dialog.showAndWait();
        result.ifPresent(s -> {
            saveSettings();
            updateCanvasSize();
            renderTemplate();
        });
    }

    @FXML
    void handleExit(ActionEvent event) {
        saveTempDeck();
        saveSettings();
        javafx.application.Platform.exit();
    }

    public boolean isProfessionalMode() {
        return professionalMode;
    }

    @FXML
    void handleToggleProfessionalMode(ActionEvent event) {
        if (event.getSource() instanceof CheckMenuItem ci) {
            professionalMode = ci.isSelected();
        }
        if (proModeMenuItem != null) proModeMenuItem.setSelected(professionalMode);
        updateCanvasSize();
        renderTemplate();
    }

    @FXML
    void handleTogglePreviewMode(ActionEvent event) {
        if (event.getSource() instanceof CheckMenuItem ci) {
            previewMode = ci.isSelected();
        } else if (event.getSource() instanceof ToggleButton tb) {
            previewMode = tb.isSelected();
        }
        
        // Sync both UI elements
        if (previewToolbarBtn != null) previewToolbarBtn.setSelected(previewMode);
        if (previewMenuItem != null) previewMenuItem.setSelected(previewMode);
        
        renderTemplate();
    }

    @FXML
    void handleToggleShowClippedContent(ActionEvent event) {
        showClippedContent = ((CheckMenuItem) event.getSource()).isSelected();
        updateCanvasSize();
        renderTemplate();
    }

    @FXML
    void handleZoomIn(ActionEvent event) {
        zoomLevel *= 1.2;
        updateZoom();
    }

    @FXML
    void handleZoomOut(ActionEvent event) {
        zoomLevel /= 1.2;
        updateZoom();
    }

    @FXML
    void handleResetZoom(ActionEvent event) {
        zoomLevel = 1.0;
        updateZoom();
    }

    private void updateZoom() {
        cardCanvas.setScaleX(zoomLevel);
        cardCanvas.setScaleY(zoomLevel);
        String zoomText = String.format("%.0f%%", zoomLevel * 100);
        zoomLabel.setText(zoomText);
        if (zoomToolbarLabel != null) {
            zoomToolbarLabel.setText(zoomText);
        }
    }

    private void updateSizeLabel() {
        if (sizeLabel != null && currentTemplate != null) {
            CardDimension d = currentTemplate.getDimension();
            double bleed = currentTemplate.getBleedMm();
            if (bleed > 0) {
                sizeLabel.setText(String.format("%.1f x %.1f mm (Bleed: %.1f mm)", d.getWidthMm(), d.getHeightMm(), bleed));
            } else {
                sizeLabel.setText(String.format("%.1f x %.1f mm", d.getWidthMm(), d.getHeightMm()));
            }
        }
    }

    private void setupZoomListeners() {
        StackPane target = displayStack != null ? displayStack : canvasContainer;
        target.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.isControlDown() || event.isShortcutDown()) {
                if (event.getDeltaY() > 0) {
                    zoomLevel *= 1.1;
                } else if (event.getDeltaY() < 0) {
                    zoomLevel /= 1.1;
                }
                updateZoom();
                event.consume();
            }
        });
    }

    private void checkForRecovery() {
        File tempFile = DeckStorage.getTempFile();
        if (tempFile.exists()) {
            File lastFile = null;
            if (settings.getLastOpenedDeckPath() != null) {
                lastFile = new File(settings.getLastOpenedDeckPath());
            }

            boolean recover = false;
            if (lastFile != null && lastFile.exists()) {
                if (tempFile.lastModified() > lastFile.lastModified()) {
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("Unsaved Changes Found");
                    alert.setHeaderText("Unsaved changes detected for '" + lastFile.getName() + "'.");
                    alert.setContentText("Do you want to recover them?");
                    Optional<ButtonType> result = alert.showAndWait();
                    if (result.isPresent() && result.get() == ButtonType.OK) {
                        recover = true;
                    }
                }
            } else {
                // No last file or it's gone, but temp exists. Maybe an unsaved new deck.
                recover = true;
            }

            if (recover) {
                loadTempDeck();
                if (lastFile != null && lastFile.exists()) {
                    currentFile = lastFile;
                    updateTitleAndStatus();
                }
            } else if (lastFile != null && lastFile.exists()) {
                loadDeck(lastFile);
            }
        } else if (settings.getLastOpenedDeckPath() != null) {
            File lastFile = new File(settings.getLastOpenedDeckPath());
            if (lastFile.exists()) {
                loadDeck(lastFile);
            }
        }
    }

    public void saveTempDeck() {
        try {
            DeckStorage.save(currentTemplate, DeckStorage.getTempFile());
        } catch (IOException e) {
            System.err.println("Failed to save temp deck: " + e.getMessage());
        }
    }

    private void loadTempDeck() {
        File tempFile = DeckStorage.getTempFile();
        if (tempFile.exists()) {
            try {
                CardTemplate template = DeckStorage.load(tempFile);
                applyTemplate(template);
            } catch (IOException e) {
                System.err.println("Failed to load temp deck: " + e.getMessage());
            }
        }
    }

    private void deleteTempDeck() {
        File tempFile = DeckStorage.getTempFile();
        if (tempFile.exists()) {
            tempFile.delete();
        }
    }

    private void loadSettings() {
        try {
            settings = DeckStorage.loadSettings();
            professionalMode = settings.isProfessionalMode();
            if (proModeMenuItem != null) proModeMenuItem.setSelected(professionalMode);
            if (settings.getLastOpenedDeckPath() != null) {
                lastOpenedDirectory = new File(settings.getLastOpenedDeckPath()).getParentFile();
            }
        } catch (IOException e) {
            System.err.println("Failed to load settings: " + e.getMessage());
            settings = new AppSettings();
        }
    }

    public void saveSettings() {
        try {
            settings.setProfessionalMode(professionalMode);
            DeckStorage.saveSettings(settings);
        } catch (IOException e) {
            System.err.println("Failed to save settings: " + e.getMessage());
        }
    }

    private void setupAutoSaveTimeline() {
        Timeline timeline = new Timeline(new KeyFrame(javafx.util.Duration.seconds(30), event -> {
            if (currentFile != null) {
                saveToFile(currentFile);
            }
        }));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    private void setupCsvWatchTimeline() {
        Timeline timeline = new Timeline(new KeyFrame(javafx.util.Duration.seconds(5), event -> {
            String csvPath = currentTemplate.getCsvPath();
            if (csvPath != null) {
                File file = new File(csvPath);
                if (file.exists()) {
                    long lastModified = file.lastModified();
                    if (lastCsvModificationTime != 0 && lastModified > lastCsvModificationTime) {
                        loadCsvFile(file);
                    }
                    lastCsvModificationTime = lastModified;
                }
            }
        }));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    private void applyTemplate(CardTemplate template) {
        this.currentTemplate = template;
        setupTemplateListeners();
        updateCanvasSize();
        if (template.getCsvPath() != null) {
            loadCsvFile(new File(template.getCsvPath()));
        }
        renderTemplate();
    }

    @FXML
    void handleSaveDeck(ActionEvent event) {
        if (currentFile == null) {
            handleSaveDeckAs(event);
        } else {
            try {
                saveToFile(currentFile);
            } catch (Exception e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setContentText("Error saving deck: " + e.getMessage());
                alert.show();
            }
        }
    }

    @FXML
    void handleSaveDeckAs(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        if (lastOpenedDirectory != null && lastOpenedDirectory.exists()) {
            fileChooser.setInitialDirectory(lastOpenedDirectory);
        }
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CardMaker Files", "*.cm"));
        File file = fileChooser.showSaveDialog(propertiesPane.getScene().getWindow());
        if (file != null) {
            lastOpenedDirectory = file.getParentFile();
            currentFile = file;
            try {
                saveToFile(file);
            } catch (Exception e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setContentText("Error saving deck: " + e.getMessage());
                alert.show();
            }
        }
    }

    @FXML
    void handleOpenDeck(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        if (lastOpenedDirectory != null && lastOpenedDirectory.exists()) {
            fileChooser.setInitialDirectory(lastOpenedDirectory);
        }
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CardMaker Files", "*.cm"));
        File file = fileChooser.showOpenDialog(propertiesPane.getScene().getWindow());
        if (file != null) {
            loadDeck(file);
        }
    }

    private void loadDeck(File file) {
        try {
            lastOpenedDirectory = file.getParentFile();
            CardTemplate template = DeckStorage.load(file);
            currentFile = file;
            settings.setLastOpenedDeckPath(file.getAbsolutePath());
            saveSettings();
            applyTemplate(template);
            updateTitleAndStatus();
            deleteTempDeck();
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setContentText("Error loading deck: " + e.getMessage());
            alert.show();
        }
    }

    private void saveToFile(File file) {
        try {
            DeckStorage.save(currentTemplate, file);
            currentFile = file;
            settings.setLastOpenedDeckPath(file.getAbsolutePath());
            saveSettings();
            updateTitleAndStatus();
            deleteTempDeck();
        } catch (IOException e) {
            // Silently fail during auto-save if needed, but for manual save show alert
            System.err.println("Error saving deck: " + e.getMessage());
        }
    }

    private void updateRecordLabel() {
        recordLabel.setText((currentRecordIndex + 1) + " / " + csvData.size());
    }

    /**
     * Updates the main window title and status bar label to show the current deck name.
     */
    private void updateTitleAndStatus() {
        String deckName = (currentFile != null) ? currentFile.getName() : "Unsaved Deck";
        statusLabel.setText("Deck: " + deckName);

        if (propertiesPane.getScene() != null && propertiesPane.getScene().getWindow() instanceof Stage stage) {
            stage.setTitle("CardMaker - " + deckName);
        }
    }
}
