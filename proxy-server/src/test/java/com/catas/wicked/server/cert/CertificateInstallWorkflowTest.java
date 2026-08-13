package com.catas.wicked.server.cert;

import com.catas.wicked.common.provider.CertInstallProvider;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class CertificateInstallWorkflowTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void keepsTemporaryFileUntilInstallationIsConfirmed() throws Exception {
        AtomicLong clock = new AtomicLong();
        CertificateInstallAwaiter awaiter = awaiter(clock, false);
        CertificateInstallWorkflow workflow = new CertificateInstallWorkflow(awaiter);
        File certificate = temporaryFolder.newFile("certificate.crt");
        RecordingProvider provider = new RecordingProvider(certificate, true, 3);

        workflow.install(certificate, "certificate".getBytes(StandardCharsets.UTF_8),
                "Wizard Proxy", "ABC123", provider);

        assertEquals(3, provider.checkCount.get());
        assertEquals("Wizard Proxy", provider.lastName);
        assertEquals("ABC123", provider.lastSha256);
        assertFalse(certificate.exists());
    }

    @Test
    public void rejectedInstallationDoesNotPollAndDeletesTemporaryFile() throws Exception {
        AtomicLong clock = new AtomicLong();
        CertificateInstallWorkflow workflow = new CertificateInstallWorkflow(awaiter(clock, false));
        File certificate = temporaryFolder.newFile("rejected.crt");
        RecordingProvider provider = new RecordingProvider(certificate, false, 1);

        assertThrows(CertificateInstallWorkflow.InstallRejectedException.class,
                () -> workflow.install(certificate, new byte[]{1}, "Wizard Proxy", "ABC123", provider));

        assertEquals(0, provider.checkCount.get());
        assertFalse(certificate.exists());
    }

    @Test
    public void timeoutDeletesTemporaryFile() throws Exception {
        AtomicLong clock = new AtomicLong();
        CertificateInstallWorkflow workflow = new CertificateInstallWorkflow(awaiter(clock, false));
        File certificate = temporaryFolder.newFile("timeout.crt");
        RecordingProvider provider = new RecordingProvider(certificate, true, Integer.MAX_VALUE);

        assertThrows(CertificateInstallWorkflow.InstallTimeoutException.class,
                () -> workflow.install(certificate, new byte[]{1}, "Wizard Proxy", "ABC123", provider));

        assertTrue(provider.checkCount.get() > 1);
        assertFalse(certificate.exists());
    }

    @Test
    public void interruptionDeletesTemporaryFile() throws Exception {
        AtomicLong clock = new AtomicLong();
        CertificateInstallWorkflow workflow = new CertificateInstallWorkflow(awaiter(clock, true));
        File certificate = temporaryFolder.newFile("interrupted.crt");
        RecordingProvider provider = new RecordingProvider(certificate, true, Integer.MAX_VALUE);

        assertThrows(InterruptedException.class,
                () -> workflow.install(certificate, new byte[]{1}, "Wizard Proxy", "ABC123", provider));

        assertFalse(certificate.exists());
    }

    private static CertificateInstallAwaiter awaiter(AtomicLong clock, boolean interrupt) {
        return new CertificateInstallAwaiter(Duration.ofSeconds(3), Duration.ofSeconds(1), clock::get, nanos -> {
            assertTrue(nanos > 0);
            if (interrupt) {
                throw new InterruptedException("test");
            }
            clock.addAndGet(nanos);
        });
    }

    private static final class RecordingProvider implements CertInstallProvider {
        private final File certificate;
        private final boolean accepted;
        private final int successfulCheck;
        private final AtomicInteger checkCount = new AtomicInteger();
        private String lastName;
        private String lastSha256;

        private RecordingProvider(File certificate, boolean accepted, int successfulCheck) {
            this.certificate = certificate;
            this.accepted = accepted;
            this.successfulCheck = successfulCheck;
        }

        @Override
        public boolean checkCertInstalled(String certName, String sha256) {
            assertTrue(certificate.exists());
            lastName = certName;
            lastSha256 = sha256;
            return checkCount.incrementAndGet() >= successfulCheck;
        }

        @Override
        public boolean install(String certPath) {
            assertTrue(certificate.exists());
            assertEquals(certificate.getAbsolutePath(), certPath);
            return accepted;
        }
    }
}
