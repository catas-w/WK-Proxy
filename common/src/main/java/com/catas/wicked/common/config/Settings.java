package com.catas.wicked.common.config;

import com.catas.wicked.common.constant.LanguagePreset;
import com.catas.wicked.common.constant.ThrottlePreset;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;

@Slf4j
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Settings {

    /**
     * General settings
     */
    private LanguagePreset language = LanguagePreset.ENGLISH;

    @JsonDeserialize(using = SafeBooleanDeserializer.class)
    private boolean showButtonLabel = true;

    @JsonDeserialize(using = SafeBooleanDeserializer.class)
    private boolean showApplicationRequestCount = true;

    @JsonDeserialize(using = SafeBooleanDeserializer.class)
    private boolean recording = true;

    @JsonDeserialize(using = SafeIntegerDeserializer.class)
    private Integer maxContentSize = 10;

    @JsonDeserialize(using = SafeIntegerDeserializer.class)
    private Integer retainedPayloadSizeMb = 512;

    @Deprecated
    @JsonDeserialize(using = SafeJsonListDeserializer.class)
    private List<String> recordIncludeList;

    @JsonDeserialize(using = SafeJsonListDeserializer.class)
    private List<String> recordExcludeList;

    /**
     * Server settings
     */
    @JsonDeserialize(using = SafeIntegerDeserializer.class)
    private Integer port;

    @JsonDeserialize(using = SafeBooleanDeserializer.class)
    private boolean systemProxy = false;

    @JsonDeserialize(using = SafeBooleanDeserializer.class)
    private boolean enableSysProxyOnLaunch;

    @JsonDeserialize(using = SafeJsonListDeserializer.class)
    private List<String> sysProxyBypassList;

    @JsonDeserialize(using = SafeIntegerDeserializer.class)
    private Integer connectTimeout = 60;

    /**
     * Ssl settings
     */
    // @JsonIgnore
    @JsonDeserialize(using = SafeBooleanDeserializer.class)
    private boolean handleSsl = false;

    private String selectedCert;

    @JsonDeserialize(using = SafeJsonListDeserializer.class)
    private List<String> sslExcludeList;

    /**
     * External proxy settings
     */
    @JsonDeserialize(using = SafeBooleanDeserializer.class)
    private boolean enableExProxy;

    @JsonDeserialize(using = SafeExternalProxyDeserializer.class)
    private ExternalProxyConfig externalProxy = new ExternalProxyConfig();

    /**
     * Throttle settings
     */
    @JsonDeserialize(using = SafeBooleanDeserializer.class)
    private boolean throttle;

    private ThrottlePreset throttlePreset;


    public Integer getMaxContentSize() {
        return maxContentSize == null ? 10 : maxContentSize;
    }

    public Integer getRetainedPayloadSizeMb() {
        return retainedPayloadSizeMb == null || retainedPayloadSizeMb <= 0 ? 512 : retainedPayloadSizeMb;
    }

    public Integer getPort() {
        return port == null ? 9966 : port;
    }

    public String getSelectedCert() {
        return selectedCert == null ? "_default_" : selectedCert;
    }

    public ExternalProxyConfig getExternalProxy() {
        if (externalProxy == null) {
            externalProxy = new ExternalProxyConfig();
        }
        return externalProxy;
    }

    public Settings copy() {
        Settings copy = new Settings();
        copy.language = language;
        copy.showButtonLabel = showButtonLabel;
        copy.showApplicationRequestCount = showApplicationRequestCount;
        copy.recording = recording;
        copy.maxContentSize = maxContentSize;
        copy.retainedPayloadSizeMb = retainedPayloadSizeMb;
        copy.recordIncludeList = copyList(recordIncludeList);
        copy.recordExcludeList = copyList(recordExcludeList);
        copy.port = port;
        copy.systemProxy = systemProxy;
        copy.enableSysProxyOnLaunch = enableSysProxyOnLaunch;
        copy.sysProxyBypassList = copyList(sysProxyBypassList);
        copy.connectTimeout = connectTimeout;
        copy.handleSsl = handleSsl;
        copy.selectedCert = selectedCert;
        copy.sslExcludeList = copyList(sslExcludeList);
        copy.enableExProxy = enableExProxy;
        copy.externalProxy = getExternalProxy().copy();
        copy.throttle = throttle;
        copy.throttlePreset = throttlePreset;
        return copy;
    }

    private static List<String> copyList(List<String> source) {
        return source == null ? null : List.copyOf(source);
    }

    static class SafeIntegerDeserializer extends JsonDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) {
            try {
                return Integer.parseInt(parser.getText());
            } catch (Exception e) {
                return null;
            }
        }
    }

    static class SafeBooleanDeserializer extends JsonDeserializer<Boolean> {
        @Override
        public Boolean deserialize(JsonParser parser, DeserializationContext context)  {
            try {
                return parser.readValueAs(Boolean.class);
            } catch (Exception e) {
                return false;
            }
        }
    }

    static class SafeExternalProxyDeserializer extends JsonDeserializer<ExternalProxyConfig> {
        @Override
        public ExternalProxyConfig deserialize(JsonParser parser, DeserializationContext context) {
            try {
                return parser.readValueAs(ExternalProxyConfig.class);
            } catch (Exception e) {
                return new ExternalProxyConfig();
            }
        }
    }

    static class SafeJsonListDeserializer extends JsonDeserializer<List<String>> {
        @Override
        public List<String> deserialize(JsonParser parser, DeserializationContext context) {
            try {
                return parser.readValueAs(new TypeReference<List<String>>() {});
            } catch (Exception e) {
                return Collections.emptyList();
            }
        }
    }
}
