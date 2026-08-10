package com.catas.wicked.server.cert;

import com.catas.wicked.common.provider.CertInstallProvider;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

final class CertificateInstallWorkflow {

    private final CertificateInstallAwaiter awaiter;

    CertificateInstallWorkflow() {
        this(new CertificateInstallAwaiter());
    }

    CertificateInstallWorkflow(CertificateInstallAwaiter awaiter) {
        this.awaiter = awaiter;
    }

    void install(File tempFile, byte[] certificateData, String certificateName, String sha256,
                 CertInstallProvider provider)
            throws IOException, InterruptedException, InstallRejectedException, InstallTimeoutException {
        try {
            FileUtils.writeByteArrayToFile(tempFile, certificateData);
            if (!provider.install(tempFile.getAbsolutePath())) {
                throw new InstallRejectedException();
            }
            if (!awaiter.await(() -> provider.checkCertInstalled(certificateName, sha256))) {
                throw new InstallTimeoutException();
            }
        } finally {
            FileUtils.deleteQuietly(tempFile);
        }
    }

    static final class InstallRejectedException extends Exception {
    }

    static final class InstallTimeoutException extends Exception {
    }
}
