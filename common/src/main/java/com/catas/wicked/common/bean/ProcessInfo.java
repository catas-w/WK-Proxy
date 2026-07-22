package com.catas.wicked.common.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private long ownerPid;
    private String ownerProcessName;
    private String ownerExecutablePath;
    private long applicationPid;
    private String applicationName;
    private String applicationExecutablePath;
    private LookupStatus lookupStatus;

    public static ProcessInfo unknown() {
        return withStatus(LookupStatus.UNKNOWN);
    }

    public static ProcessInfo withStatus(LookupStatus status) {
        return ProcessInfo.builder().lookupStatus(status).build();
    }

    public enum LookupStatus {
        UNKNOWN,
        FOUND,
        NOT_FOUND,
        UNSUPPORTED,
        ACCESS_DENIED,
        ERROR
    }
}
