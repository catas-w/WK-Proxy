package com.catas.wicked.proxy;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.assertTrue;

public class NativeResourceOptimizationTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");

    @Test
    public void customFontsRemainPackagedForConsistentRendering() throws IOException {
        assertTrue(Files.size(RESOURCES.resolve("font/MiSans-Normal.ttf")) > 0);
        assertTrue(Files.size(RESOURCES.resolve("font/MiSans-Demibold.ttf")) > 0);

        String appStylesheet = Files.readString(RESOURCES.resolve("css/app.css"));
        String commonResources = Files.readString(RESOURCES.resolve("META-INF/native-image/resource-config.json"));
        assertTrue(appStylesheet.contains("MiSans Normal"));
        assertTrue(appStylesheet.contains("MiSans Demibold"));
        assertTrue(commonResources.contains("MiSans-Normal.ttf"));
        assertTrue(commonResources.contains("MiSans-Demibold.ttf"));
    }

    @Test
    public void nativeDiagnosticsProfileReportsResourcesAndImageHeap() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));

        assertTrue(pom.contains("<id>native-diagnostics</id>"));
        assertTrue(pom.contains("-H:Log=registerResource:3"));
        assertTrue(pom.contains("-H:+PrintImageHeapPartitionSizes"));
    }

    @Test
    public void windowsNativeConfigIncludesTcpOwnerLookupMetadata() throws IOException {
        String reflection = Files.readString(RESOURCES.resolve("graal/win/reflect-config.json"));
        String proxies = Files.readString(RESOURCES.resolve("graal/win/proxy-config.json"));

        assertTrue(proxies.contains("com.sun.jna.platform.win32.IPHlpAPI"));
        assertTrue(reflection.contains("IPHlpAPI$MIB_TCPROW_OWNER_PID"));
        assertTrue(reflection.contains("IPHlpAPI$MIB_TCPTABLE_OWNER_PID"));
        assertTrue(reflection.contains("IPHlpAPI$MIB_TCP6ROW_OWNER_PID"));
        assertTrue(reflection.contains("IPHlpAPI$MIB_TCP6TABLE_OWNER_PID"));
    }

    @Test
    public void windowsNativeConfigIncludesCryptoApiCertificateStoreMetadata() throws IOException {
        String commonReflection = Files.readString(
                RESOURCES.resolve("META-INF/native-image/reflect-config.json"));
        String commonProxies = Files.readString(
                RESOURCES.resolve("META-INF/native-image/proxy-config.json"));
        String commonJni = Files.readString(
                RESOURCES.resolve("META-INF/native-image/jni-config.json"));
        String windowsReflection = Files.readString(RESOURCES.resolve("graal/win/reflect-config.json"));
        String windowsProxies = Files.readString(RESOURCES.resolve("graal/win/proxy-config.json"));
        String windowsJni = Files.readString(RESOURCES.resolve("graal/win/jni-config.json"));

        for (String proxies : new String[]{commonProxies, windowsProxies}) {
            assertTrue(proxies.contains("com.sun.jna.platform.win32.Crypt32"));
        }
        for (String reflection : new String[]{commonReflection, windowsReflection}) {
            assertTrue(reflection.contains("WinCrypt$CERT_CONTEXT"));
            assertTrue(reflection.contains("WinCrypt$HCERTSTORE"));
            assertTrue(reflection.contains("WinCrypt$HCRYPTPROV_LEGACY"));
            assertTrue(reflection.contains("WinCrypt$CertStoreProviderName"));
        }
        assertTrue(!commonJni.contains("sun.security.mscapi.CKeyStore"));
        assertTrue(!windowsJni.contains("sun.security.mscapi.CKeyStore"));
    }

}
