package com.catas.wicked.proxy.gui.componet.button;

import com.jfoenix.controls.JFXButton;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Skin;
import javafx.scene.control.skin.ButtonSkin;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WkButton extends JFXButton {

    private final StringProperty iconLiteral = new SimpleStringProperty("");

    private final StringProperty labelText = new SimpleStringProperty("");

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ButtonSkin(this);
    }

    public WkButton() {
        this.getStyleClass().add("wk-button");

        UnderLabelWrapper underLabelWrapper = new UnderLabelWrapper(iconLiteral, labelText);
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
}
