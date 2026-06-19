package io.github.coretension.deckmaker.ui;

import io.github.coretension.deckmaker.model.*;
import io.github.coretension.deckmaker.service.DataMerger;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.TextFlow;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class CardRenderer {
    private final CardTemplate template;
    private final DataMerger dataMerger;
    private final File deckFile;
    private final boolean previewMode;
    private final boolean showClippedContent;
    private final EditHooks editHooks;

    CardRenderer(CardTemplate template,
                 DataMerger dataMerger,
                 File deckFile,
                 boolean previewMode,
                 boolean showClippedContent,
                 EditHooks editHooks) {
        this.template = template;
        this.dataMerger = dataMerger;
        this.deckFile = deckFile;
        this.previewMode = previewMode;
        this.showClippedContent = showClippedContent;
        this.editHooks = editHooks;
    }

    void renderElements(ObservableList<CardElement> elements,
                        Pane targetPane,
                        Map<String, String> currentRecord,
                        boolean forFinalDesign) {
        renderElements(elements, targetPane, currentRecord, null,
                ContainerElement.LayoutType.POSITIONAL, ContainerElement.Alignment.LEFT, forFinalDesign, false);
    }

    BufferedImage renderCardToImage(Map<String, String> record,
                                    double dpi,
                                    boolean showBleedGuide,
                                    boolean professionalMode) {
        double bleedMm = professionalMode ? template.getBleedMm() : 0;
        double widthPx = (template.getDimension().getWidthMm() + 2 * bleedMm) * dpi / 25.4;
        double heightPx = (template.getDimension().getHeightMm() + 2 * bleedMm) * dpi / 25.4;

        Pane root = new Pane();
        root.setSnapToPixel(false);
        root.setPrefSize(widthPx, heightPx);
        root.setMinSize(widthPx, heightPx);
        root.setMaxSize(widthPx, heightPx);
        root.setStyle("-fx-background-color: white;");
        root.setClip(new javafx.scene.shape.Rectangle(widthPx, heightPx));

        double scale = dpi / CardDimension.getDpi();
        double bleedPx = bleedMm * dpi / 25.4;
        Pane contentPane = new Pane();
        contentPane.setSnapToPixel(false);
        contentPane.setLayoutX(bleedPx);
        contentPane.setLayoutY(bleedPx);
        contentPane.setScaleX(scale);
        contentPane.setScaleY(scale);
        contentPane.setTranslateX((scale - 1) * template.getDimension().getWidthPx() / 2);
        contentPane.setTranslateY((scale - 1) * template.getDimension().getHeightPx() / 2);
        root.getChildren().add(contentPane);

        renderElements(template.getElements(), contentPane, record, true);

        if (showBleedGuide && professionalMode) {
            double cardWidthPx = template.getDimension().getWidthMm() * dpi / 25.4;
            double cardHeightPx = template.getDimension().getHeightMm() * dpi / 25.4;
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

    private void renderElements(ObservableList<CardElement> elements,
                                Pane targetPane,
                                Map<String, String> currentRecord,
                                FontElement inheritedFont,
                                ContainerElement.LayoutType containerLayout,
                                ContainerElement.Alignment containerAlignment,
                                boolean forFinalDesign,
                                boolean isLocked) {
        FontElement currentFont = inheritedFont;
        for (CardElement element : elements) {
            if (!element.isEnabled()) {
                continue;
            }

            if (element instanceof ConditionElement condition) {
                if (dataMerger.evaluateCondition(condition.getCondition(), currentRecord)) {
                    renderElements(condition.getChildren(), targetPane, currentRecord, currentFont,
                            containerLayout, containerAlignment, forFinalDesign, isLocked);
                }
            } else if (element instanceof FontElement fontElement) {
                currentFont = fontElement;
            } else if (element instanceof IconElement iconElement && containerLayout != ContainerElement.LayoutType.POSITIONAL) {
                for (Node node : createIconNodes(iconElement, currentRecord)) {
                    targetPane.getChildren().add(node);
                    if (!forFinalDesign) {
                        if (isLocked || editHooks == null) {
                            node.setMouseTransparent(true);
                        } else {
                            editHooks.makeDraggable(node, iconElement);
                        }
                    }
                    node.getProperties().put("cardElement", iconElement);
                }
            } else {
                Node node = createNodeForElement(element, currentRecord, currentFont,
                        containerLayout, containerAlignment, forFinalDesign, isLocked, targetPane);
                if (node == null) {
                    continue;
                }

                targetPane.getChildren().add(node);
                if (element instanceof ParentCardElement parent && node instanceof Pane childPane) {
                    ContainerElement.LayoutType childLayout = ContainerElement.LayoutType.POSITIONAL;
                    ContainerElement.Alignment childAlign = ContainerElement.Alignment.LEFT;
                    boolean nextLocked = isLocked;

                    if (parent instanceof ContainerElement container) {
                        childLayout = container.getLayoutType();
                        childAlign = container.getAlignment();
                        nextLocked |= container.isLocked();
                    }

                    renderElements(parent.getChildren(), childPane, currentRecord, currentFont,
                            childLayout, childAlign, forFinalDesign, nextLocked);
                }
                if (node instanceof Pane pane && editHooks != null) {
                    editHooks.ensureResizeHandleOnTop(pane);
                }
            }
        }
    }

    private List<Node> createIconNodes(IconElement element, Map<String, String> currentRecord) {
        List<Node> nodes = new ArrayList<>();
        String value = currentRecord != null ? dataMerger.merge(element.getValue(), currentRecord) : element.getValue();
        if (value == null) {
            return nodes;
        }

        Map<String, String> iconMap = template.getIconLibrary().getMappings().get(element.getMappingName());
        if (iconMap == null) {
            return nodes;
        }

        List<String> sortedKeys = iconMap.keySet().stream()
                .filter(key -> !key.isEmpty())
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .toList();

        String remaining = value;
        while (!remaining.isEmpty()) {
            boolean found = false;
            for (String key : sortedKeys) {
                if (remaining.startsWith(key)) {
                    String iconPath = iconMap.get(key);
                    if (iconPath != null && !iconPath.isEmpty()) {
                        ImageView imageView = new ImageView();
                        String baseDir = deckFile != null ? deckFile.getParent() : null;
                        imageView.setImage(loadSafeImage(iconPath, baseDir));
                        if (imageView.getImage() != null) {
                            imageView.fitWidthProperty().bind(element.iconWidthProperty());
                            imageView.fitHeightProperty().bind(element.iconHeightProperty());
                            imageView.setPreserveRatio(true);
                            imageView.setPickOnBounds(true);
                            nodes.add(imageView);
                        }
                    }
                    remaining = remaining.substring(key.length());
                    found = true;
                    break;
                }
            }
            if (!found) {
                remaining = remaining.substring(1);
            }
        }
        return nodes;
    }

    private Node createNodeForElement(CardElement element,
                                      Map<String, String> currentRecord,
                                      FontElement fontConfig,
                                      ContainerElement.LayoutType parentLayout,
                                      ContainerElement.Alignment parentAlignment,
                                      boolean forFinalDesign,
                                      boolean isLocked,
                                      Pane parentPane) {
        Node node = switch (element) {
            case TextElement textElement -> createTextNode(textElement, currentRecord, fontConfig, parentAlignment, parentPane);
            case ImageElement imageElement -> createImageNode(imageElement, currentRecord);
            case ContainerElement containerElement -> createContainerNode(containerElement, forFinalDesign);
            case IconElement iconElement -> createIconFlowPane(iconElement, currentRecord, parentAlignment);
            default -> null;
        };

        if (node == null) {
            return null;
        }

        boolean isPositional = parentLayout == null || parentLayout == ContainerElement.LayoutType.POSITIONAL;
        if (isPositional) {
            node.layoutXProperty().bind(element.xProperty());
            if (element instanceof TextElement textElement) {
                if (node instanceof TextFlow) {
                    node.layoutYProperty().bind(element.yProperty());
                } else {
                    node.layoutYProperty().bind(element.yProperty().add(textElement.fontSizeProperty()));
                }
            } else {
                node.layoutYProperty().bind(element.yProperty());
            }
        }

        if (!forFinalDesign) {
            if (isLocked || (element instanceof ContainerElement container && container.isLocked()) || editHooks == null) {
                node.setMouseTransparent(true);
            } else {
                editHooks.makeDraggable(node, element);
                if (element instanceof ContainerElement || element instanceof ImageElement) {
                    editHooks.makeResizable((Pane) node, element);
                }
            }
        }
        node.getProperties().put("cardElement", element);
        return node;
    }

    private Node createTextNode(TextElement element,
                                Map<String, String> currentRecord,
                                FontElement fontConfig,
                                ContainerElement.Alignment parentAlignment,
                                Pane parentPane) {
        FontElement effectiveFont = resolveEffectiveFont(element, fontConfig);
        return RichTextRenderer.createTextNode(
                element,
                currentRecord,
                dataMerger,
                effectiveFont,
                parentAlignment,
                parentPane,
                template.getDimension().getWidthPx()
        );
    }

    private FontElement resolveEffectiveFont(TextElement element, FontElement fontConfig) {
        FontElement resolvedFont = fontConfig;
        if (element.getFontConfigName() != null && !element.getFontConfigName().equals("Default")) {
            FontElement libraryFont = template.getFontLibrary().getFonts().get(element.getFontConfigName());
            if (libraryFont != null) {
                resolvedFont = libraryFont;
            }
        }
        return resolvedFont;
    }

    private Node createImageNode(ImageElement element, Map<String, String> currentRecord) {
        ImageView imageView = new ImageView();
        imageView.getStyleClass().add("image-element");

        javafx.beans.value.ChangeListener<String> pathListener = (obs, old, newValue) -> {
            String path = currentRecord != null ? dataMerger.merge(newValue, currentRecord) : newValue;
            if (path != null && !path.isEmpty()) {
                String baseDir = null;
                if (newValue != null && newValue.contains("{{") && template.getCsvPath() != null) {
                    baseDir = new File(template.getCsvPath()).getParent();
                } else if (deckFile != null) {
                    baseDir = deckFile.getParent();
                }
                imageView.setImage(loadSafeImage(path, baseDir));
            } else {
                imageView.setImage(null);
            }
        };
        element.imagePathProperty().addListener(pathListener);
        pathListener.changed(null, null, element.getImagePath());

        imageView.preserveRatioProperty().bind(element.lockAspectRatioProperty());
        imageView.fitWidthProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(() -> {
            Image image = imageView.getImage();
            if (image == null || !element.isLockAspectRatio() || image.getWidth() == 0 || image.getHeight() == 0) {
                return element.getWidth();
            }
            double imageAspectRatio = image.getWidth() / image.getHeight();
            double boxAspectRatio = element.getWidth() / element.getHeight();
            return imageAspectRatio > boxAspectRatio ? element.getWidth() : element.getHeight() * imageAspectRatio;
        }, element.widthProperty(), element.heightProperty(), element.lockAspectRatioProperty(), imageView.imageProperty()));

        imageView.fitHeightProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(() -> {
            Image image = imageView.getImage();
            if (image == null || !element.isLockAspectRatio() || image.getWidth() == 0 || image.getHeight() == 0) {
                return element.getHeight();
            }
            double imageAspectRatio = image.getWidth() / image.getHeight();
            double boxAspectRatio = element.getWidth() / element.getHeight();
            return imageAspectRatio > boxAspectRatio ? element.getWidth() / imageAspectRatio : element.getHeight();
        }, element.widthProperty(), element.heightProperty(), element.lockAspectRatioProperty(), imageView.imageProperty()));

        Pane pane = new Pane(imageView);
        pane.setSnapToPixel(false);
        pane.getStyleClass().add("image-container");
        pane.minWidthProperty().bind(imageView.fitWidthProperty());
        pane.maxWidthProperty().bind(imageView.fitWidthProperty());
        pane.minHeightProperty().bind(imageView.fitHeightProperty());
        pane.maxHeightProperty().bind(imageView.fitHeightProperty());
        pane.prefWidthProperty().bind(imageView.fitWidthProperty());
        pane.prefHeightProperty().bind(imageView.fitHeightProperty());

        if (element.isAllowOverflow()) {
            pane.setManaged(false);
        }
        return pane;
    }

    private Pane createContainerNode(ContainerElement element, boolean forFinalDesign) {
        Pane pane = switch (element.getLayoutType()) {
            case VERTICAL -> {
                VBox vbox = new VBox();
                vbox.setAlignment(mapAlignmentToPos(element.getAlignment(), element.getVerticalAlignment()));
                element.alignmentProperty().addListener((obs, old, newValue) -> vbox.setAlignment(mapAlignmentToPos(newValue, element.getVerticalAlignment())));
                element.verticalAlignmentProperty().addListener((obs, old, newValue) -> vbox.setAlignment(mapAlignmentToPos(element.getAlignment(), newValue)));
                vbox.spacingProperty().bind(element.spacingProperty());
                yield vbox;
            }
            case HORIZONTAL -> {
                HBox hbox = new HBox();
                hbox.setAlignment(mapAlignmentToPos(element.getAlignment(), element.getVerticalAlignment()));
                element.alignmentProperty().addListener((obs, old, newValue) -> hbox.setAlignment(mapAlignmentToPos(newValue, element.getVerticalAlignment())));
                element.verticalAlignmentProperty().addListener((obs, old, newValue) -> hbox.setAlignment(mapAlignmentToPos(element.getAlignment(), newValue)));
                hbox.spacingProperty().bind(element.spacingProperty());
                yield hbox;
            }
            case FLOW -> {
                FlowPane flowPane = new FlowPane();
                flowPane.setAlignment(mapAlignmentToPos(element.getAlignment(), element.getVerticalAlignment()));
                element.alignmentProperty().addListener((obs, old, newValue) -> flowPane.setAlignment(mapAlignmentToPos(newValue, element.getVerticalAlignment())));
                element.verticalAlignmentProperty().addListener((obs, old, newValue) -> flowPane.setAlignment(mapAlignmentToPos(element.getAlignment(), newValue)));
                flowPane.hgapProperty().bind(element.spacingProperty());
                flowPane.vgapProperty().bind(element.spacingProperty());
                yield flowPane;
            }
            case STACK -> {
                StackPane stackPane = new StackPane();
                stackPane.setAlignment(mapAlignmentToPos(element.getAlignment(), element.getVerticalAlignment()));
                element.alignmentProperty().addListener((obs, old, newValue) -> stackPane.setAlignment(mapAlignmentToPos(newValue, element.getVerticalAlignment())));
                element.verticalAlignmentProperty().addListener((obs, old, newValue) -> stackPane.setAlignment(mapAlignmentToPos(element.getAlignment(), newValue)));
                yield stackPane;
            }
            default -> new Pane();
        };

        pane.getStyleClass().add("container-element");
        pane.minWidthProperty().bind(element.widthProperty());
        pane.maxWidthProperty().bind(element.widthProperty());
        pane.minHeightProperty().bind(element.heightProperty());
        pane.maxHeightProperty().bind(element.heightProperty());
        pane.prefWidthProperty().bind(element.widthProperty());
        pane.prefHeightProperty().bind(element.heightProperty());
        pane.setSnapToPixel(false);

        updatePaneStyle(pane, element.getBackgroundColor(), element.getAlpha(), forFinalDesign);
        element.backgroundColorProperty().addListener((obs, old, newValue) -> updatePaneStyle(pane, newValue, element.getAlpha(), forFinalDesign));
        element.alphaProperty().addListener((obs, old, newValue) -> updatePaneStyle(pane, element.getBackgroundColor(), newValue.doubleValue(), forFinalDesign));

        if (!showClippedContent || forFinalDesign) {
            javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
            clip.widthProperty().bind(pane.widthProperty());
            clip.heightProperty().bind(pane.heightProperty());
            pane.setClip(clip);
        }
        pane.setPickOnBounds(true);
        return pane;
    }

    private FlowPane createIconFlowPane(IconElement element,
                                        Map<String, String> currentRecord,
                                        ContainerElement.Alignment parentAlignment) {
        FlowPane flowPane = new FlowPane();
        flowPane.setSnapToPixel(false);
        flowPane.getStyleClass().add("icon-element");
        flowPane.setAlignment(mapAlignmentToPos(parentAlignment, ContainerElement.VerticalAlignment.TOP));
        flowPane.setPickOnBounds(false);
        flowPane.setMaxWidth(Region.USE_PREF_SIZE);
        flowPane.setMaxHeight(Region.USE_PREF_SIZE);

        javafx.beans.value.ChangeListener<Object> rebuildIcons = (obs, old, newValue) -> {
            flowPane.getChildren().clear();
            flowPane.getChildren().addAll(createIconNodes(element, currentRecord));
        };
        element.valueProperty().addListener(rebuildIcons);
        element.mappingNameProperty().addListener(rebuildIcons);
        rebuildIcons.changed(null, null, null);
        return flowPane;
    }

    private Image loadSafeImage(String path, String baseDir) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        try {
            File file = new File(path);
            if (!file.isAbsolute() && baseDir != null) {
                file = new File(baseDir, path);
            }
            if (!file.exists()) {
                return null;
            }

            if (path.toLowerCase().endsWith(".svg")) {
                try {
                    BufferedImage bufferedImage = ImageIO.read(file);
                    if (bufferedImage != null) {
                        return SwingFXUtils.toFXImage(bufferedImage, null);
                    }
                } catch (Exception e) {
                    // Fall back to the default JavaFX loader.
                }
            }
            return new Image(file.toURI().toString());
        } catch (Exception e) {
            return null;
        }
    }

    private Pos mapAlignmentToPos(ContainerElement.Alignment alignment, ContainerElement.VerticalAlignment verticalAlignment) {
        if (alignment == null) {
            alignment = ContainerElement.Alignment.LEFT;
        }
        if (verticalAlignment == null) {
            verticalAlignment = ContainerElement.VerticalAlignment.TOP;
        }

        return switch (verticalAlignment) {
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

    private void updatePaneStyle(Pane pane, String color, double alpha, boolean forFinalDesign) {
        try {
            Color parsedColor = Color.web(color);
            String alphaColor = String.format("rgba(%d, %d, %d, %.2f)",
                    (int) (parsedColor.getRed() * 255),
                    (int) (parsedColor.getGreen() * 255),
                    (int) (parsedColor.getBlue() * 255),
                    alpha);

            StringBuilder style = new StringBuilder("-fx-background-color: " + alphaColor + "; ");
            if (!forFinalDesign) {
                style.append("-fx-border-style: dashed; ");
                style.append("-fx-border-width: 1; ");
                style.append(previewMode
                        ? "-fx-border-color: transparent; "
                        : "-fx-border-color: #888888; ");
            }
            pane.setStyle(style.toString());
        } catch (Exception e) {
            // Ignore styling errors.
        }
    }

    interface EditHooks {
        void makeDraggable(Node node, CardElement element);

        void makeResizable(Pane pane, CardElement element);

        void ensureResizeHandleOnTop(Pane pane);
    }
}
