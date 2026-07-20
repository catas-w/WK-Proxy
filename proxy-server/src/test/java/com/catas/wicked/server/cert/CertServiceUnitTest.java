package com.catas.wicked.server.cert;

import com.catas.wicked.server.cert.spi.BouncyCastleCertGenerator;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public class CertServiceUnitTest {

    @Test
    public void generatedCaPemCanSignAHostCertificate() throws Exception {
        CertService certService = new CertService();
        certService.setCertGenerator(new BouncyCastleCertGenerator());
        String subject = "C=CN, ST=Shanghai, L=Shanghai, O=WK Proxy, CN=Unit Test CA";

        Pair<String, String> pem = certService.generateCaCertPEM(subject, "2024-01-01");
        PrivateKey caPrivateKey = certService.loadPriKey(
                new ByteArrayInputStream(pem.getLeft().getBytes(StandardCharsets.UTF_8)));
        X509Certificate caCertificate = certService.loadCert(
                new ByteArrayInputStream(pem.getRight().getBytes(StandardCharsets.UTF_8)));

        X509Certificate serverCertificate = certService.genCert(
                certService.getSubject(caCertificate),
                caPrivateKey,
                caCertificate.getNotBefore(),
                caCertificate.getNotAfter(),
                certService.genKeyPair().getPublic(),
                "example.test");

        serverCertificate.verify(caCertificate.getPublicKey());
        Assert.assertTrue(serverCertificate.getSubjectX500Principal().getName().contains("CN=example.test"));
        Assert.assertTrue(serverCertificate.getSubjectAlternativeNames().stream()
                .anyMatch(name -> "example.test".equals(name.get(1))));
    }
}
