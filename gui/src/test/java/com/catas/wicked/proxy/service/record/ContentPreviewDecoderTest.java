package com.catas.wicked.proxy.service.record;

import com.catas.wicked.common.util.GzipUtils;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ContentPreviewDecoderTest {

    @Test
    public void limitsPlainContentWithoutCopyingTheWholePayload() {
        byte[] source = "0123456789".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals("0123".getBytes(StandardCharsets.UTF_8),
                ContentPreviewDecoder.decode(Map.of(), source, 4));
    }

    @Test
    public void boundsDecompressedContent() throws Exception {
        byte[] compressed = GzipUtils.compress("abcdefghijklmnopqrstuvwxyz");
        byte[] decoded = ContentPreviewDecoder.decode(
                Map.of("Content-Encoding", "gzip"), compressed, 8);
        assertEquals("abcdefgh", new String(decoded, StandardCharsets.UTF_8));
    }

    @Test
    public void limitsDecodedTextToFiftyThousandCharacters() {
        byte[] content = "a".repeat(60_000).getBytes(StandardCharsets.UTF_8);
        assertEquals(50_000,
                ContentPreviewDecoder.decodeText(Map.of(), content, StandardCharsets.UTF_8).length());
    }
}
