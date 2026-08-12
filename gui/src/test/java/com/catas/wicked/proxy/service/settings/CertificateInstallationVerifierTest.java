package com.catas.wicked.proxy.service.settings;

import com.catas.wicked.common.config.CertificateConfig;
import com.catas.wicked.common.provider.CertManager;
import org.junit.Test;

import java.io.InputStream;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class CertificateInstallationVerifierTest {

    @Test
    public void installingRuntimeCertificateImmediatelyMarksRuntimeInstalled() throws Exception {
        FakeCertManager certManager = new FakeCertManager();

        CertificateInstallationVerifier.Result result = CertificateInstallationVerifier.installAndVerify(
                certManager, "runtime", () -> "runtime");

        assertEquals("runtime", result.certId());
        assertTrue(result.runtimeCertInstalled());
        assertEquals(List.of("runtime"), certManager.checkedIds);
    }

    @Test
    public void installingDraftCertificateKeepsRuntimeStatusIndependent() throws Exception {
        FakeCertManager certManager = new FakeCertManager();
        certManager.installed.put("runtime", false);

        CertificateInstallationVerifier.Result result = CertificateInstallationVerifier.installAndVerify(
                certManager, "draft", () -> "runtime");

        assertTrue(certManager.installed.get("draft"));
        assertFalse(result.runtimeCertInstalled());
        assertEquals(List.of("draft", "runtime"), certManager.checkedIds);
    }

    @Test
    public void failedSecondCheckDoesNotReportInstallation() {
        FakeCertManager certManager = new FakeCertManager();
        certManager.confirmInstallation = false;

        assertThrows(CertificateInstallationVerifier.VerificationException.class,
                () -> CertificateInstallationVerifier.installAndVerify(
                        certManager, "runtime", () -> "runtime"));
        assertEquals(List.of("runtime"), certManager.checkedIds);
    }

    @Test
    public void runtimeCertificateIsReadAfterInstallationCompletes() throws Exception {
        FakeCertManager certManager = new FakeCertManager();
        certManager.installed.put("new-runtime", true);

        CertificateInstallationVerifier.Result result = CertificateInstallationVerifier.installAndVerify(
                certManager, "draft", () -> "new-runtime");

        assertTrue(result.runtimeCertInstalled());
        assertEquals(List.of("draft", "new-runtime"), certManager.checkedIds);
    }

    private static final class FakeCertManager implements CertManager {
        private final Map<String, Boolean> installed = new HashMap<>();
        private final List<String> checkedIds = new ArrayList<>();
        private boolean confirmInstallation = true;

        @Override
        public void installCert(String certId) {
            installed.put(certId, confirmInstallation);
        }

        @Override
        public boolean checkInstalled(String certId) {
            checkedIds.add(certId);
            return Boolean.TRUE.equals(installed.get(certId));
        }

        @Override public CertificateConfig importCert(InputStream inputStream, InputStream priKeyInputStream) { return null; }
        @Override public List<CertificateConfig> getCertList() { return List.of(); }
        @Override public CertificateConfig getSelectedCert() { return null; }
        @Override public boolean deleteCertConfig(String certId) { return false; }
        @Override public CertificateConfig getCertConfigById(String certId) { return null; }
        @Override public X509Certificate getCertById(String certId) { return null; }
        @Override public PrivateKey getPriKeyById(String certId) { return null; }
        @Override public String getCertPEM(String id) { return null; }
        @Override public String getPriKeyPEM(String id) { return null; }
        @Override public String getCertSubject(X509Certificate certificate) { return null; }
        @Override public Map<String, String> getCertInfo(String certId) { return Map.of(); }
        @Override public boolean isCertMatchingPriKey(X509Certificate certificate, PrivateKey privateKey) { return false; }
        @Override public void checkSelectedCertInstalled() { }
        @Override public CertificateConfig getDefaultCert() { return null; }
        @Override public X509Certificate getServerCert(Integer port, String host) { return null; }
    }
}
