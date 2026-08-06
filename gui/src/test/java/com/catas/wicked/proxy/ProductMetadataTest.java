package com.catas.wicked.proxy;

import org.junit.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProductMetadataTest {

    private static final String VERSION = "2.0.1";

    @Test
    public void productNameIsConsistentAcrossRuntimeAndPackagingMetadata() throws IOException {
        String guiPom = Files.readString(Path.of("pom.xml"));
        Properties english = loadProperties("src/main/resources/lang/messages_en.properties");
        Properties chinese = loadProperties("src/main/resources/lang/messages_zh_CN.properties");

        assertEquals("Wizard Proxy", ProductInfo.DISPLAY_NAME);
        assertEquals(ProductInfo.DISPLAY_NAME, english.getProperty("app-name.label"));
        assertEquals(ProductInfo.DISPLAY_NAME, chinese.getProperty("app-name.label"));
        assertTrue(guiPom.contains("<name>Wizard Proxy</name>"));
        assertTrue(guiPom.contains("<bundleName>Wizard Proxy</bundleName>"));
        assertTrue(guiPom.contains("<description>Wizard Proxy HTTP/HTTPS Debugging Proxy</description>"));
    }

    @Test
    public void productVersionIsConsistentAcrossRuntimeAndPackagingMetadata() throws IOException {
        String rootPom = Files.readString(Path.of("..", "pom.xml"));
        Properties application = loadProperties("src/main/resources/application.properties");

        assertEquals(VERSION, application.getProperty("version"));
        assertTrue(rootPom.contains("<wk.version>" + VERSION + "</wk.version>"));
        assertEquals("Wizard Proxy 2.0.1", ProductInfo.versionLabel(VERSION));
    }

    @Test
    public void visibleFxmlUsesLocalizedBrandName() throws IOException {
        String application = Files.readString(Path.of("src/main/resources/fxml/application.fxml"));
        String about = Files.readString(Path.of("src/main/resources/fxml/setting-page/about.fxml"));

        assertTrue(application.contains("text=\"%app-name.label\""));
        assertTrue(about.contains("text=\"%app-name.label\""));
        assertFalse(application.contains("text=\"WK Proxy\""));
        assertFalse(about.contains("text=\"WK Proxy\""));
    }

    private static Properties loadProperties(String path) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of(path), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }
}
