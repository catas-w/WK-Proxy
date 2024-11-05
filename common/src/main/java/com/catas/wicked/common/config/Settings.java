package com.catas.wicked.common.config;

import com.catas.wicked.common.constant.ThrottlePreset;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Settings {

    /**
     * General settings
     */
    private String language;
    private boolean recording = true;
    private int maxContentSize = 10;
    private List<String> recordIncludeList;
    private List<String> recordExcludeList;

    /**
     * Server settings
     */
    private int port = 9999;
    private boolean systemProxy = false;
    private boolean enableSysProxyOnLaunch;
    private List<String> sysProxyBypassList;

    /**
     * Ssl settings
     */
    // @JsonIgnore
    private boolean handleSsl = false;
    private String selectedCert;

    private List<String> sslExcludeList;

    /**
     * External proxy settings
     */
    private boolean enableExProxy;
    private ExternalProxyConfig externalProxy;

    /**
     * Throttle settings
     */
    private boolean throttle;
    private ThrottlePreset throttlePreset;

}
