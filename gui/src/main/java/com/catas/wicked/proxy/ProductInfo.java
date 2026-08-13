package com.catas.wicked.proxy;

public final class ProductInfo {

    public static final String DISPLAY_NAME =
            com.catas.wicked.common.constant.ProductIdentity.DISPLAY_NAME;

    private ProductInfo() {
    }

    public static String versionLabel(String version) {
        return DISPLAY_NAME + " " + version;
    }
}
