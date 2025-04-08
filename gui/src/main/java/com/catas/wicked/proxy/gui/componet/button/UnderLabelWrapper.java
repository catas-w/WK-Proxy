package com.catas.wicked.proxy.gui.componet.button;


import com.catas.wicked.common.constant.StyleConstant;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Text;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Objects;

@Slf4j
public class UnderLabelWrapper extends VBox {

    private final StackPane background;

    private final FontIcon icon;

    private final Text label;

    private final IntegerProperty iconSizeProperty;

    private final StringProperty labelTextProperty;

    private final StringProperty iconLiteralProperty;

    private final ObjectProperty<Paint> iconColorProperty;

    public UnderLabelWrapper(StringProperty iconLiteral, StringProperty labelText) {
        this(iconLiteral, labelText, Color.valueOf(StyleConstant.INACTIVE_COLOR));
    }

    public UnderLabelWrapper(StringProperty iconLiteral, StringProperty labelText, Paint iconColor) {
        this(iconLiteral, labelText,
                new SimpleObjectProperty<>(iconColor), new SimpleIntegerProperty(18));
    }

    public UnderLabelWrapper(StringProperty iconLiteral, StringProperty labelText, ObjectProperty<Paint> iconColor) {
        this(iconLiteral, labelText, iconColor, new SimpleIntegerProperty(18));
    }

    public UnderLabelWrapper(StringProperty iconLiteral,
                             StringProperty labelText,
                             ObjectProperty<Paint> iconColor,
                             IntegerProperty iconSize) {
        this.iconLiteralProperty = Objects.requireNonNull(iconLiteral);
        this.labelTextProperty = Objects.requireNonNull(labelText);
        this.iconColorProperty = Objects.requireNonNull(iconColor);
        this.iconSizeProperty = Objects.requireNonNull(iconSize);

        // icon
        icon = new FontIcon();
        icon.setOpacity(0.8);
        icon.iconSizeProperty().bind(iconSizeProperty);
        icon.iconColorProperty().bind(iconColorProperty);
        iconLiteralProperty.addListener((observable, oldValue, newValue) -> {
            if (StringUtils.isNotBlank(newValue)) {
                icon.setIconLiteral(newValue);
            }
        });
        icon.getStyleClass().add("wk-icon");

        // stackPane
        background = new StackPane();
        Pane pane = new Pane();
        pane.setOpacity(0);
        pane.getStyleClass().add("wk-node-background");
        background.getChildren().addAll(pane, icon);
        // background.getChildren().addAll(icon, pane);

        this.setOnMouseEntered(event -> pane.setOpacity(0.5));
        this.setOnMouseExited(event -> pane.setOpacity(0));


        // underline label
        label = new Text();
        label.getStyleClass().add("wk-node-label");
        label.textProperty().bind(this.labelTextProperty);
        VBox.setMargin(label, new Insets(0, 0, 0, 0));

        this.setAlignment(Pos.CENTER);
        this.getChildren().addAll(background, label);
        this.getStyleClass().add("wk-node");
    }

    public BooleanProperty getLabelVisibleProperty() {
        return label.visibleProperty();
    }

    public String getLabelTextProperty() {
        return labelTextProperty.get();
    }

    public StringProperty labelTextPropertyProperty() {
        return labelTextProperty;
    }

    public void setLabelTextProperty(String labelTextProperty) {
        this.labelTextProperty.set(labelTextProperty);
    }

    public String getIconLiteralProperty() {
        return iconLiteralProperty.get();
    }

    public StringProperty iconLiteralPropertyProperty() {
        return iconLiteralProperty;
    }

    public void setIconLiteralProperty(String iconLiteralProperty) {
        this.iconLiteralProperty.set(iconLiteralProperty);
    }

    public Paint getIconColorProperty() {
        return iconColorProperty.get();
    }

    public ObjectProperty<Paint> iconColorPropertyProperty() {
        return iconColorProperty;
    }

    public void setIconColorProperty(Paint iconColorProperty) {
        this.iconColorProperty.set(iconColorProperty);
    }

    public int getIconSizeProperty() {
        return iconSizeProperty.get();
    }

    public IntegerProperty iconSizePropertyProperty() {
        return iconSizeProperty;
    }

    public void setIconSizeProperty(int iconSizeProperty) {
        this.iconSizeProperty.set(iconSizeProperty);
    }
}
