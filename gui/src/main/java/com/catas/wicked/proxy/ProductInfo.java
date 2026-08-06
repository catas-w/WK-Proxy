package com.catas.wicked.proxy;

public final class ProductInfo {

    public static final String DISPLAY_NAME = "Wizard Proxy";

    private ProductInfo() {
    }

    public static String versionLabel(String version) {
        return DISPLAY_NAME + " " + version;
    }
}
