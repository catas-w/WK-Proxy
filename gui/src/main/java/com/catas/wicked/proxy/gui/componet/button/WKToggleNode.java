package com.catas.wicked.proxy.gui.componet.button;

import com.catas.wicked.common.constant.StyleConstant;
import com.jfoenix.controls.JFXToggleNode;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Skin;
import javafx.scene.control.skin.ToggleButtonSkin;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;


@Slf4j
public class WKToggleNode extends JFXToggleNode {


    private final StringProperty labelText = new SimpleStringProperty();

    private final StringProperty iconLiteral = new SimpleStringProperty("");

    private final ObjectProperty<Paint> iconColor = new SimpleObjectProperty<>(Color.valueOf(StyleConstant.INACTIVE_COLOR));

    private final StringProperty activeIconLiteral = new SimpleStringProperty("");

    private final StringProperty inactiveIconLiteral = new SimpleStringProperty("");

    private final StringProperty activeColor = new SimpleStringProperty(StyleConstant.ACTIVE_COLOR);

    private final StringProperty inactiveColor = new SimpleStringProperty(StyleConstant.INACTIVE_COLOR);

    public WKToggleNode() {
        UnderLabelWrapper underLabelWrapper = new UnderLabelWrapper(iconLiteral, labelText, iconColor);
        this.setGraphic(underLabelWrapper);

        // set active style
        this.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                if (StringUtils.isNotBlank(activeIconLiteral.get())) {
                    iconLiteral.setValue(activeIconLiteral.get());
                }
                iconColor.setValue(Color.valueOf(activeColor.get()));
            } else {
                if (StringUtils.isNotBlank(inactiveIconLiteral.get())) {
                    iconLiteral.setValue(inactiveIconLiteral.get());
                }
                iconColor.setValue(Color.valueOf(inactiveColor.get()));
            }
        });
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        // fix: ripplier color remains bug
        return new ToggleButtonSkin(this);
    }

    public void setIconColor(Color color) {
        this.iconColor.setValue(color);
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

    public String getActiveIconLiteral() {
        return activeIconLiteral.get();
    }

    public StringProperty activeIconLiteralProperty() {
        return activeIconLiteral;
    }

    public void setActiveIconLiteral(String activeIconLiteral) {
        this.activeIconLiteral.set(activeIconLiteral);
        if (this.isSelected()) {
            this.iconColor.setValue(Color.valueOf(activeColor.get()));
        }
    }

    public String getInactiveIconLiteral() {
        return inactiveIconLiteral.get();
    }

    public StringProperty inactiveIconLiteralProperty() {
        return inactiveIconLiteral;
    }

    public void setInactiveIconLiteral(String inactiveIconLiteral) {
        this.inactiveIconLiteral.set(inactiveIconLiteral);
        if (!this.isSelected()) {
            this.iconColor.setValue(Color.valueOf(inactiveColor.get()));
        }
    }

    public String getActiveColor() {
        return activeColor.get();
    }

    public StringProperty activeColorProperty() {
        return activeColor;
    }

    public void setActiveColor(String activeColor) {
        this.activeColor.set(activeColor);
    }

    public String getInactiveColor() {
        return inactiveColor.get();
    }

    public StringProperty inactiveColorProperty() {
        return inactiveColor;
    }

    public void setInactiveColor(String inactiveColor) {
        this.inactiveColor.set(inactiveColor);
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
}
