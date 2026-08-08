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

}
