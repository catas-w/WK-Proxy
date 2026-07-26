package com.catas.wicked.proxy.service.settings;

import com.catas.wicked.common.config.Settings;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SettingsDraftTest {

    @Test
    public void editingDraftDoesNotMutateSource() {
        Settings source = new Settings();
        source.setPort(9966);
        source.setSslExcludeList(List.of("example.com"));

        SettingsDraft draft = SettingsDraft.from(source);
        draft.value().setPort(8877);
        draft.value().setSslExcludeList(List.of("changed.example.com"));
        draft.value().getExternalProxy().setPassword("secret");

        assertTrue(draft.isDirty());
        assertTrue(source.getPort() == 9966);
        assertTrue(source.getSslExcludeList().contains("example.com"));
        assertFalse("secret".equals(source.getExternalProxy().getPassword()));

        draft.markApplied();
        assertFalse(draft.isDirty());
    }
}
