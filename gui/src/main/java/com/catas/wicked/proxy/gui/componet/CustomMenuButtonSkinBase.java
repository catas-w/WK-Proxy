package com.catas.wicked.proxy.gui.componet;

import com.sun.javafx.scene.NodeHelper;
import com.sun.javafx.scene.control.ContextMenuContent;
import com.sun.javafx.scene.control.ControlAcceleratorSupport;
import com.sun.javafx.scene.control.LabeledImpl;
import com.sun.javafx.scene.control.ListenerHelper;
import com.sun.javafx.scene.control.behavior.MenuButtonBehaviorBase;
import com.sun.javafx.scene.control.skin.Utils;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.control.skin.ContextMenuSkin;
import javafx.scene.input.Mnemonic;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.util.ArrayList;
import java.util.List;

/**
 * @see javafx.scene.control.skin.MenuButtonSkinBase
 */
public class CustomMenuButtonSkinBase<C extends MenuButton> extends SkinBase<C>  {

    final LabeledImpl label;
    final StackPane arrow;
    final StackPane arrowButton;
    ContextMenu popup;

    /**
     * If true, the control should behave like a button for mouse button events.
     */
    boolean behaveLikeButton = false;


    /* *************************************************************************
     *                                                                         *
     * Constructors                                                            *
     *                                                                         *
     **************************************************************************/

    /**
     * Creates a new instance of MenuButtonSkinBase, although note that this
     * instance does not handle any behavior / input mappings - this needs to be
     * handled appropriately by subclasses.
     *
     * @param control The control that this skin should be installed onto.
     */
    public CustomMenuButtonSkinBase(final C control) {
        super(control);

        ListenerHelper lh = ListenerHelper.get(this);

        if (control.getOnMousePressed() == null) {
            // lh.addEventHandler(control, MouseEvent.MOUSE_PRESSED, (ev) -> {
            //     MenuButtonBehaviorBase behavior = getBehavior();
            //     if (behavior != null) {
            //         behavior.mousePressed(ev, behaveLikeButton);
            //     }
            // });
            control.addEventHandler(MouseEvent.MOUSE_PRESSED, (ev) -> {
                MenuButtonBehaviorBase behavior = getBehavior();
                if (behavior != null) {
                    behavior.mousePressed(ev, behaveLikeButton);
                }
            });
        }

        if (control.getOnMouseReleased() == null) {
            // lh.addEventHandler(control, MouseEvent.MOUSE_RELEASED, (ev) -> {
            //     MenuButtonBehaviorBase behavior = getBehavior();
            //     if (behavior != null) {
            //         behavior.mouseReleased(ev, behaveLikeButton);
            //     }
            // });
            control.addEventHandler(MouseEvent.MOUSE_RELEASED, (ev) -> {
                MenuButtonBehaviorBase behavior = getBehavior();
                if (behavior != null) {
                    behavior.mouseReleased(ev, behaveLikeButton);
                }
            });
        }

        /*
         * Create the objects we will be displaying.
         */
        label = new MenuLabeledImpl(getSkinnable());
        label.setMnemonicParsing(control.isMnemonicParsing());
        label.setLabelFor(control);

        arrow = new StackPane();
        arrow.getStyleClass().setAll("arrow");
        arrow.setMaxWidth(Region.USE_PREF_SIZE);
        arrow.setMaxHeight(Region.USE_PREF_SIZE);

        arrowButton = new StackPane();
        arrowButton.getStyleClass().setAll("arrow-button");
        arrowButton.getChildren().add(arrow);

        popup = new ContextMenu();
        popup.getItems().clear();
        popup.getItems().addAll(getSkinnable().getItems());

        getChildren().clear();
        getChildren().addAll(label, arrowButton);

        getSkinnable().requestLayout();

        lh.addListChangeListener(control.getItems(), (ch) -> {
            while (ch.next()) {
                popup.getItems().removeAll(ch.getRemoved());
                popup.getItems().addAll(ch.getFrom(), ch.getAddedSubList());
            }
        });

        List<Mnemonic> mnemonics = new ArrayList<>();

        lh.addChangeListener(control.sceneProperty(), true, (src, oldScene, newScene) -> {
            if (oldScene != null) {
                ControlAcceleratorSupport.removeAcceleratorsFromScene(getSkinnable().getItems(), oldScene);

                // We only need to remove the mnemonics from the old scene,
                // they will be added to the new one as soon as the popup becomes visible again.
                removeMnemonicsFromScene(mnemonics, oldScene);
            }

            if (getSkinnable().getScene() != null) {
                ControlAcceleratorSupport.addAcceleratorsIntoScene(getSkinnable().getItems(), getSkinnable());
            }
        });

        // Register listeners
        lh.addChangeListener(control.showingProperty(), (ev) -> {
            if (getSkinnable().isShowing()) {
                show();
            } else {
                hide();
            }
        });

        lh.addChangeListener(control.focusedProperty(), (ev) -> {
            // Handle tabbing away from an open MenuButton
            if (!getSkinnable().isFocused() && getSkinnable().isShowing()) {
                hide();
            }
            if (!getSkinnable().isFocused() && popup.isShowing()) {
                hide();
            }
        });

        lh.addChangeListener(control.mnemonicParsingProperty(), (ev) -> {
            label.setMnemonicParsing(getSkinnable().isMnemonicParsing());
            getSkinnable().requestLayout();
        });

        lh.addChangeListener(popup.showingProperty(), (ev) -> {
            if (!popup.isShowing() && getSkinnable().isShowing()) {
                // Popup was dismissed. Maybe user clicked outside or typed ESCAPE.
                // Make sure button is in sync.
                getSkinnable().hide();
            }

            if (popup.isShowing()) {
                boolean showMnemonics = NodeHelper.isShowMnemonics(getSkinnable());
                Utils.addMnemonics(popup, getSkinnable().getScene(), showMnemonics, mnemonics);
            } else {
                Scene scene = getSkinnable().getScene();
                // JDK-8244234: MenuButton: NPE on removing from scene with open popup
                if (scene != null) {
                    removeMnemonicsFromScene(mnemonics, scene);
                }
            }
        });
    }



    /* *************************************************************************
     *                                                                         *
     * Private implementation                                                  *
     *                                                                         *
     **************************************************************************/

    /** {@inheritDoc} */
    @Override public void dispose() {
        if (getSkinnable() == null) {
            return;
        }

        // Cleanup accelerators
        if (getSkinnable().getScene() != null) {
            ControlAcceleratorSupport.removeAcceleratorsFromScene(getSkinnable().getItems(), getSkinnable().getScene());
        }

        super.dispose();

        if (popup != null ) {
            if (popup.getSkin() != null && popup.getSkin().getNode() != null) {
                ContextMenuContent cmContent = (ContextMenuContent)popup.getSkin().getNode();
                cmContent.dispose();
            }
            popup.setSkin(null);
            popup = null;
        }
    }

    /** {@inheritDoc} */
    @Override protected double computeMinWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        return leftInset
                + label.minWidth(height)
                + snapSizeX(arrowButton.minWidth(height))
                + rightInset;
    }

    /** {@inheritDoc} */
    @Override protected double computeMinHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        return topInset
                + Math.max(label.minHeight(width), snapSizeY(arrowButton.minHeight(-1)))
                + bottomInset;
    }

    /** {@inheritDoc} */
    @Override protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        return leftInset
                + label.prefWidth(height)
                + snapSizeX(arrowButton.prefWidth(height))
                + rightInset;
    }

    /** {@inheritDoc} */
    @Override protected double computePrefHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        return topInset
                + Math.max(label.prefHeight(width), snapSizeY(arrowButton.prefHeight(-1)))
                + bottomInset;
    }

    /** {@inheritDoc} */
    @Override protected double computeMaxWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        return getSkinnable().prefWidth(height);
    }

    /** {@inheritDoc} */
    @Override protected double computeMaxHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        return getSkinnable().prefHeight(width);
    }

    /** {@inheritDoc} */
    @Override protected void layoutChildren(final double x, final double y,
                                            final double w, final double h) {
        final double arrowButtonWidth = snapSizeX(arrowButton.prefWidth(-1));
        label.resizeRelocate(x, y, w - arrowButtonWidth, h);
        arrowButton.resizeRelocate(x + (w - arrowButtonWidth), y, arrowButtonWidth, h);
    }



    /* *************************************************************************
     *                                                                         *
     * Private implementation                                                  *
     *                                                                         *
     **************************************************************************/

    MenuButtonBehaviorBase<C> getBehavior() {
        return null;
    }

    protected void show() {
        if (!popup.isShowing()) {
            popup.show(getSkinnable(), getSkinnable().getPopupSide(), 0, 0);
        }
    }

    protected void hide() {
        if (popup.isShowing()) {
            popup.hide();
        }
    }

    private void removeMnemonicsFromScene(List<Mnemonic> mnemonics, Scene scene) {
        List<Mnemonic> mnemonicsToRemove = new ArrayList<>(mnemonics);
        mnemonics.clear();

        // we wrap this in a runLater so that mnemonics are not removed
        // before all key events are fired (because KEY_PRESSED might have
        // been used to hide the menu, but KEY_TYPED and KEY_RELEASED
        // events are still to be fired, and shouldn't miss out on going
        // through the mnemonics code (especially in case they should be
        // consumed to prevent them being used elsewhere).
        // See JDK-8090026 for more detail.
        Platform.runLater(() -> mnemonicsToRemove.forEach(scene::removeMnemonic));
    }

    boolean requestFocusOnFirstMenuItem = false;
    void requestFocusOnFirstMenuItem() {
        this.requestFocusOnFirstMenuItem = true;
    }

    void putFocusOnFirstMenuItem() {
        Skin<?> popupSkin = popup.getSkin();
        if (popupSkin instanceof ContextMenuSkin) {
            Node node = popupSkin.getNode();
            if (node instanceof ContextMenuContent) {
                ((ContextMenuContent)node).requestFocusOnIndex(0);
            }
        }
    }



    /* *************************************************************************
     *                                                                         *
     * Support classes                                                         *
     *                                                                         *
     **************************************************************************/

    private static class MenuLabeledImpl extends LabeledImpl {
        MenuButton button;
        public MenuLabeledImpl(MenuButton b) {
            super(b);
            button = b;
            addEventHandler(ActionEvent.ACTION, e -> {
                button.fireEvent(new ActionEvent());
                e.consume();
            });
        }
    }

}
