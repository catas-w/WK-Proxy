package com.catas.wicked.proxy.message;

import com.catas.wicked.proxy.gui.controller.RequestViewController;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class ApplicationMessageTreeLifecycleTest {

    @Test
    public void constructionDoesNotAccessFxmlControls() {
        RequestViewController controller = new RequestViewController();

        ApplicationMessageTree tree = new ApplicationMessageTree(controller);

        assertNotNull(tree);
    }
}
