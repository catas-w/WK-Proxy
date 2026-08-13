package com.catas.wicked.common.provider;

import com.catas.wicked.common.util.CommonUtils;
import io.micronaut.context.annotation.Requires;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Singleton
@Requires(os = Requires.Family.WINDOWS)
public class WinCertInstallProvider implements CertInstallProvider {

    private static final String POWERSHELL = "powershell.exe";
    private static final long STORE_ERROR_LOG_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(30);
    private static final AtomicLong NEXT_STORE_ERROR_LOG_NANOS = new AtomicLong();

    private final ProcessExecutor processExecutor;
    private final CertificateStoreReader certificateStoreReader;

    public WinCertInstallProvider() {
        this(WinCertInstallProvider::execute, new WindowsRootCertificateStore()::readCertificates);
    }

    WinCertInstallProvider(ProcessExecutor processExecutor, CertificateStoreReader certificateStoreReader) {
        this.processExecutor = processExecutor;
        this.certificateStoreReader = certificateStoreReader;
    }

    @PostConstruct
    public void init() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Override
    public boolean checkCertInstalled(String certName, String sha256) {
        if (StringUtils.isBlank(sha256)) {
            log.warn("Cannot check Windows certificate {} without a SHA-256 fingerprint.", certName);
            return false;
        }
        try {
            for (byte[] encodedCertificate : certificateStoreReader.read()) {
                if (encodedCertificate != null
                        && StringUtils.equalsIgnoreCase(sha256, CommonUtils.SHA256(encodedCertificate))) {
                    log.info("Certificate {} is installed in LocalMachine\\Root.", certName);
                    return true;
                }
            }
        } catch (Exception error) {
            logStoreReadError(certName, error);
            return false;
        }
        log.debug("Certificate {} is not installed in LocalMachine\\Root.", certName);
        return false;
    }

    @Override
    public boolean install(String certPath) {
        List<String> command = buildInstallCommand(certPath);
        try {
            int exitCode = processExecutor.execute(command);
            if (exitCode == 0) {
                log.info("Windows accepted and completed the certificate installation command.");
                return true;
            }
            log.warn("Windows certificate installation failed with exit code {}.", exitCode);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            log.warn("Windows certificate installation was interrupted.", error);
        } catch (Exception error) {
            log.error("Error installing certificate in LocalMachine\\Root.", error);
        }
        return false;
    }

    static List<String> buildInstallCommand(String certPath) {
        if (StringUtils.isBlank(certPath)) {
            throw new IllegalArgumentException("certPath must not be blank");
        }
        String escapedPath = certPath.replace("'", "''");
        String innerScript = "$ErrorActionPreference='Stop'; "
                + "Import-Certificate -FilePath '" + escapedPath + "' "
                + "-CertStoreLocation 'Cert:\\LocalMachine\\Root' "
                + "-Confirm:$false -ErrorAction Stop | Out-Null";
        String innerEncoded = encodePowerShell(innerScript);

        String outerScript = "$ErrorActionPreference='Stop'; try { "
                + "$process = Start-Process -FilePath 'powershell.exe' "
                + "-ArgumentList @('-NoProfile','-NonInteractive','-ExecutionPolicy','Bypass',"
                + "'-EncodedCommand','" + innerEncoded + "') "
                + "-Verb RunAs -Wait -PassThru -ErrorAction Stop; "
                + "exit $process.ExitCode } catch { Write-Error $_; exit 1 }";
        return List.of(POWERSHELL, "-NoProfile", "-NonInteractive", "-EncodedCommand",
                encodePowerShell(outerScript));
    }

    static String decodePowerShell(String encodedCommand) {
        return new String(Base64.getDecoder().decode(encodedCommand), StandardCharsets.UTF_16LE);
    }

    private static String encodePowerShell(String script) {
        return Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
    }

    private static int execute(List<String> command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.inheritIO();
        Process process = builder.start();
        return process.waitFor();
    }

    private static void logStoreReadError(String certName, Exception error) {
        long now = System.nanoTime();
        long nextLog = NEXT_STORE_ERROR_LOG_NANOS.get();
        if (now >= nextLog && NEXT_STORE_ERROR_LOG_NANOS.compareAndSet(
                nextLog, now + STORE_ERROR_LOG_INTERVAL_NANOS)) {
            if (error instanceof WindowsRootCertificateStore.CertificateStoreException storeError) {
                log.error("Error checking certificate {} in LocalMachine\\Root: operation={}, error={}.",
                        certName, storeError.operation(), storeError.errorCode(), storeError);
            } else {
                log.error("Error checking certificate {} in LocalMachine\\Root.", certName, error);
            }
            return;
        }
        log.debug("Suppressed repeated LocalMachine\\Root read failure for certificate {}.", certName, error);
    }

    @FunctionalInterface
    interface ProcessExecutor {
        int execute(List<String> command) throws Exception;
    }

    @FunctionalInterface
    interface CertificateStoreReader {
        List<byte[]> read() throws Exception;
    }
}
