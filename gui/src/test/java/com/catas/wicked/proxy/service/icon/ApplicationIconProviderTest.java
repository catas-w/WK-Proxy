package com.catas.wicked.proxy.service.icon;

import com.catas.wicked.common.bean.ProcessInfo;
import com.catas.wicked.common.constant.ProductIdentity;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javax.imageio.ImageIO;

import static org.junit.Assume.assumeTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ApplicationIconProviderTest {

    @Test
    public void nativeMacProviderLoadsInstalledApplicationIcon() throws Exception {
        Path safari = Path.of("/Applications/Safari.app/Contents/MacOS/Safari");
        assumeTrue(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac"));
        assumeTrue(Files.exists(safari));
        ProcessInfo info = ProcessInfo.builder()
                .applicationName("Safari")
                .applicationExecutablePath(safari.toString())
                .lookupStatus(ProcessInfo.LookupStatus.FOUND)
                .build();

        ApplicationIconData data = new MacApplicationIconProvider().load(info).orElseThrow();
        assertTrue(data instanceof ApplicationIconData.Png);
        byte[] bytes = ((ApplicationIconData.Png) data).bytes();
        assertTrue(bytes.length > 0);
        var source = ImageIO.read(new ByteArrayInputStream(bytes));
        assertTrue(source.getWidth() >= ApplicationIconData.PNG_RASTER_SIZE);
        assertTrue(source.getHeight() >= ApplicationIconData.PNG_RASTER_SIZE);
    }

    @Test
    public void macBundlePathIsExtractedFromHelperExecutable() {
        assertEquals("/Applications/Browser.app", MacApplicationIconProvider.appBundlePath(
                "/Applications/Browser.app/Contents/Frameworks/Browser Helper.app/Contents/MacOS/Browser Helper"));
        assertEquals("/Applications/Browser.app", MacApplicationIconProvider.appBundlePath(
                "/Applications/Browser.app"));
        assertNull(MacApplicationIconProvider.appBundlePath("/usr/bin/curl"));
    }

    @Test
    public void bundledProviderMatchesKnownApplication() {
        ProcessInfo info = ProcessInfo.builder()
                .applicationName("Google Chrome")
                .applicationExecutablePath("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")
                .lookupStatus(ProcessInfo.LookupStatus.FOUND)
                .build();
        assertTrue(new BundledApplicationIconProvider().load(info).isPresent());
    }

    @Test
    public void bundledPngIconsSupportRetinaDisplay() throws Exception {
        ProcessInfo info = ProcessInfo.builder()
                .applicationName("Google Chrome")
                .applicationExecutablePath("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")
                .lookupStatus(ProcessInfo.LookupStatus.FOUND)
                .build();

        ApplicationIconData data = new BundledApplicationIconProvider().load(info).orElseThrow();
        byte[] bytes = ((ApplicationIconData.Png) data).bytes();
        var source = ImageIO.read(new ByteArrayInputStream(bytes));

        assertEquals(64, ApplicationIconData.PNG_RASTER_SIZE);
        assertTrue(source.getWidth() >= ApplicationIconData.PNG_RASTER_SIZE);
        assertTrue(source.getHeight() >= ApplicationIconData.PNG_RASTER_SIZE);
    }

    @Test
    public void bundledProviderUsesProductLogoForWizardProxy() {
        assertTrue(new BundledApplicationIconProvider()
                .load(ProductIdentity.currentProcess()).isPresent());
    }

    @Test
    public void missingWindowsAlphaIsMadeOpaque() {
        byte[] bgra = {1, 2, 3, 0, 4, 5, 6, 0};
        WindowsApplicationIconProvider.repairMissingAlpha(bgra);
        assertEquals((byte) 0xff, bgra[3]);
        assertEquals((byte) 0xff, bgra[7]);
    }

    @Test
    public void existingWindowsAlphaIsPreserved() {
        byte[] bgra = {1, 2, 3, 0, 4, 5, 6, 100};
        WindowsApplicationIconProvider.repairMissingAlpha(bgra);
        assertEquals(0, bgra[3]);
        assertEquals(100, bgra[7]);
    }
}
