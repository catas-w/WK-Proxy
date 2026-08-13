package com.catas.wicked.proxy.message;

import com.catas.wicked.common.bean.ProcessInfo;
import com.catas.wicked.common.constant.ProductIdentity;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ApplicationSourceTest {

    @Test
    public void unknownStatusUsesIdentifyingGroup() {
        ApplicationSource source = ApplicationSource.from(ProcessInfo.unknown());

        assertEquals(ApplicationSource.IDENTIFYING_KEY, source.key());
        assertEquals("Identifying...", source.displayName());
    }

    @Test
    public void failedStatusesShareUnknownGroupAndRetainStatus() {
        for (ProcessInfo.LookupStatus status : new ProcessInfo.LookupStatus[] {
                ProcessInfo.LookupStatus.NOT_FOUND,
                ProcessInfo.LookupStatus.UNSUPPORTED,
                ProcessInfo.LookupStatus.ACCESS_DENIED,
                ProcessInfo.LookupStatus.ERROR}) {
            ApplicationSource source = ApplicationSource.from(ProcessInfo.withStatus(status));
            assertEquals(ApplicationSource.UNKNOWN_KEY, source.key());
            assertTrue(source.statusText().contains(status.name()));
        }
    }

    @Test
    @Ignore
    public void executablePathIsPreferredAsStableApplicationKey() {
        ProcessInfo info = ProcessInfo.builder()
                .lookupStatus(ProcessInfo.LookupStatus.FOUND)
                .applicationName("Browser")
                .applicationExecutablePath("/Applications/Browser.app/Contents/MacOS/Browser")
                .ownerProcessName("Browser Helper")
                .ownerPid(42)
                .build();

        ApplicationSource source = ApplicationSource.from(info);

        assertEquals("path:/Applications/Browser.app/Contents/MacOS/Browser", source.key());
        assertEquals("Browser", source.displayName());
        assertTrue(source.secondaryText().contains("Browser Helper"));
        assertTrue(source.secondaryText().contains("42"));
    }

    @Test
    public void applicationNameIsUsedWhenExecutableIsUnavailable() {
        ProcessInfo info = ProcessInfo.builder()
                .lookupStatus(ProcessInfo.LookupStatus.FOUND)
                .applicationName("Visual Studio Code")
                .applicationPid(7)
                .build();

        ApplicationSource source = ApplicationSource.from(info);

        assertEquals("name:visual studio code", source.key());
        assertEquals("Visual Studio Code", source.displayName());
        assertTrue(source.secondaryText().contains("7"));
    }

    @Test
    public void wizardProxyAlwaysUsesReservedApplicationKey() {
        ProcessInfo info = ProcessInfo.builder()
                .lookupStatus(ProcessInfo.LookupStatus.FOUND)
                .applicationName(ProductIdentity.DISPLAY_NAME)
                .applicationExecutablePath("/Applications/Wizard Proxy.app/Contents/MacOS/Wizard Proxy")
                .ownerProcessName(ProductIdentity.DISPLAY_NAME)
                .ownerPid(42)
                .build();

        ApplicationSource source = ApplicationSource.from(info);

        assertEquals(ProductIdentity.APPLICATION_KEY, source.key());
        assertEquals(ProductIdentity.DISPLAY_NAME, source.displayName());
        assertTrue(source.secondaryText().contains("42"));
    }
}
