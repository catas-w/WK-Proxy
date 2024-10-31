package com.catas.wicked.proxy.gui.componet;

import com.jfoenix.adapters.ReflectionHelper;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.controls.base.IFXLabelFloatControl;
import com.jfoenix.skins.PromptLinesWrapper;
import com.jfoenix.skins.ValidationPane;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ObservableDoubleValue;
import javafx.scene.Node;
import javafx.scene.control.Skin;
import javafx.scene.control.TextField;
import javafx.scene.control.skin.TextFieldSkin;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import lombok.Getter;

import java.lang.reflect.Field;


/**
 * custom jfxTextField to relocate ValidationPane
 * @see ValidationPane#layoutPane
 */
@Getter
public class CustomTextField extends JFXTextField {

    private double errorPaneTranslateX;

    private double errorPaneTranslateY;

    private final CustomTextFieldSkin customSkin = new CustomTextFieldSkin(this, errorPaneTranslateX, errorPaneTranslateY);

    public CustomTextField() {
        super();
        errorPaneTranslateX = 0.0;
        errorPaneTranslateY = 0.0;
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return customSkin;
    }

    public void setErrorPaneTranslateX(double errorPaneTranslateX) {
        this.errorPaneTranslateX = errorPaneTranslateX;
        customSkin.setErrorPaneTranslateX(errorPaneTranslateX);
    }

    public void setErrorPaneTranslateY(double errorPaneTranslateY) {
        this.errorPaneTranslateY = errorPaneTranslateY;
        customSkin.setErrorPaneTranslateY(errorPaneTranslateY);
    }

    public static class CustomTextFieldSkin extends TextFieldSkin {

        private boolean invalid = true;

        private Text promptText;
        private Pane textPane;
        private Node textNode;
        private ObservableDoubleValue textRight;
        private DoubleProperty textTranslateX;

        private DoubleProperty errorPaneTranslateX;
        private DoubleProperty errorPaneTranslateY;

        private ValidationPane<JFXTextField> errorContainer;
        private PromptLinesWrapper<JFXTextField> linesWrapper;

        public CustomTextFieldSkin(JFXTextField textField, double errorPaneTranslateX, double errorPaneTranslateY) {
            this(textField);
            this.errorPaneTranslateX = new SimpleDoubleProperty(errorPaneTranslateX);
            this.errorPaneTranslateY = new SimpleDoubleProperty(errorPaneTranslateY);
        }

        public CustomTextFieldSkin(JFXTextField textField) {
            super(textField);
            textPane = (Pane) this.getChildren().get(0);

            // get parent fields
            textNode = ReflectionHelper.getFieldContent(TextFieldSkin.class, this, "textNode");
            textTranslateX = ReflectionHelper.getFieldContent(TextFieldSkin.class, this, "textTranslateX");
            textRight = ReflectionHelper.getFieldContent(TextFieldSkin.class, this, "textRight");

            linesWrapper = new PromptLinesWrapper<JFXTextField>(
                    textField,
                    promptTextFillProperty(),
                    textField.textProperty(),
                    textField.promptTextProperty(),
                    () -> promptText);

            linesWrapper.init(() -> createPromptNode(), textPane);

            ReflectionHelper.setFieldContent(TextFieldSkin.class, this, "usePromptText", linesWrapper.usePromptText);

            errorContainer = new ValidationPane<>(textField);

            getChildren().addAll(linesWrapper.line, linesWrapper.focusedLine, linesWrapper.promptContainer, errorContainer);

            registerChangeListener(textField.disableProperty(), obs -> linesWrapper.updateDisabled());
            registerChangeListener(textField.focusColorProperty(), obs -> linesWrapper.updateFocusColor());
            registerChangeListener(textField.unFocusColorProperty(), obs -> linesWrapper.updateUnfocusColor());
            registerChangeListener(textField.disableAnimationProperty(), obs -> errorContainer.updateClip());
        }

        public CustomTextFieldSkin(TextField control) {
            super(control);
        }

        @Override
        protected void layoutChildren(final double x, final double y, final double w, final double h) {
            super.layoutChildren(x, y, w, h);

            final double height = getSkinnable().getHeight();
            linesWrapper.layoutLines(x, y, w, h, height, Math.floor(h));
            // errorContainer.layoutPane(x, height + linesWrapper.focusedLine.getHeight(), w, h);
            errorContainer.layoutPane(x + errorPaneTranslateX.get(), height + errorPaneTranslateY.get(), w, h);

            if (getSkinnable().getWidth() > 0) {
                updateTextPos();
            }

            linesWrapper.updateLabelFloatLayout();

            if (invalid) {
                invalid = false;
                // update validation container
                errorContainer.invalid(w);
                // focus
                linesWrapper.invalid();
            }
        }


        private void updateTextPos() {
            double textWidth = textNode.getLayoutBounds().getWidth();
            final double promptWidth = promptText == null ? 0 : promptText.getLayoutBounds().getWidth();
            switch (getSkinnable().getAlignment().getHpos()) {
                case CENTER:
                    linesWrapper.promptTextScale.setPivotX(promptWidth / 2);
                    double midPoint = textRight.get() / 2;
                    double newX = midPoint - textWidth / 2;
                    if (newX + textWidth <= textRight.get()) {
                        textTranslateX.set(newX);
                    }
                    break;
                case LEFT:
                    linesWrapper.promptTextScale.setPivotX(0);
                    break;
                case RIGHT:
                    linesWrapper.promptTextScale.setPivotX(promptWidth);
                    break;
            }

        }

        private void createPromptNode() {
            if (promptText != null || !linesWrapper.usePromptText.get()) {
                return;
            }
            promptText = new Text();
            promptText.setManaged(false);
            promptText.getStyleClass().add("text");
            promptText.visibleProperty().bind(linesWrapper.usePromptText);
            promptText.fontProperty().bind(getSkinnable().fontProperty());
            promptText.textProperty().bind(getSkinnable().promptTextProperty());
            promptText.fillProperty().bind(linesWrapper.animatedPromptTextFill);
            promptText.setLayoutX(1);
            promptText.getTransforms().add(linesWrapper.promptTextScale);
            linesWrapper.promptContainer.getChildren().add(promptText);
            if (getSkinnable().isFocused() && ((IFXLabelFloatControl) getSkinnable()).isLabelFloat()) {
                promptText.setTranslateY(-Math.floor(textPane.getHeight()));
                linesWrapper.promptTextScale.setX(0.85);
                linesWrapper.promptTextScale.setY(0.85);
            }

            try {
                Field field = ReflectionHelper.getField(TextFieldSkin.class, "promptNode");
                Object oldValue = field.get(this);
                if (oldValue != null) {
                    textPane.getChildren().remove(oldValue);
                }
                field.set(this, promptText);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void setErrorPaneTranslateX(double errorPaneTranslateX) {
            this.errorPaneTranslateX.set(errorPaneTranslateX);
        }

        public void setErrorPaneTranslateY(double errorPaneTranslateY) {
            this.errorPaneTranslateY.set(errorPaneTranslateY);
        }
    }
}
