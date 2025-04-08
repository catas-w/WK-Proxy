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
    private boolean recording = true;

    @JsonDeserialize(using = SafeIntegerDeserializer.class)
    private Integer maxContentSize = 10;

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
    private ExternalProxyConfig externalProxy;

    /**
     * Throttle settings
     */
    @JsonDeserialize(using = SafeBooleanDeserializer.class)
    private boolean throttle;

    private ThrottlePreset throttlePreset;


    public Integer getMaxContentSize() {
        return maxContentSize == null ? 10 : maxContentSize;
    }

    public Integer getPort() {
        return port == null ? 9966 : port;
    }

    public String getSelectedCert() {
        return selectedCert == null ? "_default_" : selectedCert;
    }

    public ExternalProxyConfig getExternalProxy() {
        return externalProxy == null ? new ExternalProxyConfig() : externalProxy ;
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
