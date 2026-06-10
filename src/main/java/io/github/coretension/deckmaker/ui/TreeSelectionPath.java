package io.github.coretension.deckmaker.ui;

import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

/**
 * Captures and restores tree selection by index path instead of object identity.
 */
final class TreeSelectionPath {
    private TreeSelectionPath() {
    }

    static <T> List<Integer> capture(TreeView<T> treeView) {
        TreeItem<T> selected = treeView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return List.of();
        }

        LinkedList<Integer> path = new LinkedList<>();
        TreeItem<T> current = selected;
        while (current.getParent() != null) {
            TreeItem<T> parent = current.getParent();
            path.addFirst(parent.getChildren().indexOf(current));
            current = parent;
        }
        return path;
    }

    static <T> Optional<TreeItem<T>> resolve(TreeView<T> treeView, List<Integer> path) {
        TreeItem<T> item = treeView.getRoot();
        for (int index : path) {
            if (item == null || index < 0 || index >= item.getChildren().size()) {
                return Optional.empty();
            }
            item = item.getChildren().get(index);
        }
        return Optional.ofNullable(item);
    }
}
