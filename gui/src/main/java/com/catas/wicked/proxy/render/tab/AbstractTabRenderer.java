package com.catas.wicked.proxy.render.tab;

import com.catas.wicked.common.bean.HeaderEntry;
import com.catas.wicked.common.util.TableUtils;
import com.catas.wicked.proxy.gui.componet.MessageLabel;
import com.catas.wicked.proxy.gui.componet.SideBar;
import com.catas.wicked.proxy.render.TabRenderer;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import org.apache.http.entity.ContentType;

import java.util.Map;

public abstract class AbstractTabRenderer implements TabRenderer {

    protected void renderHeaders(Map<String, String> headers, TableView<HeaderEntry> tableView) {
        ObservableList<HeaderEntry> list = TableUtils.headersConvert(headers);
        Platform.runLater(() -> {
            if (!tableView.getColumns().isEmpty()) {
                tableView.setItems(list);
            }
        });
    }

    protected void renderHeaders(ObservableList<HeaderEntry> list, TableView<HeaderEntry> tableView) {
        if (!tableView.getColumns().isEmpty()) {
            tableView.setItems(list);
        }
    }

    protected void setEmptyMsgLabel(MessageLabel msgLabel) {
        setMsgLabel(msgLabel, "Empty");
    }

    protected void setMsgLabel(MessageLabel msgLabel, String msg) {
        Platform.runLater(() -> {
            msgLabel.setText(msg);
            msgLabel.setVisible(true);
        });
    }

    protected void setMsgLabel(MessageLabel msgLabel, String msg, HBox labelBox) {
        Platform.runLater(() -> {
            msgLabel.setText(msg);
            msgLabel.setVisible(true);
            labelBox.setVisible(true);
        });
    }

    protected SideBar.Strategy predictCodeStyle(ContentType contentType, int contentLength) {
        if (contentType == null) {
            return SideBar.Strategy.TEXT;
        }
        String mimeType = contentType.getMimeType();
        if (mimeType.contains("multipart/form-data")) {
            return SideBar.Strategy.MULTIPART_FORM_DATA;
        } else if (mimeType.contains("x-www-form-urlencoded")) {
            return SideBar.Strategy.URLENCODED_FORM_DATA;
        } else if (mimeType.startsWith("image")) {
            return SideBar.Strategy.IMG;
        } else if (mimeType.contains("zip") || mimeType.startsWith("audio") || mimeType.startsWith("video")) {
            return SideBar.Strategy.BINARY;
        }

        // display as plain text if too long
        if (contentLength > 3500) {
            return SideBar.Strategy.TEXT;
        }
        if (mimeType.contains("json")) {
            return SideBar.Strategy.JSON;
        } else if (mimeType.contains("xml")) {
            return SideBar.Strategy.XML;
        } else if (mimeType.contains("html")) {
            return SideBar.Strategy.HTML;
        }
        return SideBar.Strategy.TEXT;
    }
}
