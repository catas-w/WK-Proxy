package com.catas.wicked.common.constant;

import io.netty.handler.codec.http.HttpResponseStatus;

public class ProxyConstant {

    /**
     * shared key of request-info
     */
    public static final String REQUEST_INFO = "requestInfo";

    public static final String UNPARSED_ALIAS = "<Encrypted>";

    public static final String OVERSIZE_MSG = "<Content Oversize>";

    public static final String SIGNATURE = "SHA256WithRSAEncryption";

    public static final String START_DATE = "2023-01-01";

    public static final String SUBJECT = "C=CN, ST=SC, L=CD, O=Catas, CN=Catas";

    public static final String PRIVATE_FILE_PATTERN = """
                -----BEGIN PRIVATE KEY-----
                %s
                -----END PRIVATE KEY-----
                """;

    public static final String CERT_FILE_PATTERN = """
                -----BEGIN CERTIFICATE-----
                %s
                -----END CERTIFICATE-----
                """;

    public final static HttpResponseStatus SUCCESS = new HttpResponseStatus(200,
            "Connection established");

    public final static HttpResponseStatus UNAUTHORIZED = new HttpResponseStatus(407,
            "Unauthorized");
}
