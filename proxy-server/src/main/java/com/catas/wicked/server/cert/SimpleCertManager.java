package com.catas.wicked.server.cert;

import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.config.CertificateConfig;
import com.catas.wicked.common.provider.CertInstallProvider;
import com.catas.wicked.common.provider.CertManager;
import com.catas.wicked.common.util.AesUtils;
import com.catas.wicked.common.util.AlertUtils;
import com.catas.wicked.common.util.CommonUtils;
import com.catas.wicked.common.util.IdUtil;
import com.catas.wicked.common.util.SystemUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Parallel;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.scene.control.Alert;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import javax.crypto.SecretKey;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

import static com.catas.wicked.common.constant.ProxyConstant.CERT_FILE_PATTERN;
import static com.catas.wicked.common.constant.ProxyConstant.PRIVATE_FILE_PATTERN;

@Slf4j
@Singleton
public class SimpleCertManager implements CertManager {

    @Inject
    private CertService certService;

    @Inject
    private ApplicationConfig appConfig;

    @Inject
    private CertInstallProvider certInstallProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final List<CertificateConfig> customCertList = new ArrayList<>();

    private final Map<Integer, Map<String, X509Certificate>> serverCertCache = new WeakHashMap<>();

    private CertificateConfig defaultCert;

    private static final String DEFAULT_CERT_FILE = "default-cert.data";
    public static final String DEFAULT_CERT_ID = "_default_";
    public static final String DEFAULT_CERT_NAME = "Built-in";
    private static final String DEFAULT_START_DATE = "2024-01-01";
    private static final String DEFAULT_SUBJECT = "C=CN, ST=Shanghai, L=Shanghai, O=Catas, CN=catas.org";
    private static final String KEY = "yz7EZiJZ5/bPmoq6/UrqDQ==";
    private static final SecretKey secretKey = AesUtils.stringToSecretKey(KEY);

    private static final int LIMIT = 5;

    @Parallel
    @PostConstruct
    public void init() throws IOException {
        // load custom certs
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        File certFile = getCertStorageFile();
        if (!certFile.exists()) {
            log.warn("custom certs not exist");
            certFile.getParentFile().mkdirs();
            certFile.createNewFile();
            objectMapper.writeValue(certFile, Collections.emptyList());
            return;
        }

        List<CertificateConfig> configs = objectMapper.readValue(certFile, new TypeReference<List<CertificateConfig>>() {});
        log.info("Load custom certs: {}", configs.stream().map(CertificateConfig::getName).collect(Collectors.toList()));
        customCertList.addAll(configs);

        initAppCertConfig(getSelectedCert());
    }

    private void initAppCertConfig(CertificateConfig certConfig) {
        try {
            X509Certificate caCert = getCertById(certConfig.getId());
            PrivateKey caPriKey = getPriKeyById(certConfig.getId());
            appConfig.updateRootCertConfigs(getCertSubject(caCert), caCert, caPriKey);
            checkSelectedCertInstalled();

            log.info("Init appCertConfig: {}", certConfig.getName());
            KeyPair keyPair = certService.genKeyPair();
            appConfig.setServerPriKey(keyPair.getPrivate());
            appConfig.setServerPubKey(keyPair.getPublic());
        } catch (Exception e) {
            log.error("Error in initAppCertConfig", e);
            if (certConfig.isDefault()) {
                AlertUtils.alertLater(Alert.AlertType.ERROR, "Certificate init error!");
            } else {
                appConfig.getSettings().setSelectedCert(getDefaultCert().getId());
                appConfig.updateSettingsAsync();
                initAppCertConfig(getDefaultCert());
            }
        }
    }

    @Override
    public CertificateConfig importCert(InputStream certInputStream, InputStream priKeyInputStream) {
        if (customCertList.size() > LIMIT) {
            throw new RuntimeException("Certificate number has reached limit");
        }
        if (certInputStream == null) {
            throw new IllegalArgumentException();
        }
        try {
            X509Certificate cert = null;
            try {
                cert = certService.loadCert(certInputStream);
            } catch (IllegalArgumentException | CertificateException ex) {
                throw new RuntimeException("Certificate Parsed Error!");
            }

            PrivateKey privateKey = null;
            try {
                privateKey = certService.loadPriKey(priKeyInputStream);
            } catch (IllegalArgumentException | InvalidKeySpecException e) {
                throw new RuntimeException("Private Key Parsed Error!");
            }

            // check match
            boolean certMatchingPriKey = isCertMatchingPriKey(cert, privateKey);
            if (!certMatchingPriKey) {
                throw new RuntimeException("Certificate and Private Key not match!");
            }

            String subject = certService.getSubject(cert);
            Map<String, String> subjectMap = certService.getSubjectMap(cert);

            log.info("import cert: {}", subjectMap);
            String name = subjectMap.getOrDefault("CN", subject);

            // byte[] encodedCert = cert.getEncoded();
            byte[] encoded = cert.getEncoded();
            String certStr = Base64.getEncoder().encodeToString(encoded);
            String encryptCert = AesUtils.encrypt(certStr, secretKey);

            String priKeyStr = Base64.getEncoder().encodeToString(privateKey.getEncoded());
            String encryptPriKey = AesUtils.encrypt(certService.formatPEM(priKeyStr, PRIVATE_FILE_PATTERN), secretKey);

            CertificateConfig config = CertificateConfig.builder()
                    .id(IdUtil.getSimpleId())
                    .name(name)
                    .cert(encryptCert)
                    .privateKey(encryptPriKey)
                    .isDefault(false)
                    .build();

            customCertList.add(config);
            objectMapper.writeValue(getCertStorageFile(), customCertList);

            return config;
        } catch (RuntimeException e) {
            throw e;
        } catch (CertificateException e) {
            throw new RuntimeException("Certificate format incorrect!", e);
        } catch (Exception e) {
            throw new RuntimeException("Certificate load error!", e);
        }
    }

    @Override
    public List<CertificateConfig> getCertList() {
        List<CertificateConfig> list = new ArrayList<>();
        list.add(getDefaultCert());
        list.addAll(customCertList);
        return list;
    }

    @Override
    public CertificateConfig getSelectedCert() {
        String id = appConfig.getSettings().getSelectedCert();
        CertificateConfig selectedCert = getCertConfigById(id);
        if (selectedCert == null) {
            log.warn("Selected cert is null: {}", id);
            return getDefaultCert();
        }
        return selectedCert;
    }

    @Override
    public boolean deleteCertConfig(String certId) {
        boolean res = customCertList.removeIf(config -> config.getId().equals(certId));
        if (res) {
            try {
                objectMapper.writeValue(getCertStorageFile(), customCertList);
            } catch (IOException e) {
                log.error("Error in deleting cert.", e);
            }
        }
        return res;
    }

    @Override
    public CertificateConfig getCertConfigById(String certId) {
        if (certId == null) {
            return null;
        }
        if (certId.equals(DEFAULT_CERT_ID)) {
            return getDefaultCert();
        }
        return customCertList.stream().filter(cert -> cert.getId().equals(certId)).findFirst().orElse(null);
    }

    @Override
    public X509Certificate getCertById(String certId) throws Exception {
        String certPEM = getCertPEM(certId);
        if (certPEM == null) {
            return null;
        }
        return certService.loadCert(new ByteArrayInputStream(certPEM.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public PrivateKey getPriKeyById(String certId) throws Exception {
        String priKeyPEM = getPriKeyPEM(certId);
        if (priKeyPEM == null) {
            return null;
        }
        return certService.loadPriKey(new ByteArrayInputStream(priKeyPEM.getBytes()));
    }

    @Override
    public String getCertPEM(String id) throws Exception {
        CertificateConfig config = getCertConfigById(id);
        if (config == null) {
            return null;
        }
        if (config.isDefault()) {
            return config.getCert();
        }

        String certPEM = AesUtils.decrypt(config.getCert(), secretKey);
        return String.format(CERT_FILE_PATTERN, certPEM);
    }

    @Override
    public String getPriKeyPEM(String id) throws Exception {
        CertificateConfig config = getCertConfigById(id);
        if (config == null) {
            return null;
        }
        if (config.isDefault()) {
            return config.getPrivateKey();
        }

        return AesUtils.decrypt(config.getPrivateKey(), secretKey);
    }

    @Override
    public String getCertSubject(X509Certificate certificate) throws Exception {
        if (certificate == null) {
            return null;
        }
        return certService.getSubject(certificate);
    }

    @Override
    public Map<String, String> getCertInfo(String certId) throws Exception {
        X509Certificate certificate = getCertById(certId);
        Map<String, String> map = certService.getSubjectMap(certificate);
        map.put("Version", String.valueOf(certificate.getVersion()));
        map.put("Serial Number", String.valueOf(certificate.getSerialNumber()));
        map.put("Issuer", certificate.getIssuerX500Principal().getName());
        map.put("Valid From", String.valueOf(certificate.getNotBefore()));
        map.put("Valid Until", String.valueOf(certificate.getNotAfter()));
        map.put("Public Key Algorithm", certificate.getPublicKey().getAlgorithm());
        map.put("Signature Algorithm", certificate.getSigAlgName());
        map.put("SHA256", CommonUtils.SHA256(certificate.getEncoded()));

        String sig= CommonUtils.toHexString(certificate.getSignature(), ':');
        map.put("Signature", sig);

        return map;
    }

    @Override
    public boolean isCertMatchingPriKey(X509Certificate certificate, PrivateKey privateKey) {
        try {
            String data = "My tea's gone cold, I'm wondering why";

            // Sign the data using the private key
            String algName = certificate.getSigAlgName();
            Signature signature = Signature.getInstance(algName);
            signature.initSign(privateKey);
            signature.update(data.getBytes());
            byte[] signedData = signature.sign();

            // Verify the signature using the public key from the certificate
            Signature signatureVerify = Signature.getInstance(algName);
            signatureVerify.initVerify(certificate.getPublicKey());
            signatureVerify.update(data.getBytes());

            return signatureVerify.verify(signedData);
        } catch (Exception e) {
            log.error("Error in checking cert matching private key.", e);
            return false;
        }
    }

    @Override
    public boolean checkInstalled(String certId) {
        try {
            Map<String, String> certInfoMap = getCertInfo(certId);
            return certInstallProvider.checkCertInstalled(certInfoMap.get("CN"), certInfoMap.get("SHA256"));
        } catch (Exception e) {
            log.error("Error in check cert installation.", e);
        }
        return false;
    }

    @Override
    public void checkSelectedCertInstalled() {
        String certId = appConfig.getSettings().getSelectedCert();
        boolean res = checkInstalled(certId);
        log.info("checkSelectedCertInstalled result: " + res);
        appConfig.getObservableConfig().setCertInstalledStatus(res);
    }

    @Override
    public void installCert(String certId) throws Exception {
        String certPEM = getCertPEM(certId);
        if (StringUtils.isBlank(certId)) {
            throw new RuntimeException("Cannot Parse Certificate!");
        }

        File tempFile = SystemUtils.getStoragePath("temp_" + IdUtil.getSimpleId() + ".crt").toFile();
        if (!tempFile.exists()) {
            tempFile.getParentFile().mkdirs();
        }
        FileUtils.writeByteArrayToFile(tempFile, certPEM.getBytes(StandardCharsets.UTF_8));

        log.info("Trying to install {}", tempFile.getAbsoluteFile());
        boolean res = certInstallProvider.install(tempFile.getAbsolutePath());
        if (!res) {
            throw new RuntimeException("Failed To Install Certificate!");
        }
    }

    private File getCertStorageFile() {
        return SystemUtils.getStoragePath("certs.data").toFile();
    }

    @Override
    public CertificateConfig getDefaultCert() {
        if (this.defaultCert != null) {
            return this.defaultCert;
        }

        File defaultCertFile = SystemUtils.getStoragePath(DEFAULT_CERT_FILE).toFile();
        if (!defaultCertFile.exists()) {
            defaultCertFile.getParentFile().mkdirs();
        }
        if (defaultCertFile.exists()) {
            try {
                CertificateConfig config = objectMapper.readValue(defaultCertFile, CertificateConfig.class);
                assert config != null;
                log.info("read default cert:" + config);

                config.setCert(AesUtils.decrypt(config.getCert(), secretKey));
                config.setPrivateKey(AesUtils.decrypt(config.getPrivateKey(), secretKey));
                assert DEFAULT_CERT_ID.equals(config.getId()) && DEFAULT_CERT_NAME.equals(config.getName())
                        && StringUtils.isNotBlank(config.getCert()) && StringUtils.isNotBlank(config.getPrivateKey());
                return config;
            } catch (Exception e) {
                log.error("Error loading default cert.", e);
            }
        }

        // generate default cert
        try {
            Pair<String, String> pair = certService.generateCaCertPEM(DEFAULT_SUBJECT, DEFAULT_START_DATE);
            String certPEM = pair.getRight();
            String priKeyPEM = pair.getLeft();
            CertificateConfig config = CertificateConfig.builder()
                    .id(DEFAULT_CERT_ID)
                    .name(DEFAULT_CERT_NAME)
                    .cert(AesUtils.encrypt(certPEM, secretKey))
                    .privateKey(AesUtils.encrypt(priKeyPEM, secretKey))
                    .isDefault(true)
                    .build();

            // save to file
            log.info("to save default cert-file:" + config);
            objectMapper.writeValue(defaultCertFile, config);

            config.setCert(certPEM);
            config.setPrivateKey(priKeyPEM);
            this.defaultCert = config;
            return config;
        } catch (Exception e) {
            log.error("Error in generating default-cert data.", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public X509Certificate getServerCert(Integer port, String host) throws Exception {
        if (StringUtils.isBlank(host)) {
            return null;
        }
        X509Certificate cert;
        Map<String, X509Certificate> portCertCache = serverCertCache.computeIfAbsent(port, k -> new HashMap<>());
        String key = host.trim().toLowerCase();
        if (portCertCache.containsKey(key)) {
            return portCertCache.get(key);
        } else {
            cert = certService.genCert(appConfig.getIssuer(), appConfig.getCaPriKey(),
                    appConfig.getCaNotBefore(), appConfig.getCaNotAfter(), appConfig.getServerPubKey(), key);
            portCertCache.put(key, cert);
        }
        return cert;
    }

    @Deprecated
    static class DefaultCertHolder {
        private final static String DEFAULT_CERT = """
            -----BEGIN CERTIFICATE-----
            MIIDJjCCAg6gAwIBAgIGAYip42nwMA0GCSqGSIb3DQEBCwUAMEcxCzAJBgNVBAYTA
            kNOMQswCQYDVQQIDAJTQzELMAkGA1UEBwwCQ0QxDjAMBgNVBAoMBUNhdGFzMQ4wD
            AYDVQQDDAVDYXRhczAgFw0yMjEyMzExNjAwMDBaGA8yMDUyMTIzMTE2MDAwMFowR
            zELMAkGA1UEBhMCQ04xCzAJBgNVBAgMAlNDMQswCQYDVQQHDAJDRDEOMAwGA1UEC
            gwFQ2F0YXMxDjAMBgNVBAMMBUNhdGFzMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AM
            IIBCgKCAQEArJAEmjwUVgtJi8BE9euB70SZ3WXK0jM2HdqSQoVGj8RneUyZlgd73
            TK1peZEnJ4GH7moS0/s2IUneVtIr55Bmv86dQ1T9167Kr2C+uqYgKb/M0ORmYjTY
            aq8Ys9JlUuADoxpw5orr6wS1Axv/4oXQx7/7hRU1YXyOB0YC85uOlYhsQwB4+Rtr
            /Tk9xMf9rIw2L2S9Gj+ikqTaTTiz+80Za4xiU9Vp2xikUwPUAhNkMkg98ioxFPsi
            eIVI8khgtKs3Qp4oU3vAZ9WYUa8eRSj5Z30DyPZ0AN2I9CjnE+vLhTB5Qv8Y8/7k
            kpsl/cTQO5Kwt9nqUpx6VKuWtgEBEDTRwIDAQABoxYwFDASBgNVHRMBAf8ECDAGA
            QH/AgEAMA0GCSqGSIb3DQEBCwUAA4IBAQA4AIEjk3ivh1g563I+eZOeEFzAIMcti
            DV4UYx7odDMpsa9u2iJ0VzzotrZsDrO38MVF7osUr0CSYzgtlJY9PNTeTbM1ZHMg
            ZfCp9uGIe2W3s4CaPVgAuWNb30W/aMNs3w87Nd3D/ukyq1tBiU34mQbu1VwHD+az
            t3CZdirw3lBAoF+/P2tapX/r4JGO6v8EnYcjRhkFnErZ0iYgESTM6RJgOgoHg6v9
            megi8vb7XfdTo/UkZF/MvG7d7f0Ug51Z44TwMV/3aFUMim5qjGLYLhuMW6KsJ37l
            uFb6Vg1Jc+Nh55XYuUloyMaPYaKoegiICakVb60q+k7lVf0Vl8SioG3
            -----END CERTIFICATE-----
            """;

        private final static String DEFAULT_PRI_KEY = """
            -----BEGIN PRIVATE KEY-----
            MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCskASaPBRWC0mLw
            ET164HvRJndZcrSMzYd2pJChUaPxGd5TJmWB3vdMrWl5kScngYfuahLT+zYhSd5W
            0ivnkGa/zp1DVP3XrsqvYL66piApv8zQ5GZiNNhqrxiz0mVS4AOjGnDmiuvrBLUD
            G//ihdDHv/uFFTVhfI4HRgLzm46ViGxDAHj5G2v9OT3Ex/2sjDYvZL0aP6KSpNpN
            OLP7zRlrjGJT1WnbGKRTA9QCE2QySD3yKjEU+yJ4hUjySGC0qzdCnihTe8Bn1ZhR
            rx5FKPlnfQPI9nQA3Yj0KOcT68uFMHlC/xjz/uSSmyX9xNA7krC32epSnHpUq5a2
            AQEQNNHAgMBAAECggEAC8jD/7JloyCfM6N8Mh0UoP3a0hM/AA7OPcis300RrgS4G
            kEAZg35x/fMDtnESrvB6E257/az0m95bCCvPIr+qPKQD+lKmCSIfJk3hiIaKwL8f
            4g4O7dr1AogVlEYkdD3nEa7fEedfyAtovx8c4N/Ji7KRHxv9KryiSF3gGrm/SXC4
            HsCgl+/IwO4rBGK2cwdpiFItLSMz/WTDomwKM3HLKwYfQn6TXJWkI1sLdSVae2xz
            lhp51NS7OoiQW7AxLkfJMz4TzO5lt940vsd6Qe7Irkt2sDRVnFqil1h/oL6C08bo
            fFRXXAHMGafcjtM9wKHxQWhWyeNuYr8bEubcoOnoQKBgQDf9mLio/QujgxCbE9yP
            b+tXVHxviXmyZ3S8uXAJJcWkOEuW7Djqbau1KseTkZnS4LKEQ2v7fWhZuORLNJdd
            yZBb8P5waaopp18y18IFcyXDZBTpc2bvVa3ReogpZTAf5UY5a5dUmCUwds98cPuX
            tZYGDBOaS/Yf6mRNIrnngjNIQKBgQDFP1fenru8cYLWF2+vP1djsXqC3iixnvYyM
            tPM3orExfEgDoD/BdL87k7RMGOl5BXR9ymylOsMkxMnsmFY2oQhL29x2cnV84dCJ
            oLH6c756cvBiMmGRf9fU4I4CZRRpoKx8kxbnUR3LXPo9X5v4jVN5fMAnABZ6jTMi
            cynXEnrZwKBgQDFr73pP78xuMCpgOE/fbHLXSwPrj/WTvC4Wx7hU2rpyEh7mwOwc
            QWHnMDOp1kNpGF160ehmvHN3fvRETc+uQVTjUv3ETACfc9VcE4Z3OQSES7sJtYuO
            hpo+5WF66I5qGP67gHS7fvCUrsrGlRP+/ZHHFLHY6GQAF+0hCR3/c7XAQKBgGVEi
            yl5l2s9X+3o5Gc1/vjpErwDsSLYORA2tjT1gItcENrjd2j3JYbEFuUhkdeGzYkZ+
            2d7O14eWHRGCeKjdbjP8MikBxL4T9YvjW9z49a9KOY5AzYT0/OuHSoGCQcOlQUlH
            /zdNlx1ko/lEkABBFr1FxiV/zwqmWb1zIOUR0M7AoGBAINpnwCHFqKj6LQbASrNS
            A7eQnG7U8VpOR4mvDzoBc/6Mk4Y4X7ggnp72pvBe0i+0dZtz7q0U/zin41Z8sV22
            bgS82fs4/JS6MITgb07K2/KXUuIyjKScVsnt9eC/d3FnXpASQvApSv4wpXTp/svA
            yujYbyIpOP7HNC/PJxVh9On
            -----END PRIVATE KEY-----
            """;

        private static final CertificateConfig INSTANCE = CertificateConfig.builder()
                .id("_default_")
                .name("Built-in")
                .cert(DEFAULT_CERT)
                .privateKey(DEFAULT_PRI_KEY)
                .isDefault(true)
                .build();
    }
}
