package com.catas.wicked.common.provider;

import org.apache.commons.lang3.tuple.Pair;

import java.util.concurrent.ExecutionException;

public interface VersionCheckProvider {

    /**
     * get latest version info
     * @return version, url
     */
    Pair<String, String> fetchLatestVersion() throws ExecutionException, InterruptedException;
}
