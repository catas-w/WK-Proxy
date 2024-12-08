package com.catas.wicked.common.bean;

import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject;
import javafx.beans.property.FloatProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * data module for tableView
 */
public class PairEntry extends RecursiveTreeObject<PairEntry> {

    @Getter
    @AllArgsConstructor
    public enum ColumnStyle {

        OK("ok", "fas-check-circle"),
        PENDING("pending", "fas-minus-circle"),
        ERROR("error", "fas-times-circle"),
        ;

        private final String styleClass;
        private final String iconStr;

        public static ColumnStyle getByStyle(String style) {
            for (ColumnStyle value : ColumnStyle.values()) {
                if (value.getStyleClass().equals(style)) {
                    return value;
                }
            }
            return null;
        }
    }

    private StringProperty key;
    private StringProperty val;
    private FloatProperty time;
    private StringProperty tooltip;

    private final SimpleObjectProperty<ColumnStyle> columnStyle = new SimpleObjectProperty<>(null);

    public PairEntry(String key, String val) {
        this.key = new SimpleStringProperty(key);
        this.val = new SimpleStringProperty(val);
    }

    public PairEntry(String key) {
        this.key = new SimpleStringProperty(key);
        this.val = new SimpleStringProperty("-");
    }

    public PairEntry(String key, String val, String toolTip) {
        this.key = new SimpleStringProperty(key);
        this.val = new SimpleStringProperty(val);
        this.tooltip = new SimpleStringProperty(toolTip);
    }

    public String getKey() {
        return key.get();
    }

    public StringProperty keyProperty() {
        return key;
    }

    public void setKey(String key) {
        this.key.set(key);
    }

    public String getVal() {
        return val.get();
    }

    public StringProperty valProperty() {
        return val;
    }

    public void setVal(String val) {
        this.val.set(val);
    }

    public String getTooltip() {
        return tooltip.get();
    }

    public StringProperty tooltipProperty() {
        return tooltip;
    }

    public void setTooltip(String tooltip) {
        this.tooltip.set(tooltip);
    }

    public ColumnStyle getColumnStyle() {
        return columnStyle.get();
    }

    public SimpleObjectProperty<ColumnStyle> columnStyleProperty() {
        return columnStyle;
    }

    public void setColumnStyle(ColumnStyle columnStyle) {
        this.columnStyle.set(columnStyle);
    }
}
