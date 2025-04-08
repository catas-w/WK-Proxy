package com.catas.wicked.proxy.gui.componet;

import com.catas.wicked.proxy.gui.componet.button.UnderLabelWrapper;
import com.sun.javafx.scene.control.ContextMenuContent;
import com.sun.javafx.scene.control.behavior.MenuButtonBehavior;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.scene.AccessibleAttribute;
import javafx.scene.control.Control;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Skin;
import javafx.stage.WindowEvent;

public class CustomMenuButton extends MenuButton {

    private final StringProperty iconLiteral = new SimpleStringProperty("");

    private final StringProperty labelText = new SimpleStringProperty("");

    public CustomMenuButton() {
        UnderLabelWrapper underLabelWrapper = new UnderLabelWrapper(iconLiteral, labelText);
        this.setGraphic(underLabelWrapper);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new CustomMenuBtnSkin(this);
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

    /**
     * to customize the goddamn popup position
     * @see javafx.scene.control.skin.MenuButtonSkin
     */
    public static class CustomMenuBtnSkin extends CustomMenuButtonSkinBase<MenuButton> {
        static final String AUTOHIDE = "autoHide";

        private final MenuButtonBehavior behavior;


        /**
         * Creates a new MenuButtonSkin instance, installing the necessary child
         * nodes into the Control {@link Control#getChildren() children} list, as
         * well as the necessary input mappings for handling key, mouse, etc events.
         *
         * @param control The control that this skin should be installed onto.
         */
        public CustomMenuBtnSkin(final MenuButton control) {
            super(control);

            // install default input map for the MenuButton-like controls
            this.behavior = new MenuButtonBehavior(control);

            // MenuButton's showing does not get updated when autoHide happens,
            // as that hide happens under the covers. So we add to the menuButton's
            // properties map to which the MenuButton can react and update accordingly..
            // JDK-8295426:
            // onAutoHide triggers an Event.ANY, making it impossible to add a listener which dispose() can remove.
            // keeping the existing setOnAutoHide(), making sure to setOnAutoHide(null) later.
            popup.setOnAutoHide(e -> {
                MenuButton menuButton = getSkinnable();
                // work around for the fact autohide happens twice
                // remove this check when that is fixed.
                if (!menuButton.getProperties().containsKey(AUTOHIDE)) {
                    menuButton.getProperties().put(AUTOHIDE, Boolean.TRUE);
                }
            });

            // ListenerHelper lh = ListenerHelper.get(this);

            // request focus on content when the popup is shown
            // lh.addEventHandler(popup, WindowEvent.WINDOW_SHOWN, (ev) -> {
            //     if (requestFocusOnFirstMenuItem) {
            //         requestFocusOnFirstMenuItem();
            //         requestFocusOnFirstMenuItem = false;
            //     } else {
            //         ContextMenuContent cmContent = (ContextMenuContent) popup.getSkin().getNode();
            //         if (cmContent != null) {
            //             cmContent.requestFocus();
            //         }
            //     }
            // });
            popup.addEventHandler( WindowEvent.WINDOW_SHOWN, (ev) -> {
                if (requestFocusOnFirstMenuItem) {
                    requestFocusOnFirstMenuItem();
                    requestFocusOnFirstMenuItem = false;
                } else {
                    ContextMenuContent cmContent = (ContextMenuContent) popup.getSkin().getNode();
                    if (cmContent != null) {
                        cmContent.requestFocus();
                    }
                }
            });

            // lh.addEventHandler(control, ActionEvent.ACTION, (ev) -> {
            //     control.show();
            // });
            control.addEventHandler(ActionEvent.ACTION, (ev) -> {
                control.show();
            });

            label.setLabelFor(control);
        }

        @Override
        public void dispose() {
            if (getSkinnable() == null) {
                return;
            }

            popup.setOnAutoHide(null);

            super.dispose();

            if (behavior != null) {
                behavior.dispose();
            }
        }

        @Override MenuButtonBehavior getBehavior() {
            return behavior;
        }

        @Override
        public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
            switch (attribute) {
                case MNEMONIC: return label.queryAccessibleAttribute(AccessibleAttribute.MNEMONIC);
                default: return super.queryAccessibleAttribute(attribute, parameters);
            }
        }

        /**
         * custom position
         */
        @Override
        protected void show() {
            if (!popup.isShowing()) {
                // popup.show(getSkinnable(), getSkinnable().getPopupSide(), 40, 10);
                MenuButton menuButton = getSkinnable();
                popup.show(menuButton,
                menuButton.localToScreen(menuButton.getLayoutBounds().getMinX(), menuButton.getLayoutBounds().getMaxY()).getX() + 40,
                menuButton.localToScreen(menuButton.getLayoutBounds().getMinX(), menuButton.getLayoutBounds().getMaxY()).getY() - 180);
            }
        }
    }
}
