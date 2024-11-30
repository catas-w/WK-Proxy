package com.catas.wicked.server.cert;

import com.catas.wicked.common.util.CommonUtils;
import com.catas.wicked.server.cert.spi.CertGenerator;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.catas.wicked.common.constant.ProxyConstant.CERT_FILE_PATTERN;
import static com.catas.wicked.common.constant.ProxyConstant.PRIVATE_FILE_PATTERN;
import static com.catas.wicked.common.constant.ProxyConstant.START_DATE;
import static com.catas.wicked.common.constant.ProxyConstant.SUBJECT;

@Singleton
public class CertService {

    private final KeyFactory keyFactory;

    private CertGenerator certGenerator;

    public static final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    @Inject
    public void setCertGenerator(CertGenerator certGenerator) {
        this.certGenerator = certGenerator;
    }

    public CertService() {
        try {
            this.keyFactory = KeyFactory.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 生成RSA公私密钥对,长度为2048
     */
    public KeyPair genKeyPair() throws Exception {
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
        keyPairGen.initialize(2048, new SecureRandom());
        return keyPairGen.genKeyPair();
    }

    /**
     * 从文件加载RSA私钥
     * openssl pkcs8 -topk8 -nocrypt -inform PEM -outform DER -in ca.key -out ca_private.der
     */
    public PrivateKey loadPriKey(byte[] bts) throws InvalidKeySpecException {
        EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(bts));
        return keyFactory.generatePrivate(privateKeySpec);
    }

    /**
     * 从文件加载RSA私钥
     * openssl pkcs8 -topk8 -nocrypt -inform PEM -outform DER -in ca.key -out ca_private.der
     */
    public PrivateKey loadPriKey(String path) throws Exception {
        return loadPriKey(Files.readAllBytes(Paths.get(path)));
    }

    /**
     * 从文件加载RSA私钥
     * openssl pkcs8 -topk8 -nocrypt -inform PEM -outform DER -in ca.key -out ca_private.der
     */
    public PrivateKey loadPriKey(InputStream inputStream)
            throws IOException, InvalidKeySpecException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] bts = new byte[1024];
        int len;
        while ((len = inputStream.read(bts)) != -1) {
            outputStream.write(bts, 0, len);
        }
        inputStream.close();
        outputStream.close();

        String[] pattern = PRIVATE_FILE_PATTERN.split("\\n");
        String content = outputStream.toString(StandardCharsets.UTF_8)
                .replace(pattern[0], "")
                .replace(pattern[2], "")
                .replaceAll("\\n", "");
        return loadPriKey(content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 从文件加载RSA公钥
     * openssl rsa -in ca.key -pubout -outform DER -out ca_pub.der
     */
    public PublicKey loadPubKey(byte[] bts) throws Exception {
        EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(bts);
        return keyFactory.generatePublic(publicKeySpec);
    }

    /**
     * 从文件加载RSA公钥
     * openssl rsa -in ca.key -pubout -outform DER -out ca_pub.der
     */
    public PublicKey loadPubKey(String path) throws Exception {
        EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(Files.readAllBytes(Paths.get(path)));
        return keyFactory.generatePublic(publicKeySpec);
    }

    /**
     * 从文件加载RSA公钥
     * openssl rsa -in ca.key -pubout -outform DER -out ca_pub.der
     */
    public PublicKey loadPubKey(InputStream inputStream) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] bts = new byte[1024];
        int len;
        while ((len = inputStream.read(bts)) != -1) {
            outputStream.write(bts, 0, len);
        }
        inputStream.close();
        outputStream.close();
        return loadPubKey(outputStream.toByteArray());
    }

    /**
     * 从文件加载证书
     */
    public X509Certificate loadCert(InputStream inputStream) throws CertificateException, IOException {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(inputStream);
        }finally {
            inputStream.close();
        }
    }

    /**
     * 从文件加载证书
     */
    public X509Certificate loadCert(String path) throws Exception {
        return loadCert(new FileInputStream(path));
    }

    /**
     * 读取ssl证书使用者信息
     */
    public String getSubject(InputStream inputStream) throws Exception {
        X509Certificate certificate = loadCert(inputStream);
        //读出来顺序是反的需要反转下
        List<String> tempList = List.of(certificate.getIssuerX500Principal().toString().split(", "));
        return IntStream.rangeClosed(0, tempList.size() - 1)
                .mapToObj(i -> tempList.get(tempList.size() - i - 1)).collect(Collectors.joining(", "));
    }

    /**
     * 读取ssl证书使用者信息
     */
    public String getSubject(X509Certificate certificate) throws Exception {
        //读出来顺序是反的需要反转下
        List<String> tempList = List.of(certificate.getIssuerX500Principal().toString().split(", "));
        return IntStream.rangeClosed(0, tempList.size() - 1)
                .mapToObj(i -> tempList.get(tempList.size() - i - 1)).collect(Collectors.joining(", "));
    }

    /**
     * 读取证书主题信息
     */
    public Map<String, String> getSubjectMap(X509Certificate certificate) {
        List<String> list = List.of(certificate.getIssuerX500Principal().toString().split(", "));
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        for (String s : list) {
            String[] split = s.split("=", 2);
            map.put(split[0], split[1]);
        }
        return map;
    }

    /**
     * 动态生成服务器证书,并进行CA签授
     *
     * @param issuer 颁发机构
     */
    public X509Certificate genCert(String issuer, PrivateKey caPriKey, Date caNotBefore,
                                          Date caNotAfter, PublicKey serverPubKey,
                                          String... hosts) throws Exception {
        return certGenerator.generateServerCert(issuer, caPriKey, caNotBefore, caNotAfter, serverPubKey, hosts);
    }

    /**
     * 生成CA服务器证书
     */
    public X509Certificate genCACert(String subject, Date caNotBefore, Date caNotAfter,
                                            KeyPair keyPair) throws Exception {
        return certGenerator.generateCaCert(subject, caNotBefore, caNotAfter, keyPair);
    }

    public void generateCACertFile(Path basePath) throws Exception {
        generateCACertFile(basePath, SUBJECT);
    }

    /**
     * 生成根证书和私钥到文件
     * @param basePath 文件路径
     * @param subject 主题信息
     */
    public void generateCACertFile(Path basePath, String subject) throws Exception {
        Date notBeforeDate = dateFormat.parse(START_DATE);

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(notBeforeDate);
        calendar.add(Calendar.YEAR, 30);
        Date notAfterDate = calendar.getTime();

        KeyPair keyPair = genKeyPair();
        assert keyPair != null;

        PrivateKey privateKey = keyPair.getPrivate();

        String privateKeyStr = Base64.getEncoder().encodeToString(privateKey.getEncoded());
        String pKeyFileContent = String.format(PRIVATE_FILE_PATTERN, CommonUtils.wrapText(privateKeyStr));
        FileUtils.write(basePath.resolve("private.key").toFile(), pKeyFileContent, StandardCharsets.UTF_8);

        X509Certificate cert = certGenerator.generateCaCert(subject, notBeforeDate, notAfterDate, keyPair);

        byte[] encoded = cert.getEncoded();
        String certStr = Base64.getEncoder().encodeToString(encoded);
        String certFileContent = String.format(CERT_FILE_PATTERN, CommonUtils.wrapText(certStr));

        FileUtils.write(basePath.resolve("cert.crt").toFile(), certFileContent, StandardCharsets.UTF_8);
    }

    /**
     * 生产根证书和私钥到字符串
     * @return priKey, cert
     */
    public Pair<String, String> generateCaCertPEM(String subject, String notBefore) throws Exception {
        Date notBeforeDate = dateFormat.parse(notBefore);

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(notBeforeDate);
        calendar.add(Calendar.YEAR, 30);
        Date notAfterDate = calendar.getTime();

        KeyPair keyPair = genKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();
        String privateKeyStr = Base64.getEncoder().encodeToString(privateKey.getEncoded());
        String priKeyPEM = String.format(PRIVATE_FILE_PATTERN, CommonUtils.wrapText(privateKeyStr));

        X509Certificate cert = certGenerator.generateCaCert(subject, notBeforeDate, notAfterDate, keyPair);
        String certStr = Base64.getEncoder().encodeToString(cert.getEncoded());
        String certPEM = String.format(CERT_FILE_PATTERN, CommonUtils.wrapText(certStr));

        return Pair.of(priKeyPEM, certPEM);
    }

    public String formatPEM(String str, String pattern) {
        return String.format(pattern, CommonUtils.wrapText(str));
    }
}
