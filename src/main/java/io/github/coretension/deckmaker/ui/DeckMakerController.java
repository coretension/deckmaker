package io.github.coretension.deckmaker.ui;

import io.github.coretension.deckmaker.config.AppSettings;
import io.github.coretension.deckmaker.model.*;
import io.github.coretension.deckmaker.persistence.DeckStorage;
import io.github.coretension.deckmaker.service.*;
import javafx.application.Platform;
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
import javafx.geometry.Side;
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
import javafx.stage.Window;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class DeckMakerController {
    private static final String DECK_EXTENSION = ".dm";
    private static final String LEGACY_DECK_EXTENSION = ".cm";

    @FXML private TreeView<CardElement> elementTreeView;
    @FXML private Pane cardCanvas;
    @FXML private VBox propertiesPane;
    @FXML private Label recordLabel;
    @FXML private StackPane displayStack;
    @FXML private StackPane canvasContainer;
    @FXML private Label zoomLabel;
    @FXML private Label zoomToolbarLabel;
    @FXML private SplitPane mainSplitPane;
    @FXML private ToggleButton previewToolbarBtn;
    @FXML private ToggleButton snapToolbarBtn;
    @FXML private ToggleButton gridToolbarBtn;
    @FXML private CheckMenuItem previewMenuItem;
    @FXML private CheckMenuItem proModeMenuItem;
    @FXML private CheckMenuItem snapMenuItem;
    @FXML private CheckMenuItem gridMenuItem;
    @FXML private MenuItem undoMenuItem;
    @FXML private MenuItem redoMenuItem;
    @FXML private Label sizeLabel;
    @FXML private Label cursorPosLabel;
    @FXML private Label coordinatesLabel;
    @FXML private Label statusLabel;

    private final Map<CardElement, ChangeListener<Number>> xListeners = new HashMap<>();
    private final Map<CardElement, ChangeListener<Number>> yListeners = new HashMap<>();
    private final TemplateHistory history = new TemplateHistory();

    private CardTemplate currentTemplate = new CardTemplate();
    private List<Map<String, String>> csvData = new ArrayList<>();
    private List<String> csvHeaders = new ArrayList<>();
    private int currentRecordIndex = -1;
    private final DataMerger dataMerger = new DataMerger();
    private File currentFile;
    private File lastOpenedDirectory;
    private AppSettings settings;
    private CardElement copiedElement;
    private long lastCsvModificationTime = 0;
    private final ControllerState state = new ControllerState();
    private Stage iconLibraryStage;
    private Stage fontLibraryStage;
    private Stage dataViewerStage;
    private static final double SNAP_THRESHOLD_PX = 6.0;
    private static final double GRID_SPACING_MM = 5.0;
    private static final double MIN_ZOOM_LEVEL = 0.1;
    private static final double MAX_ZOOM_LEVEL = 8.0;
    private static final int MAX_RECENT_COLORS = 10;
    private final List<Node> activeSnapGuides = new ArrayList<>();
    private final ObservableList<Color> recentColors = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        state.setPreviewMode(false);
        state.setProfessionalMode(false);
        state.setShowClippedContent(false);
        state.setZoomLevel(1.0);
        state.setDirty(false);
        state.setRestoringPanelDividers(false);
        state.setPersistPanelDividers(false);
        loadSettings();
        applySavedPanelDividerPositions();
        setupPanelDividerPersistence();
        setupTemplateListeners();
        updateCanvasSize();
        updateSizeLabel();
        setupZoomListeners();
        setupCanvasInteractionShortcuts();
        updateTitleAndStatus();
        setupAutoSaveTimeline();
        setupCsvWatchTimeline();
        
        checkForRecovery();
        resetHistory();
        
        elementTreeView.setCellFactory(tv -> {
            TreeCell<CardElement> cell = new TreeCell<>() {
                @Override
                protected void updateItem(CardElement item, boolean empty) {
                    super.updateItem(item, empty);
                    textProperty().unbind();
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        setContentDisplay(ContentDisplay.TEXT_ONLY);
                        setContextMenu(null);
                        opacityProperty().unbind();
                        setOpacity(1.0);
                    } else {
                        final String icon = switch (item) {
                            case TextElement te -> "T";
                            case ImageElement ie -> "Img";
                            case IconElement ice -> "*";
                            case ContainerElement ce -> "Box";
                            case FontElement fe -> "Aa";
                            case ConditionElement ce2 -> "?";
                            default -> "El";
                        };
                        
                        Label iconLabel = new Label(icon);
                        iconLabel.setMinWidth(20);
                        iconLabel.setAlignment(Pos.CENTER);
                        iconLabel.setTooltip(new Tooltip(switch (item) {
                            case TextElement te -> "Text Element";
                            case ImageElement ie -> "Image Element";
                            case IconElement ice -> "Icon Group Element";
                            case ContainerElement ce -> "Container Element";
                            case FontElement fe -> "Font Configuration";
                            case ConditionElement ce2 -> "Condition";
                            default -> "Element";
                        }));

                        Label nameLabel = new Label();
                        nameLabel.textProperty().bind(item.nameProperty());
                        nameLabel.setMaxWidth(Double.MAX_VALUE);

                        Region spacer = new Region();
                        HBox.setHgrow(spacer, Priority.ALWAYS);

                        boolean lockedByAncestorContainer = isLockedByAncestorContainer(getTreeItem());
                        boolean hiddenByAncestorContainer = isHiddenByAncestorContainer(getTreeItem());
                        boolean appearsDisabled = !item.isEnabled() || lockedByAncestorContainer || hiddenByAncestorContainer;

                        Node visibilityNode;
                        if (hiddenByAncestorContainer) {
                            Label inheritedHiddenLabel = new Label("🚫");
                            inheritedHiddenLabel.setMinWidth(28);
                            inheritedHiddenLabel.setPrefWidth(28);
                            inheritedHiddenLabel.setAlignment(Pos.CENTER);
                            inheritedHiddenLabel.setTooltip(new Tooltip("Hidden while parent container is hidden"));
                            visibilityNode = inheritedHiddenLabel;
                        } else {
                            ToggleButton visibilityBtn = new ToggleButton();
                            visibilityBtn.setFocusTraversable(false);
                            visibilityBtn.setPrefWidth(28);
                            visibilityBtn.setSelected(item.isEnabled());
                            visibilityBtn.setText(item.isEnabled() ? "👁" : "🚫");
                            visibilityBtn.setTooltip(new Tooltip(item.isEnabled() ? "Hide Element" : "Show Element"));
                            visibilityBtn.setOnAction(e -> {
                                item.setEnabled(visibilityBtn.isSelected());
                                visibilityBtn.setText(item.isEnabled() ? "👁" : "🚫");
                                visibilityBtn.setTooltip(new Tooltip(item.isEnabled() ? "Hide Element" : "Show Element"));
                                elementTreeView.refresh();
                                renderTemplate();
                                e.consume();
                            });
                            visibilityNode = visibilityBtn;
                        }

                        Node lockNode;
                        if (item instanceof ContainerElement ce) {
                            ToggleButton lockBtn = new ToggleButton();
                            lockBtn.setFocusTraversable(false);
                            lockBtn.setPrefWidth(28);
                            lockBtn.setSelected(ce.isLocked());
                            lockBtn.setText(ce.isLocked() ? "🔒" : "🔓");
                            lockBtn.setTooltip(new Tooltip(ce.isLocked() ? "Unlock Container" : "Lock Container"));
                            lockBtn.setOnAction(e -> {
                                ce.setLocked(lockBtn.isSelected());
                                lockBtn.setText(ce.isLocked() ? "🔒" : "🔓");
                                lockBtn.setTooltip(new Tooltip(ce.isLocked() ? "Unlock Container" : "Lock Container"));
                                elementTreeView.refresh();
                                renderTemplate();
                                e.consume();
                            });
                            lockNode = lockBtn;
                        } else if (lockedByAncestorContainer) {
                            Label inheritedLockLabel = new Label("🔒");
                            inheritedLockLabel.setMinWidth(28);
                            inheritedLockLabel.setPrefWidth(28);
                            inheritedLockLabel.setAlignment(Pos.CENTER);
                            inheritedLockLabel.setTooltip(new Tooltip("Non-selectable while parent container is locked"));
                            lockNode = inheritedLockLabel;
                        } else {
                            Label placeholder = new Label("");
                            placeholder.setMinWidth(28);
                            placeholder.setPrefWidth(28);
                            lockNode = placeholder;
                        }

                        HBox row = new HBox(6, iconLabel, nameLabel, spacer, visibilityNode, lockNode);
                        row.setAlignment(Pos.CENTER_LEFT);
                        row.setDisable(lockedByAncestorContainer);
                        setGraphic(row);
                        setText(null);
                        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                        
                        setOpacity(appearsDisabled ? 0.5 : 1.0);
                        
                        ContextMenu contextMenu = new ContextMenu();
                        MenuItem enableDisableItem = new MenuItem();
                        enableDisableItem.textProperty().bind(item.enabledProperty().map(e -> e ? "Disable" : "Enable"));
                        enableDisableItem.setOnAction(e -> {
                            item.setEnabled(!item.isEnabled());
                            elementTreeView.refresh();
                            renderTemplate();
                        });

                        MenuItem copyItem = new MenuItem("Copy");
                        copyItem.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN));
                        copyItem.setOnAction(e -> handleCopyElement());

                        MenuItem pasteItem = new MenuItem("Paste");
                        pasteItem.setAccelerator(new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN));
                        pasteItem.setOnAction(e -> handlePasteElement());
                        pasteItem.setDisable(copiedElement == null);

                        MenuItem deleteItem = new MenuItem("Delete");
                        deleteItem.setAccelerator(new KeyCodeCombination(KeyCode.DELETE));
                        deleteItem.setOnAction(e -> {
                            elementTreeView.getSelectionModel().select(getTreeItem());
                            handleDeleteElement();
                        });

                        MenuItem lockUnlockItem = new MenuItem();
                        if (item instanceof ContainerElement ce) {
                            lockUnlockItem.textProperty().bind(ce.lockedProperty().map(l -> l ? "Unlock Container" : "Lock Container"));
                            lockUnlockItem.setOnAction(e -> {
                                ce.setLocked(!ce.isLocked());
                                elementTreeView.refresh();
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
            } else if (event.isControlDown() && event.getCode() == KeyCode.D) {
                handleDuplicateElement(null);
                event.consume();
            } else if (!event.isControlDown() && !event.isAltDown() && !event.isMetaDown() && event.getCode() == KeyCode.SPACE) {
                if (previewToolbarBtn != null) {
                    previewToolbarBtn.setSelected(!previewToolbarBtn.isSelected());
                    handleTogglePreviewMode(new ActionEvent(previewToolbarBtn, previewToolbarBtn));
                }
                event.consume();
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

        cardCanvas.setFocusTraversable(true);
        cardCanvas.setOnMousePressed(event -> cardCanvas.requestFocus());
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

    private boolean isLockedByAncestorContainer(TreeItem<CardElement> item) {
        TreeItem<CardElement> current = item == null ? null : item.getParent();
        while (current != null) {
            CardElement currentValue = current.getValue();
            if (currentValue instanceof ContainerElement ce && ce.isLocked()) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private ContainerElement findNearestLockedAncestorContainer(CardElement element) {
        ParentCardElement parent = findParentElement(element);
        while (parent != null) {
            if (parent instanceof ContainerElement container && container.isLocked()) {
                return container;
            }
            parent = findParentElement(parent);
        }
        return null;
    }

    private boolean isHiddenByAncestorContainer(TreeItem<CardElement> item) {
        TreeItem<CardElement> current = item == null ? null : item.getParent();
        while (current != null) {
            CardElement currentValue = current.getValue();
            if (currentValue instanceof ContainerElement ce && !ce.isEnabled()) {
                return true;
            }
            current = current.getParent();
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

        if (selectedEl == null || state.isPreviewMode()) return;

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
        cardCanvas.setSnapToPixel(false);
        double width = currentTemplate.getDimension().getWidthPx();
        double height = currentTemplate.getDimension().getHeightPx();
        double bleedPx = state.isProfessionalMode() ? currentTemplate.getBleedMm() * (CardDimension.getDpi() / 25.4) : 0;
        
        cardCanvas.setMinWidth(width + 2 * bleedPx);
        cardCanvas.setMaxWidth(width + 2 * bleedPx);
        cardCanvas.setMinHeight(height + 2 * bleedPx);
        cardCanvas.setMaxHeight(height + 2 * bleedPx);
        
        updateSizeLabel();
        
        if (!state.isShowClippedContent()) {
            javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(width + 2 * bleedPx, height + 2 * bleedPx);
            cardCanvas.setClip(clip);
        } else {
            cardCanvas.setClip(null);
        }
    }

    private void renderTemplate() {
        cardCanvas.getChildren().clear();
        double bleedPx = state.isProfessionalMode() ? currentTemplate.getBleedMm() * (CardDimension.getDpi() / 25.4) : 0;
        
        Map<String, String> currentRecord = (currentRecordIndex >= 0 && currentRecordIndex < csvData.size()) 
                ? csvData.get(currentRecordIndex) : null;

        Pane contentPane = new Pane();
        contentPane.setLayoutX(bleedPx);
        contentPane.setLayoutY(bleedPx);

        if (state.isShowGrid() && !state.isPreviewMode()) {
            cardCanvas.getChildren().add(createGridOverlay(bleedPx));
        }
        cardCanvas.getChildren().add(contentPane);

        renderElements(currentTemplate.getElements(), contentPane, currentRecord, null, ContainerElement.LayoutType.POSITIONAL, ContainerElement.Alignment.LEFT, false, false);
        
        // Add bleed guide last so it's always visible
        if (state.isProfessionalMode() && !state.isPreviewMode()) {
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
        root.setSnapToPixel(false);
        root.setPrefSize(widthPx, heightPx);
        root.setMinSize(widthPx, heightPx);
        root.setMaxSize(widthPx, heightPx);
        root.setStyle("-fx-background-color: white;");

        // Apply clipping to the root
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(widthPx, heightPx);
        root.setClip(clip);

        double scale = dpi / CardDimension.getDpi();
        Pane contentPane = new Pane();
        contentPane.setSnapToPixel(false);
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
        pane.setSnapToPixel(false);
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
                vbox.setAlignment(mapAlignmentToPos(ce.getAlignment(), ce.getVerticalAlignment()));
                ce.alignmentProperty().addListener((obs, old, newVal) -> vbox.setAlignment(mapAlignmentToPos(newVal, ce.getVerticalAlignment())));
                ce.verticalAlignmentProperty().addListener((obs, old, newVal) -> vbox.setAlignment(mapAlignmentToPos(ce.getAlignment(), newVal)));
                vbox.spacingProperty().bind(ce.spacingProperty());
                yield vbox;
            }
            case HORIZONTAL -> {
                HBox hbox = new HBox();
                hbox.setAlignment(mapAlignmentToPos(ce.getAlignment(), ce.getVerticalAlignment()));
                ce.alignmentProperty().addListener((obs, old, newVal) -> hbox.setAlignment(mapAlignmentToPos(newVal, ce.getVerticalAlignment())));
                ce.verticalAlignmentProperty().addListener((obs, old, newVal) -> hbox.setAlignment(mapAlignmentToPos(ce.getAlignment(), newVal)));
                hbox.spacingProperty().bind(ce.spacingProperty());
                yield hbox;
            }
            case FLOW -> {
                FlowPane flowPane = new FlowPane();
                flowPane.setAlignment(mapAlignmentToPos(ce.getAlignment(), ce.getVerticalAlignment()));
                ce.alignmentProperty().addListener((obs, old, newVal) -> flowPane.setAlignment(mapAlignmentToPos(newVal, ce.getVerticalAlignment())));
                ce.verticalAlignmentProperty().addListener((obs, old, newVal) -> flowPane.setAlignment(mapAlignmentToPos(ce.getAlignment(), newVal)));
                flowPane.hgapProperty().bind(ce.spacingProperty());
                flowPane.vgapProperty().bind(ce.spacingProperty());
                yield flowPane;
            }
            case STACK -> {
                StackPane stackPane = new StackPane();
                stackPane.setAlignment(mapAlignmentToPos(ce.getAlignment(), ce.getVerticalAlignment()));
                ce.alignmentProperty().addListener((obs, old, newVal) -> stackPane.setAlignment(mapAlignmentToPos(newVal, ce.getVerticalAlignment())));
                ce.verticalAlignmentProperty().addListener((obs, old, newVal) -> stackPane.setAlignment(mapAlignmentToPos(ce.getAlignment(), newVal)));
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
        pane.setSnapToPixel(false);

        updatePaneStyle(pane, ce.getBackgroundColor(), ce.getAlpha(), forFinalDesign);
        ce.backgroundColorProperty().addListener((obs, old, newVal) -> updatePaneStyle(pane, newVal, ce.getAlpha(), forFinalDesign));
        ce.alphaProperty().addListener((obs, old, newVal) -> updatePaneStyle(pane, ce.getBackgroundColor(), newVal.doubleValue(), forFinalDesign));

        if (!state.isShowClippedContent() || forFinalDesign) {
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
        flowPane.setSnapToPixel(false);
        flowPane.getStyleClass().add("icon-element");
        flowPane.setAlignment(mapAlignmentToPos(parentAlignment, ContainerElement.VerticalAlignment.TOP));
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

    private javafx.geometry.Pos mapAlignmentToPos(ContainerElement.Alignment alignment, ContainerElement.VerticalAlignment vAlignment) {
        if (alignment == null) alignment = ContainerElement.Alignment.LEFT;
        if (vAlignment == null) vAlignment = ContainerElement.VerticalAlignment.TOP;
        
        return switch (vAlignment) {
            case TOP -> switch (alignment) {
                case LEFT -> Pos.TOP_LEFT;
                case CENTER -> Pos.TOP_CENTER;
                case RIGHT -> Pos.TOP_RIGHT;
            };
            case MIDDLE -> switch (alignment) {
                case LEFT -> Pos.CENTER_LEFT;
                case CENTER -> Pos.CENTER;
                case RIGHT -> Pos.CENTER_RIGHT;
            };
            case BOTTOM -> switch (alignment) {
                case LEFT -> Pos.BOTTOM_LEFT;
                case CENTER -> Pos.BOTTOM_CENTER;
                case RIGHT -> Pos.BOTTOM_RIGHT;
            };
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
            if (!state.isPreviewMode() && !forFinalDesign) {
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
            beginHistoryCompoundEdit();
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

        handle.setOnMouseReleased(mouseEvent -> {
            endHistoryCompoundEdit();
            mouseEvent.consume();
        });

        handle.setOnMouseClicked(javafx.scene.input.MouseEvent::consume);

        pane.getChildren().add(handle);
    }

    private void makeDraggable(Node node, CardElement el) {
        final Delta dragDelta = new Delta();
        final CardElement[] targetEl = new CardElement[1];
        final Node[] targetNode = new Node[1];

        node.setOnMousePressed(mouseEvent -> {
            cardCanvas.requestFocus();
            beginHistoryCompoundEdit();
            clearSnapGuides();
            ContainerElement lockedAncestor = findNearestLockedAncestorContainer(el);
            if (lockedAncestor != null) {
                targetEl[0] = lockedAncestor;
                targetNode[0] = findNodeForElement(cardCanvas, lockedAncestor);
                selectElement(lockedAncestor);
            }
            CardElement selected = getSelectedElement();
            // If a container is already selected and we clicked it or its child, 
            // keep dragging the container instead of selecting the child.
            if (targetEl[0] == null && selected instanceof ContainerElement ce && isDescendant(ce, el)) {
                targetEl[0] = ce;
                targetNode[0] = findNodeForElement(cardCanvas, ce);
            } else if (targetEl[0] == null) {
                targetEl[0] = el;
                targetNode[0] = node;
                // Normal selection behavior
                if (!selectElement(el)) {
                    elementTreeView.getSelectionModel().clearSelection();
                    updatePropertiesPane(el);
                    highlightOnCanvas(el);
                }
            }
            
            if (targetEl[0] != null) {
                dragDelta.x = targetEl[0].getX() - mouseEvent.getSceneX();
                dragDelta.y = targetEl[0].getY() - mouseEvent.getSceneY();
            }
            
            mouseEvent.consume();
        });

        node.setOnMouseDragged(mouseEvent -> {
            if (targetEl[0] == null || targetNode[0] == null) return;
            CardElement activeEl = targetEl[0];
            Node activeNode = targetNode[0];

            double requestedX = mouseEvent.getSceneX() + dragDelta.x;
            double requestedY = mouseEvent.getSceneY() + dragDelta.y;
            MovementDecision decision = evaluateMovementDecision(activeEl, activeNode, requestedX, requestedY);

            if (decision.applied()) {
                showSnapGuides(decision.xSnap(), decision.ySnap());
                activeEl.setX(decision.x());
                activeEl.setY(decision.y());
            } else {
                clearSnapGuides();
            }
            mouseEvent.consume();
        });

        node.setOnMouseReleased(mouseEvent -> {
            clearSnapGuides();
            endHistoryCompoundEdit();
            mouseEvent.consume();
        });
    }

    private double[] getNodeVisualSize(Node node) {
        double width = node.getLayoutBounds().getWidth();
        double height = node.getLayoutBounds().getHeight();

        if (node instanceof Text text) {
            Text temp = new Text(text.getText());
            temp.setFont(text.getFont());
            temp.setStrokeWidth(text.getStrokeWidth());
            width = temp.getLayoutBounds().getWidth();
            height = temp.getLayoutBounds().getHeight();
        }

        return new double[]{width, height};
    }

    private MovementDecision evaluateMovementDecision(CardElement activeEl,
                                                      Node activeNode,
                                                      double requestedX,
                                                      double requestedY) {
        double cardWidth = currentTemplate.getDimension().getWidthPx();
        double cardHeight = currentTemplate.getDimension().getHeightPx();

        double[] size = getNodeVisualSize(activeNode);
        double width = size[0];
        double height = size[1];

        double[] constrainedPosition = constrainPositionForElement(activeEl, requestedX, requestedY, width, height, cardWidth, cardHeight);
        double constrainedX = constrainedPosition[0];
        double constrainedY = constrainedPosition[1];

        List<ElementBounds> snapTargets = state.isSnapToGuides() ? collectSnapTargets(activeEl) : List.of();
        SnapResult xSnap = state.isSnapToGuides()
                ? calculateSnap(constrainedX, width, cardWidth, true, snapTargets)
                : new SnapResult(constrainedX, null, true);
        SnapResult ySnap = state.isSnapToGuides()
                ? calculateSnap(constrainedY, height, cardHeight, false, snapTargets)
                : new SnapResult(constrainedY, null, false);

        double snappedX = xSnap.position();
        double snappedY = ySnap.position();

        double[] finalPosition = constrainPositionForElement(activeEl, snappedX, snappedY, width, height, cardWidth, cardHeight);
        double finalX = finalPosition[0];
        double finalY = finalPosition[1];

        boolean applied = hasMovementDelta(activeEl, finalX, finalY);
        return new MovementDecision(finalX, finalY, xSnap, ySnap, applied);
    }

    private double[] constrainPositionForElement(CardElement element,
                                                 double x,
                                                 double y,
                                                 double width,
                                                 double height,
                                                 double cardWidth,
                                                 double cardHeight) {
        if (element instanceof ImageElement ie && ie.isAllowOverflow()) {
            return new double[]{x, y};
        }

        double constrainedX = Math.max(0, x);
        if (constrainedX + width > cardWidth) {
            constrainedX = Math.max(0, cardWidth - width);
        }

        double constrainedY = Math.max(0, y);
        if (constrainedY + height > cardHeight) {
            constrainedY = Math.max(0, cardHeight - height);
        }

        return new double[]{constrainedX, constrainedY};
    }

    private boolean hasMovementDelta(CardElement element, double newX, double newY) {
        return Math.abs(element.getX() - newX) > 0.0001 || Math.abs(element.getY() - newY) > 0.0001;
    }

    private Pane createGridOverlay(double bleedPx) {
        double cardWidth = currentTemplate.getDimension().getWidthPx();
        double cardHeight = currentTemplate.getDimension().getHeightPx();
        double spacingPx = GRID_SPACING_MM * (CardDimension.getDpi() / 25.4);

        Pane grid = new Pane();
        grid.setMouseTransparent(true);
        grid.setLayoutX(bleedPx);
        grid.setLayoutY(bleedPx);
        grid.setMinSize(cardWidth, cardHeight);
        grid.setPrefSize(cardWidth, cardHeight);
        grid.setMaxSize(cardWidth, cardHeight);

        for (double x = spacingPx; x < cardWidth; x += spacingPx) {
            javafx.scene.shape.Line line = new javafx.scene.shape.Line(x, 0, x, cardHeight);
            styleGridLine(line);
            grid.getChildren().add(line);
        }
        for (double y = spacingPx; y < cardHeight; y += spacingPx) {
            javafx.scene.shape.Line line = new javafx.scene.shape.Line(0, y, cardWidth, y);
            styleGridLine(line);
            grid.getChildren().add(line);
        }

        return grid;
    }

    private void styleGridLine(javafx.scene.shape.Line line) {
        line.setStroke(Color.web("#e6e6e6"));
        line.setStrokeWidth(0.5);
        line.setMouseTransparent(true);
    }

    private void setupCanvasInteractionShortcuts() {
        cardCanvas.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() || event.isShortcutDown() || event.isAltDown() || event.isMetaDown()) {
                return;
            }
            double step = event.isShiftDown() ? 10.0 : 1.0;
            boolean handled = switch (event.getCode()) {
                case LEFT -> nudgeSelectedElement(-step, 0);
                case RIGHT -> nudgeSelectedElement(step, 0);
                case UP -> nudgeSelectedElement(0, -step);
                case DOWN -> nudgeSelectedElement(0, step);
                default -> false;
            };
            if (handled) {
                event.consume();
            }
        });
    }

    private boolean nudgeSelectedElement(double deltaX, double deltaY) {
        CardElement selected = getSelectedElement();
        if (selected == null || state.isPreviewMode() || findNearestLockedAncestorContainer(selected) != null) {
            return false;
        }

        Node node = findNodeForElement(cardCanvas, selected);
        if (node == null) {
            return false;
        }

        double[] size = getNodeVisualSize(node);
        double cardWidth = currentTemplate.getDimension().getWidthPx();
        double cardHeight = currentTemplate.getDimension().getHeightPx();
        double[] constrained = constrainPositionForElement(
                selected,
                selected.getX() + deltaX,
                selected.getY() + deltaY,
                size[0],
                size[1],
                cardWidth,
                cardHeight
        );

        if (!hasMovementDelta(selected, constrained[0], constrained[1])) {
            return false;
        }

        beginHistoryCompoundEdit();
        selected.setX(constrained[0]);
        selected.setY(constrained[1]);
        endHistoryCompoundEdit();
        renderTemplate();
        selectElement(selected);
        return true;
    }

    private List<ElementBounds> collectSnapTargets(CardElement draggedElement) {
        List<ElementBounds> targets = new ArrayList<>();
        collectSnapTargetsRecursive(cardCanvas, draggedElement, targets);
        return targets;
    }

    private void collectSnapTargetsRecursive(Pane pane, CardElement draggedElement, List<ElementBounds> targets) {
        for (Node child : pane.getChildren()) {
            Object maybeElement = child.getProperties().get("cardElement");
            if (maybeElement instanceof CardElement other && other != draggedElement) {
                targets.add(createElementBounds(other, child));
            }
            if (child instanceof Pane childPane) {
                collectSnapTargetsRecursive(childPane, draggedElement, targets);
            }
        }
    }

    private ElementBounds createElementBounds(CardElement element, Node node) {
        double[] size = getNodeVisualSize(node);
        double width = size[0];
        double height = size[1];

        double left = element.getX();
        double top = element.getY();
        double right = left + width;
        double bottom = top + height;
        double centerX = left + width / 2.0;
        double centerY = top + height / 2.0;

        return new ElementBounds(left, centerX, right, top, centerY, bottom);
    }

    private SnapResult calculateSnap(double currentPosition,
                                     double size,
                                     double cardSize,
                                     boolean isX,
                                     List<ElementBounds> targets) {
        double leftOrTop = currentPosition;
        double center = currentPosition + size / 2.0;
        double rightOrBottom = currentPosition + size;

        double bestDistance = SNAP_THRESHOLD_PX + 1;
        double bestAdjustedPosition = currentPosition;
        Double guide = null;

        List<Double> axisTargets = new ArrayList<>();
        axisTargets.add(0.0);
        axisTargets.add(cardSize / 2.0);
        axisTargets.add(cardSize);

        for (ElementBounds bounds : targets) {
            if (isX) {
                axisTargets.add(bounds.min());
                axisTargets.add(bounds.center());
                axisTargets.add(bounds.max());
            } else {
                axisTargets.add(bounds.crossMin());
                axisTargets.add(bounds.crossCenter());
                axisTargets.add(bounds.crossMax());
            }
        }

        for (double target : axisTargets) {
            double[] anchors = new double[]{leftOrTop, center, rightOrBottom};
            for (double anchor : anchors) {
                double distance = Math.abs(target - anchor);
                if (distance <= SNAP_THRESHOLD_PX && distance < bestDistance) {
                    bestDistance = distance;
                    bestAdjustedPosition = currentPosition + (target - anchor);
                    guide = target;
                }
            }
        }

        return new SnapResult(bestAdjustedPosition, guide, isX);
    }

    private void showSnapGuides(SnapResult xSnap, SnapResult ySnap) {
        clearSnapGuides();
        double bleedPx = state.isProfessionalMode() ? currentTemplate.getBleedMm() * (CardDimension.getDpi() / 25.4) : 0;
        double cardWidth = currentTemplate.getDimension().getWidthPx();
        double cardHeight = currentTemplate.getDimension().getHeightPx();

        if (xSnap.guide() != null) {
            javafx.scene.shape.Line vertical = new javafx.scene.shape.Line(
                    bleedPx + xSnap.guide(), bleedPx,
                    bleedPx + xSnap.guide(), bleedPx + cardHeight
            );
            vertical.setStroke(Color.DODGERBLUE);
            vertical.setStrokeWidth(1.0);
            vertical.getStrokeDashArray().setAll(4.0, 4.0);
            vertical.setMouseTransparent(true);
            cardCanvas.getChildren().add(vertical);
            activeSnapGuides.add(vertical);
        }

        if (ySnap.guide() != null) {
            javafx.scene.shape.Line horizontal = new javafx.scene.shape.Line(
                    bleedPx, bleedPx + ySnap.guide(),
                    bleedPx + cardWidth, bleedPx + ySnap.guide()
            );
            horizontal.setStroke(Color.DODGERBLUE);
            horizontal.setStrokeWidth(1.0);
            horizontal.getStrokeDashArray().setAll(4.0, 4.0);
            horizontal.setMouseTransparent(true);
            cardCanvas.getChildren().add(horizontal);
            activeSnapGuides.add(horizontal);
        }

        activeSnapGuides.forEach(Node::toFront);
    }

    private void clearSnapGuides() {
        if (activeSnapGuides.isEmpty()) {
            return;
        }
        cardCanvas.getChildren().removeAll(activeSnapGuides);
        activeSnapGuides.clear();
    }

    private record ElementBounds(double min, double center, double max,
                                 double crossMin, double crossCenter, double crossMax) { }

    private record SnapResult(double position, Double guide, boolean xAxis) { }

    private record MovementDecision(double x, double y, SnapResult xSnap, SnapResult ySnap, boolean applied) { }

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
        // Section headers intentionally omitted to keep element settings UI compact.
    }

    private void addProperty(String label, Node control) {
        addProperty(label, control, null);
    }

    private void addProperty(String label, Node control, String tooltipText) {
        addProperty(label, control, tooltipText, !(control instanceof TextArea));
    }

    private void addProperty(String label, Node control, String tooltipText, boolean compactRow) {
        Label l = new Label(label);
        l.setStyle("-fx-text-fill: #444; -fx-font-size: 0.9em;");
        l.setMinWidth(110);
        l.setPrefWidth(110);
        l.setMaxWidth(110);
        if (tooltipText != null) {
            Tooltip tooltip = new Tooltip(tooltipText);
            l.setTooltip(tooltip);
            if (control instanceof Control c) {
                c.setTooltip(tooltip);
            } else if (control instanceof Pane p) {
                for (Node child : p.getChildren()) {
                    if (child instanceof Control c && c.getTooltip() == null) {
                        c.setTooltip(tooltip);
                    }
                }
            }
        }

        if (control instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }

        if (compactRow) {
            HBox row = new HBox(8, l, control);
            row.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(control, Priority.ALWAYS);
            propertiesPane.getChildren().add(row);
            return;
        }

        propertiesPane.getChildren().addAll(l, control);
    }

    private void addStandaloneControl(Control control, String tooltipText) {
        if (tooltipText != null) {
            control.setTooltip(new Tooltip(tooltipText));
        }
        control.setMaxWidth(Double.MAX_VALUE);
        propertiesPane.getChildren().add(control);
    }

    private void configureColorPicker(ColorPicker picker) {
        picker.setStyle("-fx-color-label-visible: true;");
        picker.setMaxWidth(Double.MAX_VALUE);
        picker.getCustomColors().setAll(recentColors);
    }

    private void rememberRecentColor(Color color) {
        if (color == null) {
            return;
        }
        recentColors.remove(color);
        recentColors.add(0, color);
        if (recentColors.size() > MAX_RECENT_COLORS) {
            recentColors.remove(MAX_RECENT_COLORS, recentColors.size());
        }
    }

    private HBox createTaggableField(TextInputControl field) {
        Button insertTagButton = new Button("Insert CSV Tag");
        insertTagButton.setTooltip(new Tooltip("Insert a {{Header}} tag from the loaded CSV at the cursor"));
        insertTagButton.setDisable(csvHeaders.isEmpty());

        ContextMenu tagMenu = new ContextMenu();
        if (csvHeaders.isEmpty()) {
            MenuItem emptyItem = new MenuItem("Load a CSV to insert tags");
            emptyItem.setDisable(true);
            tagMenu.getItems().add(emptyItem);
        } else {
            for (String header : csvHeaders) {
                String tag = "{{" + header + "}}";
                MenuItem item = new MenuItem(tag);
                item.setOnAction(event -> insertTagAtCaret(field, tag));
                tagMenu.getItems().add(item);
            }
        }

        insertTagButton.setOnAction(event -> tagMenu.show(insertTagButton, Side.BOTTOM, 0, 0));

        HBox row = new HBox(5, field, insertTagButton);
        HBox.setHgrow(field, Priority.ALWAYS);
        return row;
    }

    private void insertTagAtCaret(TextInputControl field, String tag) {
        field.requestFocus();
        int caret = Math.max(0, field.getCaretPosition());
        field.insertText(caret, tag);
        field.positionCaret(caret + tag.length());
    }

    private void updatePropertiesPane(CardElement el) {
        clearActiveListeners();
        propertiesPane.getChildren().clear();
        if (el == null) return;

        addSectionLabel("Element Settings");
        addSelectionPathRow(el);
        TextField nameField = new TextField(el.getName());
        nameField.textProperty().bindBidirectional(el.nameProperty());
        addProperty("Name", nameField, "The name of this element in the element tree");

        if (el instanceof ConditionElement ce) {
            addSectionLabel("Condition");
            TextField conditionField = new TextField(ce.getCondition());
            conditionField.textProperty().bindBidirectional(ce.conditionProperty());
            addManagedListener(ce.conditionProperty(), (obs, old, newVal) -> renderTemplate());
            addProperty("CSV Column or Expression", createTaggableField(conditionField), "CSV column name (e.g., 'type') or expression (e.g., 'level>5') to control visibility");
        }

        if (el instanceof TextElement te) {
            addSectionLabel("Text Content");
            TextArea textArea = new TextArea(te.getText());
            textArea.setPrefRowCount(3);
            textArea.textProperty().bindBidirectional(te.textProperty());
            addManagedListener(te.textProperty(), (obs, old, newVal) -> renderTemplate());
            addProperty("Content (use {{header}} for merge)", createTaggableField(textArea), "The text to display. Use {{Header}} to insert dynamic CSV data.", false);

            addSectionLabel("Appearance");
            ComboBox<String> fontConfigCombo = new ComboBox<>();
            fontConfigCombo.getItems().add("Default");
            fontConfigCombo.getItems().addAll(currentTemplate.getFontLibrary().getFonts().keySet());
            fontConfigCombo.setValue(te.getFontConfigName());
            te.fontConfigNameProperty().bind(fontConfigCombo.valueProperty());
            addManagedListener(te.fontConfigNameProperty(), (obs, old, newVal) -> renderTemplate());
            addProperty("Font Configuration", fontConfigCombo, "Select a pre-defined font style from the Font Library");

            javafx.beans.binding.BooleanBinding isNotDefault = te.fontConfigNameProperty().isNotEqualTo("Default");

            HBox sizeBox = UIUtils.createSliderWithNumericField(te.fontSizeProperty(), 8, 72);
            sizeBox.disableProperty().bind(isNotDefault);
            addManagedListener(te.fontSizeProperty(), (obs, old, newVal) -> renderTemplate());
            addProperty("Size", sizeBox, "Adjust the font size in points");

            ColorPicker colorPicker = new ColorPicker(Color.web(te.getColor()));
            configureColorPicker(colorPicker);
            colorPicker.disableProperty().bind(isNotDefault);
            colorPicker.setOnAction(e -> {
                Color selectedColor = colorPicker.getValue();
                te.setColor(UIUtils.toHexString(selectedColor));
                rememberRecentColor(selectedColor);
                renderTemplate();
            });
            addProperty("Color", colorPicker, "Choose the color for the text");

            addSectionLabel("Layout");
            HBox angleBox = UIUtils.createSliderWithNumericField(te.angleProperty(), -360, 360);
            angleBox.disableProperty().bind(isNotDefault);
            addManagedListener(te.angleProperty(), (obs, old, newVal) -> renderTemplate());
            addProperty("Angle", angleBox, "Rotate the element (in degrees)");

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
            lockAspectBox.setTooltip(new Tooltip("Maintain the same proportions when resizing"));
            lockAspectBox.selectedProperty().bindBidirectional(ie.lockAspectRatioProperty());

            CheckBox allowOverflowBox = new CheckBox("Allow Overflow (goes out of bounds)");
            allowOverflowBox.setTooltip(new Tooltip("If enabled, the element won't be clipped by its parent's bounds"));
            allowOverflowBox.selectedProperty().bindBidirectional(ie.allowOverflowProperty());
            addManagedListener(ie.allowOverflowProperty(), (obs, old, newVal) -> {
                updateCanvasSize();
                renderTemplate();
            });

            HBox pathBox = createTaggableField(pathField);
            pathBox.getChildren().add(browseBtn);
            addProperty("Path ({{header}})", pathBox, "Path to the image file. Use {{Header}} for dynamic paths.");
            addProperty("Width", widthBox, "Width of the image in millimeters");
            addProperty("Height", heightBox, "Height of the image in millimeters");
            addStandaloneControl(lockAspectBox, "Maintain the same proportions when resizing");
            addStandaloneControl(allowOverflowBox, "If enabled, the element won't be clipped by its parent's bounds");

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
            lockAspectBox.setTooltip(new Tooltip("Maintain the same proportions when resizing"));
            lockAspectBox.selectedProperty().bindBidirectional(ce.lockAspectRatioProperty());

            addSectionLabel("Appearance");
            HBox alphaBox = UIUtils.createSliderWithNumericField(ce.alphaProperty(), 0.0, 1.0);
            ColorPicker colorPicker = new ColorPicker(Color.TRANSPARENT);
            configureColorPicker(colorPicker);
            try {
                colorPicker.setValue(Color.web(ce.getBackgroundColor()));
            } catch (Exception e) {
                // Ignore
            }
            colorPicker.setOnAction(e -> {
                Color selectedColor = colorPicker.getValue();
                ce.setBackgroundColor(UIUtils.toHexString(selectedColor));
                rememberRecentColor(selectedColor);
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

            ComboBox<ContainerElement.VerticalAlignment> vAlignBox = new ComboBox<>(javafx.collections.FXCollections.observableArrayList(ContainerElement.VerticalAlignment.values()));
            vAlignBox.valueProperty().bindBidirectional(ce.verticalAlignmentProperty());
            addManagedListener(ce.verticalAlignmentProperty(), (obs, old, newVal) -> renderTemplate());

            HBox spacingBox = UIUtils.createSliderWithNumericField(ce.spacingProperty(), 0, 100);
            addManagedListener(ce.spacingProperty(), (obs, old, newVal) -> renderTemplate());

            CheckBox lockedBox = new CheckBox("Locked (Children non-editable)");
            lockedBox.setTooltip(new Tooltip("If enabled, children cannot be selected or moved on the canvas"));
            lockedBox.selectedProperty().bindBidirectional(ce.lockedProperty());
            addManagedListener(ce.lockedProperty(), (obs, old, newVal) -> renderTemplate());

            addProperty("Width", widthBox, "Width of the container in millimeters");
            addProperty("Height", heightBox, "Height of the container in millimeters");
            addStandaloneControl(lockAspectBox, "Maintain the same proportions when resizing");
            addProperty("Alpha", alphaBox, "Opacity level (0.0 = transparent, 1.0 = opaque)");
            addProperty("Color", colorPicker, "The fill color for this container");
            addProperty("Layout", layoutBox, "How children are arranged: POSITIONAL (manual), HORIZONTAL (row), VERTICAL (column), STACK (layered)");
            addProperty("H-Alignment", alignBox, "Horizontal alignment of children");
            addProperty("V-Alignment", vAlignBox, "Vertical alignment of children");
            addProperty("Spacing", spacingBox, "Space between children in HORIZONTAL or VERTICAL layouts");
            addStandaloneControl(lockedBox, "If enabled, children cannot be selected or moved on the canvas");

        } else if (el instanceof IconElement ice) {
            TextField valueField = new TextField(ice.getValue());
            valueField.textProperty().bindBidirectional(ice.valueProperty());
            
            HBox iconWidthBox = UIUtils.createSliderWithNumericField(ice.iconWidthProperty(), 8, 200);
            HBox iconHeightBox = UIUtils.createSliderWithNumericField(ice.iconHeightProperty(), 8, 200);

            ComboBox<String> mappingBox = new ComboBox<>();
            mappingBox.getItems().addAll(currentTemplate.getIconLibrary().getMappings().keySet());
            mappingBox.valueProperty().bindBidirectional(ice.mappingNameProperty());

            addProperty("Value (supports {{header}})", createTaggableField(valueField), "The text to be replaced by icons based on mapping");
            addProperty("Icon Mapping", mappingBox, "Select which icon mapping configuration to use");
            addSectionLabel("Icon Dimensions");
            addProperty("Width", iconWidthBox, "Width of each icon in millimeters");
            addProperty("Height", iconHeightBox, "Height of each icon in millimeters");

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
            configureColorPicker(colorPicker);
            colorPicker.setOnAction(e -> {
                Color selectedColor = colorPicker.getValue();
                fe.setColor(UIUtils.toHexString(selectedColor));
                rememberRecentColor(selectedColor);
                renderTemplate();
            });

            HBox angleBox = UIUtils.createSliderWithNumericField(fe.angleProperty(), -360, 360);
            addManagedListener(fe.angleProperty(), (obs, old, newVal) -> renderTemplate());

            HBox outlineWidthBox = UIUtils.createSliderWithNumericField(fe.outlineWidthProperty(), 0, 20);
            addManagedListener(fe.outlineWidthProperty(), (obs, old, newVal) -> renderTemplate());

            ColorPicker outlineColorPicker = new ColorPicker(Color.web(fe.getOutlineColor()));
            configureColorPicker(outlineColorPicker);
            outlineColorPicker.setOnAction(e -> {
                Color selectedColor = outlineColorPicker.getValue();
                fe.setOutlineColor(UIUtils.toHexString(selectedColor));
                rememberRecentColor(selectedColor);
                renderTemplate();
            });

            addProperty("Family", familyBox, "The font family name");
            addProperty("Size", sizeBox, "Adjust the font size in points");
            addProperty("Weight", weightBox, "Choose the font weight (e.g., Normal, Bold)");
            addProperty("Posture", postureBox, "Choose the font posture (e.g., Regular, Italic)");
            addProperty("Color", colorPicker, "Choose the color for the text");
            addProperty("Angle", angleBox, "Rotate the text (in degrees)");
            addProperty("Outline Width", outlineWidthBox, "Width of the text outline");
            addProperty("Outline Color", outlineColorPicker, "Color of the text outline");
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
            saveTempDeck();
        });

        Button browseBtn = new Button("Browse");
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
            saveTempDeck();
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
            csvData = new ArrayList<>();
            csvHeaders = new ArrayList<>();
            currentRecordIndex = -1;
            lastCsvModificationTime = 0;
            state.setDirty(false);
            setupTemplateListeners();
            updateCanvasSize();
            updateRecordLabel();
            renderTemplate();
            updateTitleAndStatus();
            deleteTempDeck();
            resetHistory();
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
            saveTempDeck();
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
            updateTitleAndStatus("Loaded CSV: " + file.getName());
            renderTemplate();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setContentText("Error loading CSV: " + e.getMessage());
            alert.show();
        }
    }

    @FXML
    void handleAddText() {
        addElement(new TextElement());
    }

    @FXML
    void handleAddImage() {
        addElement(new ImageElement());
    }

    @FXML
    void handleAddContainer() {
        addElement(new ContainerElement());
    }

    @FXML
    void handleAddFont() {
        addElement(new FontElement());
    }

    @FXML
    void handleAddIcon() {
        addElement(new IconElement());
    }

    @FXML
    void handleAddCondition() {
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
        addMapBtn.setTooltip(new Tooltip("Create a new named icon mapping"));
        Button removeMapBtn = new Button("Remove Selected");
        removeMapBtn.setTooltip(new Tooltip("Delete the selected icon mapping"));
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
                    addKeyBtn.setTooltip(new Tooltip("Add a new character sequence to be replaced by an icon"));
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
                            saveTempDeck();
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
        addFontBtn.setTooltip(new Tooltip("Create a new named font configuration"));
        Button removeFontBtn = new Button("Remove Selected");
        removeFontBtn.setTooltip(new Tooltip("Delete the selected font configuration"));
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
                    familyBox.setTooltip(new Tooltip("The font family name"));
                    fontEl.fontFamilyProperty().bind(familyBox.valueProperty());
                    familyBox.valueProperty().addListener((o, ov, nv) -> {
                        renderTemplate();
                        updatePropertiesPane(getSelectedElement());
                        saveTempDeck();
                    });

                    HBox sizeBox = UIUtils.createSliderWithNumericField(fontEl.fontSizeProperty(), 8, 120);
                    Tooltip sizeTooltip = new Tooltip("Adjust the font size in points");
                    sizeBox.getChildren().forEach(n -> { if (n instanceof Control c) c.setTooltip(sizeTooltip); });
                    fontEl.fontSizeProperty().addListener((o, ov, nv) -> {
                        renderTemplate();
                        updatePropertiesPane(getSelectedElement());
                        saveTempDeck();
                    });

                    ComboBox<FontWeight> weightBox = new ComboBox<>(FXCollections.observableArrayList(FontWeight.values()));
                    weightBox.setValue(fontEl.getFontWeight());
                    weightBox.setMaxWidth(Double.MAX_VALUE);
                    weightBox.setTooltip(new Tooltip("Choose the font weight (e.g., Normal, Bold)"));
                    fontEl.fontWeightProperty().bind(weightBox.valueProperty());
                    weightBox.valueProperty().addListener((o, ov, nv) -> {
                        renderTemplate();
                        updatePropertiesPane(getSelectedElement());
                        saveTempDeck();
                    });

                    ComboBox<FontPosture> postureBox = new ComboBox<>(FXCollections.observableArrayList(FontPosture.values()));
                    postureBox.setValue(fontEl.getFontPosture());
                    postureBox.setMaxWidth(Double.MAX_VALUE);
                    postureBox.setTooltip(new Tooltip("Choose the font posture (e.g., Regular, Italic)"));
                    fontEl.fontPostureProperty().bind(postureBox.valueProperty());
                    postureBox.valueProperty().addListener((o, ov, nv) -> {
                        renderTemplate();
                        updatePropertiesPane(getSelectedElement());
                        saveTempDeck();
                    });

                    ColorPicker colorPicker = new ColorPicker(Color.web(fontEl.getColor()));
                    configureColorPicker(colorPicker);
                    colorPicker.setTooltip(new Tooltip("Choose the color for the text"));
                    colorPicker.setOnAction(ce -> {
                        Color selectedColor = colorPicker.getValue();
                        fontEl.setColor(UIUtils.toHexString(selectedColor));
                        rememberRecentColor(selectedColor);
                        renderTemplate();
                        updatePropertiesPane(getSelectedElement());
                        saveTempDeck();
                    });

                    HBox angleBox = UIUtils.createSliderWithNumericField(fontEl.angleProperty(), -360, 360);
                    Tooltip angleTooltip = new Tooltip("Rotate the text (in degrees)");
                    angleBox.getChildren().forEach(n -> { if (n instanceof Control c) c.setTooltip(angleTooltip); });
                    fontEl.angleProperty().addListener((o, ov, nv) -> {
                        renderTemplate();
                        updatePropertiesPane(getSelectedElement());
                        saveTempDeck();
                    });

                    HBox outlineWidthBox = UIUtils.createSliderWithNumericField(fontEl.outlineWidthProperty(), 0, 20);
                    Tooltip outlineTooltip = new Tooltip("Width of the text outline");
                    outlineWidthBox.getChildren().forEach(n -> { if (n instanceof Control c) c.setTooltip(outlineTooltip); });
                    fontEl.outlineWidthProperty().addListener((o, ov, nv) -> {
                        renderTemplate();
                        updatePropertiesPane(getSelectedElement());
                        saveTempDeck();
                    });

                    ColorPicker outlineColorPicker = new ColorPicker(Color.web(fontEl.getOutlineColor()));
                    configureColorPicker(outlineColorPicker);
                    outlineColorPicker.setTooltip(new Tooltip("Color of the text outline"));
                    outlineColorPicker.setOnAction(ce -> {
                        Color selectedColor = outlineColorPicker.getValue();
                        fontEl.setOutlineColor(UIUtils.toHexString(selectedColor));
                        rememberRecentColor(selectedColor);
                        renderTemplate();
                        updatePropertiesPane(getSelectedElement());
                        saveTempDeck();
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
    void handleDeleteElement() {
        CardElement selected = getSelectedElement();
        if (selected != null) {
            ObservableList<CardElement> parentList = findParentList(selected);
            if (parentList != null) {
                parentList.remove(selected);
            }
        }
    }

    @FXML
    void handleCopyElement() {
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
    void handlePasteElement() {
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

    @FXML
    void handleDuplicateElement(ActionEvent event) {
        CardElement selected = getSelectedElement();
        if (selected == null) return;

        try {
            CardElement newEl = DeckStorage.clone(selected, CardElement.class);
            newEl.setX(newEl.getX() + 10);
            newEl.setY(newEl.getY() + 10);
            ObservableList<CardElement> parentList = findParentList(selected);
            if (parentList != null) {
                newEl.setName(nextCopyName(selected.getName(), parentList));
            }

            if (parentList != null) {
                int index = parentList.indexOf(selected);
                parentList.add(index + 1, newEl);
                selectElement(newEl);
            }
        } catch (IOException e) {
            System.err.println("Failed to duplicate element: " + e.getMessage());
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
        saveButton.setTooltip(new Tooltip("Save changes back to the original CSV file"));
        Button closeButton = new Button("Close");
        closeButton.setTooltip(new Tooltip("Close the data viewer"));

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
        if (!runPreflightBeforeProduction("Print Deck")) {
            return;
        }
        PrintService printService = new PrintService(currentTemplate, csvData, dataMerger, this);
        printService.showPrintDialog(propertiesPane.getScene().getWindow());
    }

    @FXML
    void handleExportPdf(ActionEvent event) {
        if (!runPreflightBeforeProduction("Export PDF")) {
            return;
        }
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
        if (!runPreflightBeforeProduction("Export TTS Deck Sheet")) {
            return;
        }
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
        browseButton.setTooltip(new Tooltip("Select a deck file"));
        browseButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            addDeckOpenFilters(fileChooser);
            File file = fileChooser.showOpenDialog(dialog.getDialogPane().getScene().getWindow());
            if (file != null) {
                pathField.setText(file.getAbsolutePath());
            }
        });

        TextField bleedField = new TextField(String.valueOf(currentTemplate.getBleedMm()));
        bleedField.setPrefWidth(50);
        bleedField.setTooltip(new Tooltip("Extra margin around the card for printing (standard is 3mm)"));

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
            saveTempDeck();
            updateCanvasSize();
            renderTemplate();
        });
    }

    @FXML
    void handleShowControlsHelp(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Keyboard Shortcuts");
        alert.setHeaderText("Shortcuts Not Directly Shown in Menus");
        alert.setResizable(true);
        alert.getDialogPane().setPrefWidth(700);
        alert.getDialogPane().setPrefHeight(460);
        alert.getDialogPane().setContent(new ScrollPane(new Label("""
                ELEMENT TREE SHORTCUTS
                - Ctrl+Up: Move selected element up (or into/out of a container depending on position).
                - Ctrl+Down: Move selected element down (or into/out of a container depending on position).
                - Ctrl+D: Duplicate selected element while the tree is focused.

                CONTEXT ACTION SHORTCUTS
                - Ctrl+C: Copy selected element.
                - Ctrl+V: Paste copied element.
                - Delete: Delete selected element.

                CANVAS SHORTCUTS
                - Arrow Keys: Nudge the selected element by 1 px after focusing the canvas.
                - Shift+Arrow Keys: Nudge the selected element by 10 px.
                - Snap: Toggle guide snapping from the toolbar or View menu.
                - Grid: Toggle the editor grid from the toolbar or View menu.

                GLOBAL TOGGLE
                - Space: Toggle Preview Mode when the element tree is focused.
                """)));
        alert.showAndWait();
    }

    @FXML
    void handleExit(ActionEvent event) {
        saveTempDeck();
        saveSettings();
        javafx.application.Platform.exit();
    }

    public boolean isProfessionalMode() {
        return state.isProfessionalMode();
    }

    @FXML
    void handleToggleProfessionalMode(ActionEvent event) {
        if (event.getSource() instanceof CheckMenuItem ci) {
            state.setProfessionalMode(ci.isSelected());
        }
        if (proModeMenuItem != null) proModeMenuItem.setSelected(state.isProfessionalMode());
        updateCanvasSize();
        renderTemplate();
    }

    @FXML
    void handleTogglePreviewMode(ActionEvent event) {
        if (event.getSource() instanceof CheckMenuItem ci) {
            state.setPreviewMode(ci.isSelected());
        } else if (event.getSource() instanceof ToggleButton tb) {
            state.setPreviewMode(tb.isSelected());
        }
        
        // Sync both UI elements
        if (previewToolbarBtn != null) previewToolbarBtn.setSelected(state.isPreviewMode());
        if (previewMenuItem != null) previewMenuItem.setSelected(state.isPreviewMode());
        
        renderTemplate();
    }

    @FXML
    void handleToggleShowClippedContent(ActionEvent event) {
        state.setShowClippedContent(((CheckMenuItem) event.getSource()).isSelected());
        updateCanvasSize();
        renderTemplate();
    }

    @FXML
    void handleToggleSnapToGuides(ActionEvent event) {
        Object source = event.getSource();
        if (source instanceof CheckMenuItem ci) {
            state.setSnapToGuides(ci.isSelected());
        } else if (source instanceof ToggleButton tb) {
            state.setSnapToGuides(tb.isSelected());
        }
        syncSnapAndGridControls();
        saveSettings();
        updateTitleAndStatus();
    }

    @FXML
    void handleToggleGrid(ActionEvent event) {
        Object source = event.getSource();
        if (source instanceof CheckMenuItem ci) {
            state.setShowGrid(ci.isSelected());
        } else if (source instanceof ToggleButton tb) {
            state.setShowGrid(tb.isSelected());
        }
        syncSnapAndGridControls();
        saveSettings();
        renderTemplate();
        updateTitleAndStatus();
    }

    private void syncSnapAndGridControls() {
        if (snapMenuItem != null) snapMenuItem.setSelected(state.isSnapToGuides());
        if (snapToolbarBtn != null) snapToolbarBtn.setSelected(state.isSnapToGuides());
        if (gridMenuItem != null) gridMenuItem.setSelected(state.isShowGrid());
        if (gridToolbarBtn != null) gridToolbarBtn.setSelected(state.isShowGrid());
    }

    @FXML
    void handleZoomIn(ActionEvent event) {
        state.setZoomLevel(state.getZoomLevel() * 1.2);
        updateZoom();
        saveSettings();
    }

    @FXML
    void handleZoomOut(ActionEvent event) {
        state.setZoomLevel(state.getZoomLevel() / 1.2);
        updateZoom();
        saveSettings();
    }

    @FXML
    void handleResetZoom(ActionEvent event) {
        state.setZoomLevel(1.0);
        updateZoom();
        saveSettings();
    }

    private void updateZoom() {
        state.setZoomLevel(sanitizeZoomLevel(state.getZoomLevel()));
        cardCanvas.setScaleX(state.getZoomLevel());
        cardCanvas.setScaleY(state.getZoomLevel());
        String zoomText = String.format("%.0f%%", state.getZoomLevel() * 100);
        zoomLabel.setText(zoomText);
        if (zoomToolbarLabel != null) {
            zoomToolbarLabel.setText(zoomText);
        }
    }

    private double sanitizeZoomLevel(double zoomLevel) {
        if (!Double.isFinite(zoomLevel)) {
            return 1.0;
        }
        return Math.max(MIN_ZOOM_LEVEL, Math.min(MAX_ZOOM_LEVEL, zoomLevel));
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
                    state.setZoomLevel(state.getZoomLevel() * 1.1);
                } else if (event.getDeltaY() < 0) {
                    state.setZoomLevel(state.getZoomLevel() / 1.1);
                }
                updateZoom();
                saveSettings();
                event.consume();
            }
        });
    }

    private void checkForRecovery() {
        File tempFile = DeckStorage.getRecoveryTempFile();
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

    private void resetHistory() {
        try {
            history.reset(currentTemplate);
        } catch (IOException e) {
            System.err.println("Failed to initialize undo history: " + e.getMessage());
        }
        updateHistoryControls();
    }

    private void recordHistorySnapshot() {
        try {
            if (history.record(currentTemplate)) {
                updateHistoryControls();
            }
        } catch (IOException e) {
            System.err.println("Failed to record undo history: " + e.getMessage());
        }
    }

    private void updateHistoryControls() {
        if (undoMenuItem != null) {
            undoMenuItem.setDisable(!history.canUndo());
        }
        if (redoMenuItem != null) {
            redoMenuItem.setDisable(!history.canRedo());
        }
    }

    private void beginHistoryCompoundEdit() {
        history.suppressRecording();
    }

    private void endHistoryCompoundEdit() {
        history.resumeRecording();
        saveTempDeck();
    }

    @FXML
    void handleUndo(ActionEvent event) {
        try {
            history.undo().ifPresent(template -> restoreHistoryTemplate(template, "Undo"));
            updateHistoryControls();
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to restore undo history: " + e.getMessage());
            alert.show();
        }
    }

    @FXML
    void handleRedo(ActionEvent event) {
        try {
            history.redo().ifPresent(template -> restoreHistoryTemplate(template, "Redo"));
            updateHistoryControls();
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to restore redo history: " + e.getMessage());
            alert.show();
        }
    }

    private void restoreHistoryTemplate(CardTemplate template, String statusMessage) {
        List<Integer> selectedPath = TreeSelectionPath.capture(elementTreeView);
        history.suppressRecording();
        try {
            closeTemplateEditorWindows();
            applyTemplate(template);
            selectTreePath(selectedPath);
            state.setDirty(true);
            saveTempDeck();
            updateTitleAndStatus(statusMessage);
        } finally {
            history.resumeRecording();
            updateHistoryControls();
        }
    }

    private void selectTreePath(List<Integer> path) {
        Optional<TreeItem<CardElement>> item = TreeSelectionPath.resolve(elementTreeView, path);
        if (item.isPresent() && item.get().getValue() != null) {
            elementTreeView.getSelectionModel().select(item.get());
        } else {
            elementTreeView.getSelectionModel().clearSelection();
            updatePropertiesPane(null);
            highlightOnCanvas(null);
            updateCoordinatesLabel(null);
        }
    }

    private void closeTemplateEditorWindows() {
        if (iconLibraryStage != null) {
            iconLibraryStage.close();
            iconLibraryStage = null;
        }
        if (fontLibraryStage != null) {
            fontLibraryStage.close();
            fontLibraryStage = null;
        }
    }

    public void saveTempDeck() {
        if (!state.isDirty()) {
            state.setDirty(true);
            updateTitleAndStatus();
        }
        try {
            DeckStorage.save(currentTemplate, DeckStorage.getTempFile());
            recordHistorySnapshot();
        } catch (IOException e) {
            System.err.println("Failed to save temp deck: " + e.getMessage());
        }
    }

    private void loadTempDeck() {
        File tempFile = DeckStorage.getRecoveryTempFile();
        if (tempFile.exists()) {
            try {
                CardTemplate template = DeckStorage.load(tempFile);
                state.setDirty(true);
                applyTemplate(template);
            } catch (IOException e) {
                System.err.println("Failed to load temp deck: " + e.getMessage());
            }
        }
    }

    private void deleteTempDeck() {
        DeckStorage.deleteTempFiles();
    }

    private void loadSettings() {
        try {
            settings = DeckStorage.loadSettings();
            state.setProfessionalMode(settings.isProfessionalMode());
            state.setSnapToGuides(settings.isSnapToGuides());
            state.setShowGrid(settings.isShowGrid());
            state.setZoomLevel(sanitizeZoomLevel(settings.getZoomLevel()));
            settings.setZoomLevel(state.getZoomLevel());
            if (proModeMenuItem != null) proModeMenuItem.setSelected(state.isProfessionalMode());
            syncSnapAndGridControls();
            if (settings.getLastOpenedDeckPath() != null) {
                lastOpenedDirectory = new File(settings.getLastOpenedDeckPath()).getParentFile();
            }
            updateZoom();
        } catch (IOException e) {
            System.err.println("Failed to load settings: " + e.getMessage());
            settings = new AppSettings();
            state.setZoomLevel(1.0);
            syncSnapAndGridControls();
            updateZoom();
        }
    }

    private boolean runPreflightBeforeProduction(String actionName) {
        PreflightService.PreflightReport report = new PreflightService(dataMerger)
                .validate(currentTemplate, csvData, csvHeaders, currentFile);
        if (report.isClean()) {
            return true;
        }

        Alert alert = new Alert(report.hasErrors() ? Alert.AlertType.ERROR : Alert.AlertType.CONFIRMATION);
        alert.setTitle("Preflight Report");
        alert.setHeaderText(report.hasErrors()
                ? actionName + " blocked by preflight errors"
                : actionName + " has preflight warnings");
        alert.setResizable(true);
        alert.getDialogPane().setPrefWidth(760);
        alert.getDialogPane().setPrefHeight(480);

        TextArea issueText = new TextArea(formatPreflightIssues(report));
        issueText.setEditable(false);
        issueText.setWrapText(true);
        issueText.setPrefRowCount(18);
        alert.getDialogPane().setContent(issueText);

        if (report.hasErrors()) {
            alert.getButtonTypes().setAll(ButtonType.OK);
            alert.showAndWait();
            return false;
        }

        ButtonType continueButton = new ButtonType("Continue Anyway", ButtonBar.ButtonData.OK_DONE);
        alert.getButtonTypes().setAll(continueButton, ButtonType.CANCEL);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == continueButton;
    }

    private String formatPreflightIssues(PreflightService.PreflightReport report) {
        StringBuilder sb = new StringBuilder();
        for (PreflightService.PreflightIssue issue : report.issues()) {
            sb.append(issue.severity() == PreflightService.Severity.ERROR ? "ERROR: " : "WARNING: ");
            sb.append(issue.message()).append(System.lineSeparator());
        }
        return sb.toString();
    }

    private void applySavedPanelDividerPositions() {
        if (settings == null || mainSplitPane == null) {
            return;
        }
        state.setPersistPanelDividers(false);
        Runnable applyPositions = () -> {
            if (mainSplitPane.getDividers().size() < 2) {
                return;
            }
            double left = settings.getLeftPanelDividerPosition();
            double right = settings.getRightPanelDividerPosition();
            if (left <= 0 || left >= 1 || right <= 0 || right >= 1 || left >= right) {
                left = 0.22;
                right = 0.78;
            }
            state.setRestoringPanelDividers(true);
            mainSplitPane.setDividerPositions(left, right);
            state.setRestoringPanelDividers(false);
        };
        Platform.runLater(() -> {
            applyPositions.run();
            // Apply again after an extra pulse to prevent startup layout from overriding restored positions.
            Platform.runLater(() -> {
                applyPositions.run();
                state.setPersistPanelDividers(true);
            });
        });
    }

    private void setupPanelDividerPersistence() {
        if (mainSplitPane == null) {
            return;
        }
        Platform.runLater(() -> {
            if (mainSplitPane.getDividers().size() < 2) {
                return;
            }
            mainSplitPane.getDividers().get(0).positionProperty().addListener((obs, oldVal, newVal) -> {
                if (state.isRestoringPanelDividers() || !state.isPersistPanelDividers()) {
                    return;
                }
                settings.setLeftPanelDividerPosition(newVal.doubleValue());
                saveSettings();
            });
            mainSplitPane.getDividers().get(1).positionProperty().addListener((obs, oldVal, newVal) -> {
                if (state.isRestoringPanelDividers() || !state.isPersistPanelDividers()) {
                    return;
                }
                settings.setRightPanelDividerPosition(newVal.doubleValue());
                saveSettings();
            });
        });
    }

    public void saveSettings() {
        try {
            settings.setProfessionalMode(state.isProfessionalMode());
            settings.setSnapToGuides(state.isSnapToGuides());
            settings.setShowGrid(state.isShowGrid());
            settings.setZoomLevel(sanitizeZoomLevel(state.getZoomLevel()));
            if (mainSplitPane != null && mainSplitPane.getScene() != null) {
                Window window = mainSplitPane.getScene().getWindow();
                if (window instanceof Stage stage) {
                    settings.setWindowWidth(stage.getWidth());
                    settings.setWindowHeight(stage.getHeight());
                }
            }
            if (mainSplitPane != null && mainSplitPane.getDividers().size() >= 2) {
                settings.setLeftPanelDividerPosition(mainSplitPane.getDividers().get(0).getPosition());
                settings.setRightPanelDividerPosition(mainSplitPane.getDividers().get(1).getPosition());
            }
            DeckStorage.saveSettings(settings);
        } catch (IOException e) {
            System.err.println("Failed to save settings: " + e.getMessage());
        }
    }

    private void setupAutoSaveTimeline() {
        Timeline timeline = new Timeline(new KeyFrame(javafx.util.Duration.seconds(30), event -> {
            if (state.isDirty()) {
                try {
                    DeckStorage.save(currentTemplate, DeckStorage.getRecoveryTempFile());
                } catch (IOException e) {
                    System.err.println("Failed to autosave recovery deck: " + e.getMessage());
                }
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
        closeTemplateEditorWindows();
        clearActiveListeners();
        copiedElement = null;
        elementTreeView.getSelectionModel().clearSelection();
        updatePropertiesPane(null);
        highlightOnCanvas(null);
        updateCoordinatesLabel(null);

        this.currentTemplate = template;
        setupTemplateListeners();
        updateCanvasSize();
        if (template.getCsvPath() != null) {
            currentRecordIndex = 0;
            loadCsvFile(new File(template.getCsvPath()));
        } else {
            csvData = new ArrayList<>();
            csvHeaders = new ArrayList<>();
            currentRecordIndex = -1;
            lastCsvModificationTime = 0;
            updateRecordLabel();
        }
        renderTemplate();
    }

    @FXML
    void handleSaveDeck(ActionEvent event) {
        if (currentFile == null) {
            handleSaveDeckAs(event);
        } else {
            try {
                saveToFile(toDeckMakerFile(currentFile));
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
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("DeckMaker Files", "*.dm"));
        File file = fileChooser.showSaveDialog(propertiesPane.getScene().getWindow());
        if (file != null) {
            file = toDeckMakerFile(file);
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
        addDeckOpenFilters(fileChooser);
        File file = fileChooser.showOpenDialog(propertiesPane.getScene().getWindow());
        if (file != null) {
            loadDeck(file);
        }
    }

    private void loadDeck(File file) {
        try {
            lastOpenedDirectory = file.getParentFile();
            CardTemplate template = DeckStorage.load(file);
            currentFile = toDeckMakerFile(file);
            state.setDirty(isLegacyDeckFile(file));
            settings.setLastOpenedDeckPath(file.getAbsolutePath());
            saveSettings();
            applyTemplate(template);
            if (isLegacyDeckFile(file)) {
                updateTitleAndStatus("Loaded legacy .cm deck. Save will write " + currentFile.getName());
            } else {
                updateTitleAndStatus();
            }
            deleteTempDeck();
            resetHistory();
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
            state.setDirty(false);
            settings.setLastOpenedDeckPath(file.getAbsolutePath());
            saveSettings();
            updateTitleAndStatus("Saved: " + file.getName());
            deleteTempDeck();
        } catch (IOException e) {
            // Silently fail during auto-save if needed, but for manual save show alert
            System.err.println("Error saving deck: " + e.getMessage());
        }
    }

    private static void addDeckOpenFilters(FileChooser fileChooser) {
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("DeckMaker Files", "*.dm", "*.cm"),
                new FileChooser.ExtensionFilter("DeckMaker Files (*.dm)", "*.dm"),
                new FileChooser.ExtensionFilter("Legacy DeckMaker Files (*.cm)", "*.cm")
        );
    }

    static boolean isLegacyDeckFile(File file) {
        return file != null && file.getName().toLowerCase(Locale.ROOT).endsWith(LEGACY_DECK_EXTENSION);
    }

    static File toDeckMakerFile(File file) {
        if (file == null) {
            return null;
        }
        String path = file.getPath();
        String lowerPath = path.toLowerCase(Locale.ROOT);
        if (lowerPath.endsWith(DECK_EXTENSION)) {
            return file;
        }
        if (lowerPath.endsWith(LEGACY_DECK_EXTENSION)) {
            return new File(path.substring(0, path.length() - LEGACY_DECK_EXTENSION.length()) + DECK_EXTENSION);
        }
        return new File(path + DECK_EXTENSION);
    }

    private void updateRecordLabel() {
        recordLabel.setText((currentRecordIndex + 1) + " / " + csvData.size());
    }

    /**
     * Updates the main window title and status bar label to show the current deck name.
     */
    private void updateTitleAndStatus() {
        updateTitleAndStatus(null);
    }

    private void updateTitleAndStatus(String temporaryMessage) {
        String deckName = (currentFile != null) ? currentFile.getName() : "Unsaved Deck";
        if (state.isDirty()) {
            deckName += " (modified)";
        }
        String modeBadges = buildModeBadges();

        if (temporaryMessage != null) {
            statusLabel.setText(temporaryMessage + modeBadges);
            // Optionally clear after some time, but for now we'll just show it
        } else {
            statusLabel.setText("Deck: " + deckName + (csvData.isEmpty() ? "" : " | CSV: " + csvData.size() + " records") + modeBadges);
        }

        if (propertiesPane.getScene() != null && propertiesPane.getScene().getWindow() instanceof Stage stage) {
            stage.setTitle("DeckMaker - " + (currentFile != null ? currentFile.getName() : "Unsaved") + (state.isDirty() ? "*" : ""));
        }
    }

    private String buildModeBadges() {
        List<String> badges = new ArrayList<>();
        if (state.isProfessionalMode()) badges.add("PRO");
        if (state.isPreviewMode()) badges.add("PREVIEW");
        if (state.isShowClippedContent()) badges.add("CLIPPED");
        if (state.isSnapToGuides()) badges.add("SNAP");
        if (state.isShowGrid() && !state.isPreviewMode()) badges.add("GRID");
        return badges.isEmpty() ? "" : " | [" + String.join("] [", badges) + "]";
    }

    private void addSelectionPathRow(CardElement el) {
        TextField selectionPathField = new TextField(buildSelectionPath(el));
        selectionPathField.setEditable(false);
        selectionPathField.setFocusTraversable(false);
        addProperty("Selection Path", selectionPathField, "Hierarchy path from root to this element");
    }

    private String buildSelectionPath(CardElement el) {
        LinkedList<String> path = new LinkedList<>();
        path.addFirst(el.getName());

        ParentCardElement parent = findParentElement(el);
        while (parent != null) {
            path.addFirst(parent.getName());
            parent = findParentElement(parent);
        }

        path.addFirst("Root");
        return String.join(" > ", path);
    }

    private String nextCopyName(String baseName, ObservableList<CardElement> siblings) {
        String copyName = baseName + " (Copy)";
        if (!containsElementName(siblings, copyName)) {
            return copyName;
        }

        int copyNumber = 2;
        while (containsElementName(siblings, baseName + " (Copy " + copyNumber + ")")) {
            copyNumber++;
        }
        return baseName + " (Copy " + copyNumber + ")";
    }

    private boolean containsElementName(ObservableList<CardElement> elements, String name) {
        for (CardElement element : elements) {
            if (name.equals(element.getName())) {
                return true;
            }
        }
        return false;
    }
}
