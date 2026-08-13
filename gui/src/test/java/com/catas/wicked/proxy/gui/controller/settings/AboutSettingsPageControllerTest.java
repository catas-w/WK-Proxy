package com.catas.wicked.proxy.gui.controller.settings;

import com.catas.wicked.common.provider.DesktopProvider;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class AboutSettingsPageControllerTest {

    private static void setDesktopProvider(AboutSettingsPageController controller,
                                           DesktopProvider desktopProvider) throws Exception {
        Field field = AboutSettingsPageController.class.getDeclaredField("desktopProvider");
        field.setAccessible(true);
        field.set(controller, desktopProvider);
    }
}
