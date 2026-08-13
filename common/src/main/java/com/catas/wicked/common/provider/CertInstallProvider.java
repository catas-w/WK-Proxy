package com.catas.wicked.common.provider;

public interface CertInstallProvider {


    boolean checkCertInstalled(String certName, String SHA256);

    /**
     * Requests installation of a certificate on the local system. A true result
     * means that the request was accepted; callers must verify the final state
     * with {@link #checkCertInstalled(String, String)}.
     *
     * @param certPath path to x509certificate
     * @return true when the installation request was accepted
     */
    boolean install(String certPath);
}
