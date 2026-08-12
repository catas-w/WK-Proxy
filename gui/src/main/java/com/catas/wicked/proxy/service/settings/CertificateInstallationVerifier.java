package com.catas.wicked.proxy.service.settings;

import com.catas.wicked.common.provider.CertManager;

import java.util.Objects;
import java.util.function.Supplier;

public final class CertificateInstallationVerifier {

    private CertificateInstallationVerifier() {
    }

    public static Result installAndVerify(CertManager certManager, String certId,
                                          Supplier<String> runtimeCertIdSupplier) throws Exception {
        Objects.requireNonNull(certManager, "certManager");
        Objects.requireNonNull(runtimeCertIdSupplier, "runtimeCertIdSupplier");

        certManager.installCert(certId);
        if (!certManager.checkInstalled(certId)) {
            throw new VerificationException();
        }

        String runtimeCertId = runtimeCertIdSupplier.get();
        boolean runtimeCertInstalled = Objects.equals(certId, runtimeCertId)
                || certManager.checkInstalled(runtimeCertId);
        return new Result(certId, runtimeCertInstalled);
    }

    public record Result(String certId, boolean runtimeCertInstalled) {
    }

    public static final class VerificationException extends Exception {
    }
}
