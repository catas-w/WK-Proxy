package com.catas.wicked.proxy.gui.componet;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.TreeItem;

import java.lang.reflect.Field;

/**
 * Enable treeView filterable
 * From: JFX
 * @param <T>
 */
public class FilterableTreeItem<T> extends TreeItem<T> {
    final private ObservableList<TreeItem<T>> sourceList;
    private FilteredList<TreeItem<T>> filteredList;
    private ObjectProperty<TreeItemPredicate<T>> predicate = new SimpleObjectProperty<>();

    public FilterableTreeItem() {
        this(null);
    }

    public FilterableTreeItem(T value) {
        super(value);
        this.sourceList = FXCollections.observableArrayList();
        this.filteredList = new FilteredList<>(this.sourceList);
        this.filteredList.predicateProperty().bind(Bindings.createObjectBinding(() -> {
            return child -> {
                // Set the predicate of child items to force filtering
                if (child instanceof FilterableTreeItem) {
                    FilterableTreeItem<T> filterableChild = (FilterableTreeItem<T>) child;
                    filterableChild.setPredicate(this.predicate.get());
                }
                // If there is no predicate, keep this tree item
                if (this.predicate.get() == null)
                    return true;
                // If there are children, keep this tree item
                if (child.getChildren().size() > 0)
                    return true;
                // Otherwise ask the TreeItemPredicate
                return this.predicate.get().test(this, child.getValue());
            };
        }, this.predicate));
        setHiddenFieldChildren(this.filteredList);
    }

    protected void setHiddenFieldChildren(ObservableList<TreeItem<T>> list) {
        try {
            Field childrenField = TreeItem.class.getDeclaredField("children");
            childrenField.setAccessible(true);
            childrenField.set(this, list);

            Field declaredField = TreeItem.class.getDeclaredField("childrenListener");
            declaredField.setAccessible(true);
            list.addListener((ListChangeListener<? super TreeItem<T>>) declaredField.get(this));
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            throw new RuntimeException("Could not set TreeItem.children", e);
        }
    }

    public ObservableList<TreeItem<T>> getInternalChildren() {
        return this.sourceList;
    }

    public void setPredicate(TreeItemPredicate<T> predicate) {
        this.predicate.set(predicate);
    }

    public TreeItemPredicate getPredicate() {
        return predicate.get();
    }

    public ObjectProperty<TreeItemPredicate<T>> predicateProperty() {
        return predicate;
    }
}
