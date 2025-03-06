package com.catas.wicked.proxy.gui.componet.button;

import com.catas.wicked.common.constant.StyleConstant;
import com.jfoenix.controls.JFXToggleNode;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Skin;
import javafx.scene.control.skin.ToggleButtonSkin;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.kordamp.ikonli.javafx.FontIcon;


@Slf4j
public class WKToggleNode extends JFXToggleNode {

    private static final int TOOL_TIP_DELAY = 100;

    private final FontIcon icon;

    private int iconSize = 18;

    private final StringProperty labelText = new SimpleStringProperty();

    private final StringProperty iconLiteral = new SimpleStringProperty("");

    private final StringProperty activeIconLiteral = new SimpleStringProperty("");

    private final StringProperty inactiveIconLiteral = new SimpleStringProperty("");

    private final StringProperty activeColor = new SimpleStringProperty(StyleConstant.ACTIVE_COLOR);

    private final StringProperty inactiveColor = new SimpleStringProperty(StyleConstant.INACTIVE_COLOR);

    public WKToggleNode() {
        icon = new FontIcon();
        icon.setIconSize(iconSize);
        icon.setIconColor(Color.valueOf(inactiveColor.get()));
        icon.setOpacity(0.8);

        // Label label = new Label();
        Text label = new Text();
        label.getStyleClass().add("wk-node-label");
        label.textProperty().bind(this.labelTextProperty());
        VBox.setMargin(label, new Insets(2, 0, 0, 0));

        VBox vBox = new VBox();
        vBox.setAlignment(Pos.CENTER);
        vBox.getChildren().addAll(this.icon, label);
        this.setGraphic(vBox);
        this.getStyleClass().add("wk-node");

        // set active style
        this.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                if (StringUtils.isNotBlank(activeIconLiteral.get())) {
                    this.icon.setIconLiteral(activeIconLiteral.get());
                }
                this.icon.setIconColor(Color.valueOf(activeColor.get()));
            } else {
                if (StringUtils.isNotBlank(inactiveIconLiteral.get())) {
                    this.icon.setIconLiteral(inactiveIconLiteral.get());
                }
                this.icon.setIconColor(Color.valueOf(inactiveColor.get()));
            }
        });
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        // fix: ripplier color remains bug
        return new ToggleButtonSkin(this);
    }

    public void setIconColor(Color color) {
        this.icon.setIconColor(color);
    }

    public void setIconSize(int iconSize) {
        this.iconSize = iconSize;
        this.icon.setIconSize(iconSize);
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
            this.icon.setIconLiteral(activeIconLiteral);
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
            this.icon.setIconLiteral(inactiveIconLiteral);
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
        this.icon.setIconLiteral(iconLiteral);
    }
}
