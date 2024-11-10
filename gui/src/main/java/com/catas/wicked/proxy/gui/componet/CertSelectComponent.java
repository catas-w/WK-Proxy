package com.catas.wicked.proxy.gui.componet;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXRadioButton;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Background;
import javafx.scene.layout.Border;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.Consumer;


public class CertSelectComponent extends HBox {

    private final CertRadioButton radioBtn;
    private Pane pane = new Pane();
    private final Label label = new Label();
    private final Label clickLabel = new Label();
    private JFXButton previewBtn = new JFXButton();
    private JFXButton operateBtn = new JFXButton();

    public CertSelectComponent(String option, String certId, String operateIconStr) {
        radioBtn = new CertRadioButton(option, certId);
        radioBtn.getStyleClass().add("cert-radio-btn");

        // pane.setBorder(Border.stroke(Color.valueOf("#00ea00")));
        HBox.setHgrow(pane, Priority.ALWAYS);

        // preview btn
        FontIcon previewIcon = new FontIcon();
        previewIcon.setIconLiteral("fas-eye");
        previewBtn.setGraphic(previewIcon);
        Tooltip previewToolTip = new Tooltip("Preview");
        previewToolTip.setShowDelay(Duration.millis(100));
        previewBtn.setTooltip(previewToolTip);

        // operate btn
        FontIcon operateIcon = new FontIcon();
        operateIcon.setIconLiteral(operateIconStr);
        operateBtn.setGraphic(operateIcon);
        Tooltip operateToolTip = new Tooltip();
        operateToolTip.setShowDelay(Duration.millis(100));
        operateBtn.setTooltip(operateToolTip);

        label.getStyleClass().add("alert-label");
        clickLabel.getStyleClass().add("click-label");
        previewBtn.getStyleClass().add("preview-btn");
        operateBtn.getStyleClass().add("operate-btn");

        this.getStyleClass().add("grid-element");
        this.getChildren().addAll(radioBtn, label, clickLabel, pane, previewBtn, operateBtn);
    }

    public void setToggleGroup(ToggleGroup toggleGroup) {
        if (toggleGroup != null) {
            this.radioBtn.setToggleGroup(toggleGroup);
        }
    }

    public void setAlertLabel(String str, String clickStr) {
        this.label.setText(str);
        FontIcon icon = new FontIcon();
        icon.setIconLiteral("fas-exclamation-triangle");
        this.label.setGraphic(icon);

        this.clickLabel.setText(clickStr);
        this.clickLabel.setVisible(true);
    }

    public void setOnClickLabelAction(Consumer<Event> consumer) {
        clickLabel.setOnMouseClicked(consumer::accept);
    }

    public void setOperateIcon(String iconStr) {
        if (StringUtils.isBlank(iconStr)) {
            return;
        }
        FontIcon icon = new FontIcon();
        icon.setIconLiteral(iconStr);
        operateBtn.setGraphic(icon);
    }

    public void setOperateEvent(Consumer<ActionEvent> consumer) {
        if (consumer != null) {
            this.operateBtn.setOnAction(consumer::accept);
        }
    }

    public void setOperateTooltip(String toolTip) {
        this.operateBtn.getTooltip().setText(toolTip);
    }

    public void setPreviewTooltip(String tooltip) {
        this.previewBtn.getTooltip().setText(tooltip);
    }

    public void setPreviewEvent(Consumer<ActionEvent> consumer) {
        if (consumer != null) {
            this.previewBtn.setOnAction(consumer::accept);
        }
    }

    public void setSelected(boolean value) {
        this.radioBtn.setSelected(value);
    }

    @Getter
    public static class CertRadioButton extends JFXRadioButton {

        private final String certId;

        public CertRadioButton(String text, String certId) {
            super(text);
            this.certId = certId;
        }
    }
}
