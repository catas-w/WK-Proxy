package com.catas.wicked.common.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Slf4j
public class CommonUtils {

    public static String toHexString(byte[] byteArray, char separator) {
        StringBuilder builder = new StringBuilder();
        final int len = 12;
        for (int i = 0; i < byteArray.length; i++) {
            builder.append(String.format("%02X", byteArray[i]));
            if (i > 0 && i % len == 0) {
                builder.append("\n");
            } else {
                builder.append(separator);
            }
        }

        if (builder.charAt(builder.length() - 1) == separator) {
            builder.deleteCharAt(builder.length() - 1);
        }
        return builder.toString();
    }

    public static String toHexString(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public static String SHA256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] sha256 = digest.digest(data);
            return toHexString(sha256);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error in get SHA256 hash.", e);
        }
    }

    public static String wrapText(String content) {
        return wrapText(content, 64);
    }

    public static String wrapText(String content, int length) {
        if (StringUtils.isEmpty(content) || length <= 0 || length > content.length()) {
            return content;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            builder.append(content.charAt(i));
            if (i > 0 && i % length == 0) {
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    /**
     * compare version with pattern like "1.0.1"
     * @return 1 v1 > v2,  -1 v1 &lt; v2
     */
    public static int compareVersions(String version1, String version2) {
        String[] parts1 = version1.split("\\.");
        String[] parts2 = version2.split("\\.");

        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int v1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int v2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;

            if (v1 != v2) {
                return Integer.compare(v1, v2);
            }
        }
        return 0;
    }
}
