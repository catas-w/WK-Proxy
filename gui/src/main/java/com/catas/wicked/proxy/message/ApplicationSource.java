package com.catas.wicked.proxy.message;

import com.catas.wicked.common.bean.ProcessInfo;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;

record ApplicationSource(String key, String displayName, String secondaryText, String statusText) {

    static final String IDENTIFYING_KEY = "__identifying__";
    static final String UNKNOWN_KEY = "__unknown__";

    static ApplicationSource from(ProcessInfo info) {
        ProcessInfo.LookupStatus status = info == null ? ProcessInfo.LookupStatus.UNKNOWN : info.getLookupStatus();
        if (status == null || status == ProcessInfo.LookupStatus.UNKNOWN) {
            return new ApplicationSource(IDENTIFYING_KEY, "Identifying...", "", "Identifying source application");
        }
        if (status != ProcessInfo.LookupStatus.FOUND) {
            return new ApplicationSource(UNKNOWN_KEY, "Unknown Application", "", "Lookup status: " + status);
        }
        String applicationName = StringUtils.firstNonBlank(
                info.getApplicationName(), info.getOwnerProcessName(), "Unknown Application");
        String path = StringUtils.firstNonBlank(info.getApplicationExecutablePath(), info.getOwnerExecutablePath());
        String key = StringUtils.isNotBlank(path) ? normalizePath(path)
                : "name:" + applicationName.toLowerCase(Locale.ROOT);
        String processName = StringUtils.firstNonBlank(info.getOwnerProcessName(), applicationName);
        long pid = info.getOwnerPid() > 0 ? info.getOwnerPid() : info.getApplicationPid();
        String secondary = processName + (pid > 0 ? "  PID " + pid : "");
        return new ApplicationSource(key, applicationName, secondary, "Lookup status: FOUND");
    }

    static String normalizePath(String path) {
        String normalized = path.replace('\\', '/');
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            normalized = normalized.toLowerCase(Locale.ROOT);
        }
        return "path:" + normalized;
    }
}
