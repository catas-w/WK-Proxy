package com.catas.wicked.proxy.gui.componet;

import com.catas.wicked.common.constant.CodeStyle;
import com.catas.wicked.proxy.gui.componet.highlight.CodeStyleLabeled;
import com.catas.wicked.proxy.gui.componet.richtext.DisplayCodeArea;
import com.jfoenix.controls.JFXToggleNode;
import javafx.animation.Animation;
import javafx.animation.RotateTransition;
import javafx.animation.Transition;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;

public class SideBar extends HBox {

    /**
     * Defines which components to show/hide
     */
    public enum Strategy {
        QUERY_PARAMS(false, CodeStyle.QUERY_FORM, List.of(CodeStyle.QUERY_FORM, CodeStyle.ORIGIN)),
        MULTIPART_FORM_DATA(false, CodeStyle.MULTIPART_FORM, List.of(CodeStyle.MULTIPART_FORM, CodeStyle.ORIGIN)),
        URLENCODED_FORM_DATA(false, CodeStyle.QUERY_FORM, List.of(CodeStyle.QUERY_FORM, CodeStyle.ORIGIN)),

        TEXT(true, CodeStyle.ORIGIN, List.of(CodeStyle.ORIGIN, CodeStyle.HEX)),
        JSON(true, CodeStyle.JSON, List.of(CodeStyle.ORIGIN, CodeStyle.HEX)),
        XML(true, CodeStyle.XML, List.of(CodeStyle.ORIGIN, CodeStyle.HEX)),
        HTML(true, CodeStyle.HTML, List.of(CodeStyle.ORIGIN, CodeStyle.HEX)),

        BINARY(false, CodeStyle.ORIGIN, List.of(CodeStyle.ORIGIN, CodeStyle.HEX)),
        IMG(false, null, Collections.emptyList());

        private final List<CodeStyle> visibleList;
        private final boolean showCombo;
        private final CodeStyle preset;

        Strategy(boolean showCombo, CodeStyle preset, List<CodeStyle> visibleList) {
            this.showCombo = showCombo;
            this.preset = preset;
            this.visibleList = visibleList;
        }
    }

    @FXML
    private Button collapseBtn;

    @FXML
    private JFXToggleNode wrapBtn;

    @FXML
    private DropShadow shadow;

    private CodeStyle codeStyle;

    private DisplayCodeArea targetCodeArea;

    private static final String SELECTED_STYLE = "selected";

    public SideBar() {
        URL resource = getClass().getResource("/fxml/component/side_bar.fxml");
        FXMLLoader fxmlLoader = new FXMLLoader(resource);
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }

        collapseBtn.setOnAction(new SideBarEventHandler(collapseBtn, this));

        wrapBtn.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                return;
            }
            if (targetCodeArea != null) {
                targetCodeArea.setWrapText(newValue);
            }
            String color = newValue ? "#33cbb9" : "#616161";
            ((FontIcon) wrapBtn.getGraphic()).setIconColor(Color.web(color));
        });

        ObservableList<Node> children = getChildren();
        children.addListener(new ListChangeListener<Node>() {
            @Override
            public void onChanged(Change<? extends Node> c) {
                while (c.next()) {
                    if (c.wasAdded()) {
                        // add listener when child is added
                        Node node = c.getAddedSubList().get(0);
                        if (node == collapseBtn || node == wrapBtn) {
                            return;
                        }
                        node.setOnMouseClicked(event -> {
                            if (node instanceof ComboBox<?> comboBox) {
                                ComboBox<CodeStyleLabeled> labelComboBox = (ComboBox<CodeStyleLabeled>) comboBox;
                                CodeStyleLabeled selectedItem = labelComboBox.getSelectionModel().getSelectedItem();
                                setCodeStyle(selectedItem.targetCodeStyle(), true, true);
                            } else if (node instanceof CodeStyleLabeled codeStyleLabeled) {
                                setCodeStyle(codeStyleLabeled.targetCodeStyle(), true, true);
                            }
                        });
                        node.managedProperty().bind(node.visibleProperty());
                        Platform.runLater(() -> {
                            getChildren().remove(wrapBtn);
                            getChildren().add(wrapBtn);
                        });
                    }
                }
            }
        });
    }

    @Override
    protected double computeMinWidth(double height) {
        return super.computeMinWidth(height);
    }

    public void setTargetCodeArea(DisplayCodeArea targetCodeArea) {
        this.targetCodeArea = targetCodeArea;
    }

    /**
     * set strategy
     */
    public void setStrategy(Strategy strategy) {

        if (strategy.preset == null) {
            this.setVisible(false);
            return;
        }
        this.setVisible(true);

        ObservableList<Node> children = getChildren();
        for (Node child : children) {
            if (child == collapseBtn) {
                continue;
            }
            if (child instanceof ComboBox<?>) {
                child.setVisible(strategy.showCombo);
            } else if (child instanceof CodeStyleLabeled labeled) {
                child.setVisible(strategy.visibleList.contains(labeled.targetCodeStyle()));
            }
        }

        setCodeStyle(strategy.preset, true, false);
    }

    /**
     * set codeStyle of related CodeArea
     * @param codeStyle codeStyle
     * @param refreshSelectStyle refresh selected styleClass in children
     */
    public void setCodeStyle(CodeStyle codeStyle, boolean refreshSelectStyle, boolean refreshCodeArea) {
        if (codeStyle == null) {
            return;
        }
        this.codeStyle = codeStyle;
        if (refreshSelectStyle) {
            refreshSelectStyle();
        }

        if (targetCodeArea != null) {
            targetCodeArea.setCodeStyle(codeStyle, refreshCodeArea);
        }
    }

    /**
     * set selected styleClass in children
     */
    public void refreshSelectStyle() {
        // refresh select style
        for (Node child : getChildren()) {
            if (child instanceof CodeStyleLabeled codeStyleLabeled) {
                if (this.codeStyle == codeStyleLabeled.targetCodeStyle()) {
                    if (!child.getStyleClass().contains(SELECTED_STYLE)) {
                        child.getStyleClass().add(SELECTED_STYLE);
                    }
                } else {
                    child.getStyleClass().remove(SELECTED_STYLE);
                }
            } else if (child instanceof ComboBox<?> comboBox) {
                ComboBox<CodeStyleLabeled> labelComboBox = (ComboBox<CodeStyleLabeled>) comboBox;
                comboBox.getStyleClass().remove(SELECTED_STYLE);
                for (CodeStyleLabeled item : labelComboBox.getItems()) {
                    if (this.codeStyle == item.targetCodeStyle()) {
                        comboBox.getStyleClass().add(SELECTED_STYLE);
                        Platform.runLater(() -> {
                            labelComboBox.getSelectionModel().select(item);
                        });
                    }
                }
            }
        }
    }

    static class SideBarEventHandler implements EventHandler<ActionEvent> {

        private Button collapseBtn;
        private double expandedWidth;
        private SideBar sideBar;
        private double minWidth;
        private boolean collapsed;
        private static final String COLLAPSE_ICON = "fas-angle-double-right";
        private static final String EXPAND_ICON = "fas-angle-double-left";
        private static final double DURATION = 500;

        private RotateTransition rotateTransition;

        public SideBarEventHandler(Button collapseBtn, SideBar sideBar) {
            this.collapseBtn = collapseBtn;
            this.sideBar = sideBar;
            this.minWidth = collapseBtn.getMinWidth();
        }

        private void rotateIcon(FontIcon icon) {
            RotateTransition rotateTransition = new RotateTransition(Duration.millis(300), icon);
            rotateTransition.setByAngle(180); // Rotate by 180 degrees
            rotateTransition.setCycleCount(1); // Number of rotations
            rotateTransition.setAutoReverse(false);
            rotateTransition.play();
        }

        @Override
        public void handle(ActionEvent event) {
            expandedWidth = sideBar.getWidth();

            // collapse animation
            final Animation hideSidebar = new Transition() {
                {
                    setCycleDuration(Duration.millis(DURATION));
                }

                @Override
                protected void interpolate(double frac) {
                    final double remainWidth = expandedWidth * (1.0 - frac);
                    final double curWidth = expandedWidth * frac;
                    if (remainWidth <= minWidth) {
                        return;
                    }
                    // sideBar.setPrefWidth(curWidth);
                    // collapse sideBar to minWidth
                    sideBar.setTranslateX(curWidth);
                }
            };

            hideSidebar.onFinishedProperty().set(actionEvent -> {
                collapsed = true;
                sideBar.setTranslateX(expandedWidth - minWidth);
                // for (Node child : sideBar.getChildren()) {
                //     if (child != collapseBtn) {
                //         child.setVisible(false);
                //     }
                // }
                FontIcon icon = (FontIcon) collapseBtn.getGraphic();
                rotateIcon(icon);
            });

            // expand animation
            final Animation showSidebar = new Transition() {
                {
                    setCycleDuration(Duration.millis(250));
                }

                @Override
                protected void interpolate(double frac) {
                    final double curWidth = expandedWidth * frac;
                    if (curWidth >= expandedWidth - minWidth) {
                        return;
                    }
                    // sideBar.setPrefWidth(curWidth);
                    sideBar.setTranslateX(expandedWidth - curWidth - minWidth);
                }
            };

            showSidebar.onFinishedProperty().set(actionEvent -> {
                sideBar.setTranslateX(0);
                FontIcon icon = (FontIcon) collapseBtn.getGraphic();
                rotateIcon(icon);
            });

            if (showSidebar.statusProperty().get() == Animation.Status.STOPPED
                    && hideSidebar.statusProperty().get() == Animation.Status.STOPPED) {
                if (!collapsed) {
                    hideSidebar.play();
                } else {
                    // for (Node child : sideBar.getChildren()) {
                    //     child.setVisible(true);
                    // }
                    collapsed = false;
                    showSidebar.play();
                }
            }
        }
    }
}
