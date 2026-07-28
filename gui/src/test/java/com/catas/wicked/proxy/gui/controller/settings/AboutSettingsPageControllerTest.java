package com.catas.wicked.proxy.gui.controller.settings;

import com.catas.wicked.common.provider.DesktopProvider;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class AboutSettingsPageControllerTest {

    @Test
    public void opensRepositoryLicenseAndEmailTargets() throws Exception {
        AboutSettingsPageController controller = new AboutSettingsPageController();
        List<String> openedTargets = new ArrayList<>();
        setDesktopProvider(controller, openedTargets::add);

        controller.openRepository();
        controller.openLicense();
        controller.openEmail();

        assertEquals(List.of(
                "https://github.com/catas-w/HumBird-Proxy/",
                "https://www.gnu.org/licenses/gpl-3.0.html",
                "mailto:catasw@foxmail.com"
        ), openedTargets);
    }

    private static void setDesktopProvider(AboutSettingsPageController controller,
                                           DesktopProvider desktopProvider) throws Exception {
        Field field = AboutSettingsPageController.class.getDeclaredField("desktopProvider");
        field.setAccessible(true);
        field.set(controller, desktopProvider);
    }
}
