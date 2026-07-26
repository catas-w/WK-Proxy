package com.catas.wicked.proxy.service.settings;

import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.config.Settings;
import com.catas.wicked.common.provider.CertManager;
import com.catas.wicked.server.proxy.ProxyServer;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Singleton
public class SettingsCommitService {

    private final ApplicationConfig applicationConfig;
    private final ProxyServer proxyServer;
    private final CertManager certManager;
    private final PortAvailabilityChecker portAvailabilityChecker;
    private final ThreadPoolExecutor executor;

    public SettingsCommitService(ApplicationConfig applicationConfig,
                                 ProxyServer proxyServer,
                                 CertManager certManager,
                                 PortAvailabilityChecker portAvailabilityChecker) {
        this.applicationConfig = applicationConfig;
        this.proxyServer = proxyServer;
        this.certManager = certManager;
        this.portAvailabilityChecker = portAvailabilityChecker;
        this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(4), runnable -> {
                    Thread thread = new Thread(runnable, "settings-commit");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    public CompletionStage<SettingsApplyResult> apply(SettingsDraft draft) {
        Settings before = draft.baseline();
        Settings candidate = draft.snapshot();
        SettingsChangeSet changes = SettingsChangeSet.between(before, candidate);
        if (!changes.hasChanges()) {
            return CompletableFuture.completedFuture(SettingsApplyResult.success(changes));
        }

        try {
            return CompletableFuture.supplyAsync(() -> applyInternal(before, candidate, changes), executor);
        } catch (RuntimeException error) {
            return CompletableFuture.completedFuture(SettingsApplyResult.failure(changes, error));
        }
    }

    private SettingsApplyResult applyInternal(Settings before, Settings candidate, SettingsChangeSet changes) {
        boolean switchedPort = false;
        try {
            if (changes.portChanged() && !portAvailabilityChecker.isAvailable(candidate.getPort())) {
                throw new IllegalStateException("Port " + candidate.getPort() + " is unavailable");
            }

            if (changes.certificateChanged()) {
                loadCertificate(candidate.getSelectedCert());
            }

            applicationConfig.replaceSettings(candidate);
            if (changes.portChanged()) {
                switchedPort = true;
                proxyServer.restart();
            }
            if (changes.certificateChanged()) {
                loadCertificate(candidate.getSelectedCert());
                certManager.checkSelectedCertInstalled();
            }
            applicationConfig.persistSettings();
            return SettingsApplyResult.success(changes);
        } catch (Throwable error) {
            applicationConfig.replaceSettings(before);
            if (switchedPort) {
                try {
                    proxyServer.restart();
                } catch (Throwable rollbackError) {
                    error.addSuppressed(rollbackError);
                }
            }
            if (changes.certificateChanged()) {
                try {
                    loadCertificate(before.getSelectedCert());
                } catch (Throwable rollbackError) {
                    error.addSuppressed(rollbackError);
                }
            }
            return SettingsApplyResult.failure(changes, error);
        }
    }

    private void loadCertificate(String certId) throws Exception {
        X509Certificate certificate = certManager.getCertById(certId);
        PrivateKey privateKey = certManager.getPriKeyById(certId);
        applicationConfig.updateRootCertConfigs(
                certManager.getCertSubject(certificate), certificate, privateKey);
    }

    @PreDestroy
    public void close() {
        executor.shutdownNow();
    }
}
