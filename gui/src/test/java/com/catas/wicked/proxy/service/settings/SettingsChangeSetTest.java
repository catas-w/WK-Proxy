package com.catas.wicked.proxy.service.settings;

import com.catas.wicked.common.config.Settings;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SettingsChangeSetTest {

    @Test
    public void classifiesIndependentSettingsChanges() {
        Settings before = new Settings();
        Settings after = before.copy();
        after.setPort(8877);
        after.setHandleSsl(true);
        after.getExternalProxy().setPassword("secret");

        SettingsChangeSet changes = SettingsChangeSet.between(before, after);

        assertTrue(changes.portChanged());
        assertTrue(changes.sslChanged());
        assertTrue(changes.externalProxyChanged());
        assertFalse(changes.generalChanged());
        assertTrue(changes.hasChanges());
    }
}
