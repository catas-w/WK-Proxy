package com.catas.wicked.common.constant;

import com.catas.wicked.common.bean.ProcessInfo;

/** Product identity shared by the proxy server and desktop GUI. */
public final class ProductIdentity {

    public static final String DISPLAY_NAME = "Wizard Proxy";
    public static final String APPLICATION_KEY = "__wizard_proxy__";

    private ProductIdentity() {
    }

    public static ProcessInfo currentProcess() {
        long pid = currentPid();
        return ProcessInfo.builder()
                .ownerPid(pid)
                .ownerProcessName(DISPLAY_NAME)
                .applicationPid(pid)
                .applicationName(DISPLAY_NAME)
                .lookupStatus(ProcessInfo.LookupStatus.FOUND)
                .build();
    }

    private static long currentPid() {
        try {
            return ProcessHandle.current().pid();
        } catch (RuntimeException | LinkageError ignored) {
            return 0L;
        }
    }
}
