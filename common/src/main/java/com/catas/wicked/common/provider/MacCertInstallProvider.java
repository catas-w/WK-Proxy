package com.catas.wicked.common.provider;

import com.catas.wicked.common.jna.CertInstallerLibrary;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@Slf4j
@Singleton
@Requires(os = Requires.Family.MAC_OS)
public class MacCertInstallProvider implements CertInstallProvider {

    @Override
    public boolean checkCertInstalled(String certName, String SHA256) {
        ProcessBuilder processBuilder = new ProcessBuilder(
                "security", "find-certificate", "-a", "-c", certName, "-Z", "/Library/Keychains/System.keychain");
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            StringBuilder output = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            String res = output.toString();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                // Check if the output contains information about the certificate
                return res.contains(certName) && (res.contains(SHA256.toUpperCase()) || res.contains(SHA256));
            } else {
                log.debug("Certificate is not installed in the macOS system keychain: {}, output: {}",
                        certName, res.trim());
                return false;
            }
        } catch (Exception e) {
            log.error("Error in checking cert installation in MacOS.", e);
            return false;
        }
    }

    @Override
    public boolean install(String certPath) {
        if (StringUtils.isBlank(certPath)) {
            throw new IllegalArgumentException();
        }
        boolean accepted = CertInstallerLibrary.INSTANCE.installCert(certPath);
        log.info("Certificate installation request accepted in MacOS: {}, path: {}", accepted, certPath);
        return accepted;
    }
}
