package com.catas.wicked.proxy.gui.componet.button;

import com.catas.wicked.common.constant.StyleConstant;
import com.jfoenix.controls.JFXButton;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Skin;
import javafx.scene.control.skin.ButtonSkin;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WkButton extends JFXButton {

    private final StringProperty iconLiteral = new SimpleStringProperty("");

    private final StringProperty labelText = new SimpleStringProperty("");

    private final ObjectProperty<Paint> iconColor =
            new SimpleObjectProperty<>(Color.valueOf(StyleConstant.INACTIVE_COLOR));

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ButtonSkin(this);
    }

    public WkButton() {
        this.getStyleClass().add("wk-button");

        UnderLabelWrapper underLabelWrapper = new UnderLabelWrapper(iconLiteral, labelText, iconColor);
        this.setGraphic(underLabelWrapper);
    }

    public String getIconLiteral() {
        return iconLiteral.get();
    }

    public StringProperty iconLiteralProperty() {
        return iconLiteral;
    }

    public void setIconLiteral(String iconLiteral) {
        this.iconLiteral.set(iconLiteral);
    }

    public String getLabelText() {
        return labelText.get();
    }

    public StringProperty labelTextProperty() {
        return labelText;
    }

    public void setLabelText(String labelText) {
        this.labelText.set(labelText);
    }

    public ObjectProperty<Paint> iconColorProperty() {
        return iconColor;
    }

    public void setIconColor(Paint iconColor) {
        this.iconColor.set(iconColor);
    }
}
