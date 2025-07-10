package com.catas.wicked.proxy.gui.componet;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Background;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;


/**
 * Always locate in center of parent anchorPane
 */
public class MessageLabel extends Label {

    private static final String STYLE = "message-label";

    public MessageLabel() {
        super();
        init();
    }

    public MessageLabel(String text) {
        super(text);
        init();
    }

    public MessageLabel(String text, Node graphic) {
        super(text, graphic);
        init();
    }

    private void init() {
        this.getStyleClass().add(STYLE);
        this.setMaxWidth(Double.MAX_VALUE);
        this.setMaxHeight(Double.MAX_VALUE);
        AnchorPane.setLeftAnchor(this, 0.0);
        AnchorPane.setRightAnchor(this, 0.0);
        AnchorPane.setTopAnchor(this, 0.0);
        AnchorPane.setBottomAnchor(this, 0.0);
        this.setAlignment(Pos.CENTER);
    }

}
