package com.catas.wicked.server.cert;

import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.config.CertificateConfig;
import com.catas.wicked.common.provider.CertInstallProvider;
import com.catas.wicked.common.provider.CertManager;
import com.catas.wicked.common.provider.ResourceMessageProvider;
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

    @Inject
    private ResourceMessageProvider resourceMessageProvider;

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

        List<CertificateConfig> configs = new ArrayList<>();
        try {
            configs = objectMapper.readValue(certFile, new TypeReference<List<CertificateConfig>>() {});
            log.info("Load custom certs successfully: {}", configs.size());
        } catch (Exception e) {
            log.error("Error in loading custom certs", e);
            objectMapper.writeValue(certFile, Collections.emptyList());
        }
        customCertList.addAll(configs);

        initAppCertConfig(getSelectedCert());
    }

    private void initAppCertConfig(CertificateConfig certConfig) {
        try {
            X509Certificate caCert = getCertById(certConfig.getId());
            PrivateKey caPriKey = getPriKeyById(certConfig.getId());
            appConfig.updateRootCertConfigs(getCertSubject(caCert), caCert, caPriKey);
            // checkSelectedCertInstalled();

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
            throw new RuntimeException(resourceMessageProvider.getMessage("cert-out-number.alert"));
        }
        if (certInputStream == null) {
            throw new IllegalArgumentException();
        }
        try {
            X509Certificate cert = null;
            try {
                cert = certService.loadCert(certInputStream);
            } catch (IllegalArgumentException | CertificateException ex) {
                throw new RuntimeException(resourceMessageProvider.getMessage("cert-parsed-error.alert"));
            }

            PrivateKey privateKey = null;
            try {
                privateKey = certService.loadPriKey(priKeyInputStream);
            } catch (IllegalArgumentException | InvalidKeySpecException e) {
                throw new RuntimeException(resourceMessageProvider.getMessage("pri-key-parsed-error.alert"));
            }

            // check match
            boolean certMatchingPriKey = isCertMatchingPriKey(cert, privateKey);
            if (!certMatchingPriKey) {
                throw new RuntimeException(resourceMessageProvider.getMessage("cert-key-not-match.alert"));
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
            throw new RuntimeException(resourceMessageProvider.getMessage("cert-format-error.alert"), e);
        } catch (Exception e) {
            throw new RuntimeException(resourceMessageProvider.getMessage("cert-load-error.alert"), e);
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
}
