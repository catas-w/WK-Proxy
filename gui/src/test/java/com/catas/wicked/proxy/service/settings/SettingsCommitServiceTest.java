package com.catas.wicked.proxy.service.settings;

import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.config.Settings;
import com.catas.wicked.common.constant.LanguagePreset;
import com.catas.wicked.server.proxy.ProxyServer;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SettingsCommitServiceTest {

    private SettingsCommitService service;

    @After
    public void tearDown() {
        if (service != null) {
            service.close();
        }
    }

    @Test
    public void unavailablePortDoesNotMutateRuntimeSettings() throws Exception {
        FakeApplicationConfig config = new FakeApplicationConfig();
        Settings settings = new Settings();
        settings.setPort(9966);
        config.setSettings(settings);
        FakeProxyServer server = new FakeProxyServer();
        service = new SettingsCommitService(config, server, null, port -> false);

        SettingsDraft draft = SettingsDraft.from(settings);
        draft.value().setPort(8877);
        SettingsApplyResult result = service.apply(draft).toCompletableFuture().get(2, TimeUnit.SECONDS);

        assertFalse(result.success());
        assertEquals(SettingsApplyFailureType.PORT_UNAVAILABLE, result.failureType());
        assertEquals(Integer.valueOf(8877), result.rejectedPort());
        assertEquals(9966, config.getSettings().getPort().intValue());
        assertEquals(0, server.restartCount);
        assertEquals(0, config.persistCount);
    }

    @Test
    public void changedPortRestartsAndPersistsOnce() throws Exception {
        FakeApplicationConfig config = new FakeApplicationConfig();
        Settings settings = new Settings();
        settings.setPort(9966);
        config.setSettings(settings);
        FakeProxyServer server = new FakeProxyServer();
        service = new SettingsCommitService(config, server, null, port -> true);

        SettingsDraft draft = SettingsDraft.from(settings);
        draft.value().setPort(8877);
        SettingsApplyResult result = service.apply(draft).toCompletableFuture().get(2, TimeUnit.SECONDS);

        assertTrue(result.success());
        assertEquals(8877, config.getSettings().getPort().intValue());
        assertEquals(1, server.restartCount);
        assertEquals(1, config.persistCount);
    }

    @Test
    public void restartFailureRestoresOriginalSettingsWithoutPersisting() throws Exception {
        FakeApplicationConfig config = new FakeApplicationConfig();
        Settings settings = new Settings();
        settings.setPort(9966);
        config.setSettings(settings);
        FakeProxyServer server = new FakeProxyServer();
        server.failFirstRestart = true;
        service = new SettingsCommitService(config, server, null, port -> true);

        SettingsDraft draft = SettingsDraft.from(settings);
        draft.value().setPort(8877);
        SettingsApplyResult result = service.apply(draft).toCompletableFuture().get(2, TimeUnit.SECONDS);

        assertFalse(result.success());
        assertEquals(9966, config.getSettings().getPort().intValue());
        assertEquals(2, server.restartCount);
        assertEquals(0, config.persistCount);
    }

    @Test
    public void unchangedDraftDoesNotTouchRuntimeServices() throws Exception {
        FakeApplicationConfig config = new FakeApplicationConfig();
        Settings settings = new Settings();
        config.setSettings(settings);
        FakeProxyServer server = new FakeProxyServer();
        service = new SettingsCommitService(config, server, null, port -> true);

        SettingsApplyResult result = service.apply(SettingsDraft.from(settings))
                .toCompletableFuture().get(2, TimeUnit.SECONDS);

        assertTrue(result.success());
        assertEquals(0, server.restartCount);
        assertEquals(0, config.persistCount);
    }

    @Test
    public void unchangedPortDoesNotRunAvailabilityCheck() throws Exception {
        FakeApplicationConfig config = new FakeApplicationConfig();
        Settings settings = new Settings();
        settings.setPort(9966);
        config.setSettings(settings);
        FakeProxyServer server = new FakeProxyServer();
        AtomicInteger checkCount = new AtomicInteger();
        service = new SettingsCommitService(config, server, null, port -> {
            checkCount.incrementAndGet();
            return true;
        });

        SettingsDraft draft = SettingsDraft.from(settings);
        draft.value().setThrottle(!settings.isThrottle());
        SettingsApplyResult result = service.apply(draft)
                .toCompletableFuture().get(2, TimeUnit.SECONDS);

        assertTrue(result.success());
        assertEquals(0, checkCount.get());
    }

    @Test
    public void languageOnlyChangePersistsWithoutPortCheckOrRestart() throws Exception {
        FakeApplicationConfig config = new FakeApplicationConfig();
        Settings settings = new Settings();
        settings.setLanguage(LanguagePreset.ENGLISH);
        config.setSettings(settings);
        FakeProxyServer server = new FakeProxyServer();
        AtomicInteger checkCount = new AtomicInteger();
        service = new SettingsCommitService(config, server, null, port -> {
            checkCount.incrementAndGet();
            return true;
        });

        SettingsDraft draft = SettingsDraft.from(settings);
        draft.value().setLanguage(LanguagePreset.CHINESE);
        SettingsApplyResult result = service.apply(draft)
                .toCompletableFuture().get(2, TimeUnit.SECONDS);

        assertTrue(result.success());
        assertTrue(result.changes().languageChanged());
        assertEquals(LanguagePreset.CHINESE, config.getSettings().getLanguage());
        assertEquals(0, checkCount.get());
        assertEquals(0, server.restartCount);
        assertEquals(1, config.persistCount);
    }

    @Test
    public void asyncAvailabilityCheckDoesNotMutateSettings() throws Exception {
        FakeApplicationConfig config = new FakeApplicationConfig();
        Settings settings = new Settings();
        settings.setPort(9966);
        config.setSettings(settings);
        FakeProxyServer server = new FakeProxyServer();
        service = new SettingsCommitService(config, server, null, port -> false);

        boolean available = service.checkPortAvailability(8877)
                .toCompletableFuture().get(2, TimeUnit.SECONDS);

        assertFalse(available);
        assertEquals(9966, config.getSettings().getPort().intValue());
        assertEquals(0, server.restartCount);
        assertEquals(0, config.persistCount);
    }

    @Test
    public void availabilityCheckFailureIsReportedAsApplyError() throws Exception {
        FakeApplicationConfig config = new FakeApplicationConfig();
        Settings settings = new Settings();
        settings.setPort(9966);
        config.setSettings(settings);
        FakeProxyServer server = new FakeProxyServer();
        service = new SettingsCommitService(config, server, null, port -> {
            throw new IllegalStateException("check failed");
        });

        SettingsDraft draft = SettingsDraft.from(settings);
        draft.value().setPort(8877);
        SettingsApplyResult result = service.apply(draft)
                .toCompletableFuture().get(2, TimeUnit.SECONDS);

        assertFalse(result.success());
        assertEquals(SettingsApplyFailureType.APPLY_ERROR, result.failureType());
        assertEquals(9966, config.getSettings().getPort().intValue());
        assertEquals(0, server.restartCount);
        assertEquals(0, config.persistCount);
    }

    private static final class FakeApplicationConfig extends ApplicationConfig {
        private int persistCount;

        @Override
        public synchronized void persistSettings() throws IOException {
            persistCount++;
        }
    }

    private static final class FakeProxyServer extends ProxyServer {
        private int restartCount;
        private boolean failFirstRestart;

        @Override
        public synchronized void restart() {
            restartCount++;
            if (failFirstRestart && restartCount == 1) {
                throw new IllegalStateException("restart failed");
            }
        }
    }
}
