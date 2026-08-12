package com.catas.wicked.common.provider;

import com.catas.wicked.common.util.CommonUtils;
import org.junit.Test;

import java.security.PublicKey;
import java.security.cert.Certificate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WinCertInstallProviderUnitTest {

    @Test
    public void installCommandWaitsForElevatedProcessAndUsesLocalMachineRoot() {
        List<String> command = WinCertInstallProvider.buildInstallCommand(
                "C:\\Users\\测试 User\\Wizard's CA.crt");

        String outerScript = WinCertInstallProvider.decodePowerShell(command.get(4));
        assertTrue(outerScript.contains("-Verb RunAs -Wait -PassThru"));
        String innerEncoded = between(outerScript, "'-EncodedCommand','", "') -Verb RunAs");
        String innerScript = WinCertInstallProvider.decodePowerShell(innerEncoded);
        assertTrue(innerScript.contains("Cert:\\LocalMachine\\Root"));
        assertTrue(innerScript.contains("-Confirm:$false -ErrorAction Stop"));
        assertTrue(innerScript.contains("C:\\Users\\测试 User\\Wizard''s CA.crt"));
    }

    @Test
    public void installUsesElevatedChildExitCode() {
        AtomicReference<List<String>> captured = new AtomicReference<>();
        WinCertInstallProvider provider = new WinCertInstallProvider(command -> {
            captured.set(command);
            return 0;
        }, List::of);

        assertTrue(provider.install("C:\\ca.crt"));
        assertTrue(captured.get().contains("-EncodedCommand"));
    }

    @Test
    public void installRejectsNonZeroExitCodeAndLaunchFailure() {
        WinCertInstallProvider failed = new WinCertInstallProvider(command -> 5, List::of);
        WinCertInstallProvider rejected = new WinCertInstallProvider(command -> {
            throw new IllegalStateException("UAC rejected");
        }, List::of);

        assertFalse(failed.install("C:\\ca.crt"));
        assertFalse(rejected.install("C:\\ca.crt"));
    }

    @Test
    public void certificateCheckMatchesFingerprintWithoutDependingOnAliasOrCommonName() {
        byte[] encoded = {1, 2, 3, 4};
        Certificate certificate = new EncodedCertificate(encoded);
        WinCertInstallProvider provider = new WinCertInstallProvider(command -> 0,
                () -> List.of(certificate));

        assertTrue(provider.checkCertInstalled("different-common-name", CommonUtils.SHA256(encoded)));
        assertFalse(provider.checkCertInstalled("different-common-name", CommonUtils.SHA256(new byte[]{9})));
    }

    private static String between(String value, String start, String end) {
        int from = value.indexOf(start) + start.length();
        return value.substring(from, value.indexOf(end, from));
    }

    private static final class EncodedCertificate extends Certificate {
        private final byte[] encoded;

        private EncodedCertificate(byte[] encoded) {
            super("X.509");
            this.encoded = encoded.clone();
        }

        @Override public byte[] getEncoded() { return encoded.clone(); }
        @Override public void verify(PublicKey key) { }
        @Override public void verify(PublicKey key, String sigProvider) { }
        @Override public String toString() { return "test-certificate"; }
        @Override public PublicKey getPublicKey() { return null; }
    }
}
