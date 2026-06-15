package io.github.coretension.deckmaker.ui;

import io.github.coretension.deckmaker.model.ContainerElement;
import io.github.coretension.deckmaker.model.FontElement;
import io.github.coretension.deckmaker.model.TextElement;
import io.github.coretension.deckmaker.service.DataMerger;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

final class RichTextRenderer {
    private static final Pattern SUPPORTED_TAG_PATTERN = Pattern.compile("(?i)<\\s*/?\\s*(b|i|u|span|br)\\b");
    private static final Pattern TAG_PATTERN = Pattern.compile("(?i)<\\s*(/?)\\s*(b|i|u|span|br)\\b([^>]*)>");
    private static final Pattern COLOR_STYLE_PATTERN = Pattern.compile("(?i)color\\s*:\\s*([^;\"']+)");

    private RichTextRenderer() {
    }

    static Node createTextNode(TextElement element,
                               Map<String, String> currentRecord,
                               DataMerger dataMerger,
                               FontElement effectiveFont,
                               ContainerElement.Alignment parentAlignment,
                               Pane parentPane,
                               double cardWidthPx) {
        String mergedText = currentRecord != null ? dataMerger.merge(element.getText(), currentRecord) : element.getText();
        if (containsRichTextMarkup(mergedText)) {
            return createRichTextNode(element, mergedText, effectiveFont, parentAlignment, parentPane, cardWidthPx);
        }
        return createPlainTextNode(element, currentRecord, dataMerger, effectiveFont, parentAlignment, parentPane, cardWidthPx);
    }

    private static Text createPlainTextNode(TextElement element,
                                            Map<String, String> currentRecord,
                                            DataMerger dataMerger,
                                            FontElement effectiveFont,
                                            ContainerElement.Alignment parentAlignment,
                                            Pane parentPane,
                                            double cardWidthPx) {
        Text text = new Text();
        bindTextWrappingWidth(text, element, parentPane, cardWidthPx);
        text.textProperty().bind(Bindings.createStringBinding(
                () -> currentRecord != null ? dataMerger.merge(element.getText(), currentRecord) : element.getText(),
                element.textProperty()
        ));
        text.getStyleClass().add("text-element");
        applyTextStyleBindings(text, element, effectiveFont);
        text.setTextAlignment(mapAlignmentToTextAlignment(parentAlignment));
        return text;
    }

    private static TextFlow createRichTextNode(TextElement element,
                                               String mergedText,
                                               FontElement effectiveFont,
                                               ContainerElement.Alignment parentAlignment,
                                               Pane parentPane,
                                               double cardWidthPx) {
        TextFlow flow = new TextFlow();
        flow.getStyleClass().add("text-element");
        bindTextFlowWidth(flow, element, parentPane, cardWidthPx);
        flow.setTextAlignment(mapAlignmentToTextAlignment(parentAlignment));
        flow.getChildren().setAll(createRichTextFragments(mergedText, element, effectiveFont));

        if (effectiveFont != null) {
            flow.rotateProperty().bind(effectiveFont.angleProperty());
        } else {
            flow.rotateProperty().bind(element.angleProperty());
        }
        return flow;
    }

    private static void applyTextStyleBindings(Text text, TextElement element, FontElement effectiveFont) {
        if (effectiveFont != null) {
            text.fontProperty().bind(Bindings.createObjectBinding(
                    () -> Font.font(effectiveFont.getFontFamily(), effectiveFont.getFontWeight(), effectiveFont.getFontPosture(), effectiveFont.getFontSize()),
                    effectiveFont.fontFamilyProperty(), effectiveFont.fontWeightProperty(), effectiveFont.fontPostureProperty(), effectiveFont.fontSizeProperty()
            ));
            text.fillProperty().bind(Bindings.createObjectBinding(
                    () -> safeColor(effectiveFont.getColor(), Color.BLACK), effectiveFont.colorProperty()
            ));
            text.rotateProperty().bind(effectiveFont.angleProperty());
            text.strokeWidthProperty().bind(effectiveFont.outlineWidthProperty());
            text.strokeProperty().bind(Bindings.createObjectBinding(
                    () -> safeColor(effectiveFont.getOutlineColor(), Color.TRANSPARENT), effectiveFont.outlineColorProperty()
            ));
        } else {
            text.fontProperty().bind(Bindings.createObjectBinding(
                    () -> Font.font(element.getFontSize()), element.fontSizeProperty()
            ));
            text.fillProperty().bind(Bindings.createObjectBinding(
                    () -> safeColor(element.getColor(), Color.BLACK), element.colorProperty()
            ));
            text.rotateProperty().bind(element.angleProperty());
            text.strokeWidthProperty().bind(element.outlineWidthProperty());
            text.strokeProperty().bind(Bindings.createObjectBinding(
                    () -> safeColor(element.getOutlineColor(), Color.TRANSPARENT), element.outlineColorProperty()
            ));
        }
    }

    private static void bindTextWrappingWidth(Text text, TextElement element, Pane parentPane, double cardWidthPx) {
        if (parentPane != null) {
            text.wrappingWidthProperty().bind(Bindings.createDoubleBinding(
                    () -> Math.max(0, parentPane.getWidth() - element.getX()),
                    parentPane.widthProperty(), element.xProperty()
            ));
        } else {
            text.wrappingWidthProperty().bind(Bindings.createDoubleBinding(
                    () -> Math.max(0, cardWidthPx - element.getX()),
                    element.xProperty()
            ));
        }
    }

    private static void bindTextFlowWidth(TextFlow flow, TextElement element, Pane parentPane, double cardWidthPx) {
        Observable[] dependencies = parentPane != null
                ? new Observable[]{parentPane.widthProperty(), element.xProperty()}
                : new Observable[]{element.xProperty()};
        DoubleBinding widthBinding = Bindings.createDoubleBinding(
                () -> Math.max(0, (parentPane != null ? parentPane.getWidth() : cardWidthPx) - element.getX()),
                dependencies
        );
        flow.prefWidthProperty().bind(widthBinding);
        flow.maxWidthProperty().bind(widthBinding);
    }

    private static boolean containsRichTextMarkup(String value) {
        return value != null && value.contains("<") && SUPPORTED_TAG_PATTERN.matcher(value).find();
    }

    private static List<Text> createRichTextFragments(String value, TextElement element, FontElement effectiveFont) {
        List<Text> fragments = new ArrayList<>();
        RichTextStyle style = RichTextStyle.base(element, effectiveFont);
        Deque<String> colorStack = new ArrayDeque<>();
        java.util.regex.Matcher matcher = TAG_PATTERN.matcher(value == null ? "" : value);
        int lastPos = 0;

        while (matcher.find()) {
            addRichTextFragment(fragments, value.substring(lastPos, matcher.start()), style);

            String closing = matcher.group(1);
            String tag = matcher.group(2).toLowerCase(Locale.ROOT);
            String attrs = matcher.group(3) == null ? "" : matcher.group(3);
            boolean selfClosing = attrs.trim().endsWith("/");

            if ("br".equals(tag)) {
                addRichTextFragment(fragments, "\n", style);
            } else if (closing != null && !closing.isEmpty()) {
                switch (tag) {
                    case "b" -> style.boldDepth = Math.max(0, style.boldDepth - 1);
                    case "i" -> style.italicDepth = Math.max(0, style.italicDepth - 1);
                    case "u" -> style.underlineDepth = Math.max(0, style.underlineDepth - 1);
                    case "span" -> style.color = colorStack.isEmpty() ? style.baseColor : colorStack.pop();
                    default -> {
                    }
                }
            } else {
                switch (tag) {
                    case "b" -> style.boldDepth++;
                    case "i" -> style.italicDepth++;
                    case "u" -> style.underlineDepth++;
                    case "span" -> {
                        colorStack.push(style.color);
                        String spanColor = extractSpanColor(attrs);
                        if (spanColor != null) {
                            style.color = spanColor;
                        }
                        if (selfClosing) {
                            style.color = colorStack.isEmpty() ? style.baseColor : colorStack.pop();
                        }
                    }
                    default -> {
                    }
                }
            }
            lastPos = matcher.end();
        }

        addRichTextFragment(fragments, value.substring(lastPos), style);
        if (fragments.isEmpty()) {
            addRichTextFragment(fragments, "", style);
        }
        return fragments;
    }

    private static void addRichTextFragment(List<Text> fragments, String rawText, RichTextStyle style) {
        if (rawText == null || rawText.isEmpty()) {
            return;
        }
        Text text = new Text(unescapeBasicHtml(rawText));
        text.setFont(Font.font(
                style.fontFamily,
                style.boldDepth > 0 ? FontWeight.BOLD : style.fontWeight,
                style.italicDepth > 0 ? FontPosture.ITALIC : style.fontPosture,
                style.fontSize
        ));
        text.setFill(safeColor(style.color, Color.BLACK));
        text.setUnderline(style.underlineDepth > 0);
        text.setStrokeWidth(style.outlineWidth);
        text.setStroke(safeColor(style.outlineColor, Color.TRANSPARENT));
        fragments.add(text);
    }

    private static String extractSpanColor(String attrs) {
        java.util.regex.Matcher matcher = COLOR_STYLE_PATTERN.matcher(attrs == null ? "" : attrs);
        if (!matcher.find()) {
            return null;
        }
        String color = matcher.group(1).trim();
        return color.isEmpty() ? null : color;
    }

    private static Color safeColor(String value, Color fallback) {
        try {
            return Color.web(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String unescapeBasicHtml(String value) {
        return value
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }

    private static TextAlignment mapAlignmentToTextAlignment(ContainerElement.Alignment alignment) {
        if (alignment == null) {
            return TextAlignment.LEFT;
        }
        return switch (alignment) {
            case LEFT -> TextAlignment.LEFT;
            case CENTER -> TextAlignment.CENTER;
            case RIGHT -> TextAlignment.RIGHT;
        };
    }

    private static final class RichTextStyle {
        private final String fontFamily;
        private final FontWeight fontWeight;
        private final FontPosture fontPosture;
        private final double fontSize;
        private final String baseColor;
        private final double outlineWidth;
        private final String outlineColor;
        private int boldDepth;
        private int italicDepth;
        private int underlineDepth;
        private String color;

        private RichTextStyle(String fontFamily,
                              FontWeight fontWeight,
                              FontPosture fontPosture,
                              double fontSize,
                              String color,
                              double outlineWidth,
                              String outlineColor) {
            this.fontFamily = fontFamily;
            this.fontWeight = fontWeight;
            this.fontPosture = fontPosture;
            this.fontSize = fontSize;
            this.baseColor = color;
            this.color = color;
            this.outlineWidth = outlineWidth;
            this.outlineColor = outlineColor;
        }

        private static RichTextStyle base(TextElement element, FontElement effectiveFont) {
            if (effectiveFont != null) {
                return new RichTextStyle(
                        effectiveFont.getFontFamily(),
                        effectiveFont.getFontWeight(),
                        effectiveFont.getFontPosture(),
                        effectiveFont.getFontSize(),
                        effectiveFont.getColor(),
                        effectiveFont.getOutlineWidth(),
                        effectiveFont.getOutlineColor()
                );
            }
            return new RichTextStyle(
                    Font.getDefault().getFamily(),
                    FontWeight.NORMAL,
                    FontPosture.REGULAR,
                    element.getFontSize(),
                    element.getColor(),
                    element.getOutlineWidth(),
                    element.getOutlineColor()
            );
        }
    }
}
