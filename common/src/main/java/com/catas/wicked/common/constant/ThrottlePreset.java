package com.catas.wicked.common.constant;

import lombok.Getter;

@Getter
public enum ThrottlePreset {

    SLOW_2G("Slow 2G", 50 * 1000, 20 * 1000),
    REGULAR_2G("Regular 2G", 250 * 1000, 50 * 1000),
    REGULAR_3G("Regular 3G", 750 * 1000, 250 * 1000),
    REGULAR_4G("Regular 4G", 4000 * 1000, 3000 * 1000);

    private final String desc;

    /**
     * 0 or a limit in bytes/s
     */
    private final long writeLimit;

    /**
     * 0 or a limit in bytes/s
     */
    private final long readLimit;

    /**
     * The delay between two computations of performances for channels
     */
    private final long checkInterval;

    /**
     * The maximum delay to wait in case of traffic excess.
     */
    private final long maxTime;

    ThrottlePreset(String desc, long writeLimit, long readLimit, long checkInterval, long maxTime) {
        this.desc = desc;
        this.writeLimit = writeLimit;
        this.readLimit = readLimit;
        this.checkInterval = checkInterval;
        this.maxTime = maxTime;
    }

    ThrottlePreset(String desc, long writeLimit, long readLimit) {
        this.desc = desc;
        this.writeLimit = writeLimit;
        this.readLimit = readLimit;
        this.checkInterval = 15000;
        this.maxTime = 1000;
    }
}
