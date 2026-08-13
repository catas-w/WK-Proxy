package com.catas.wicked.proxy.service.record;

import org.apache.commons.compress.compressors.brotli.BrotliCompressorInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/** Bounded content decoding for request detail previews. */
public final class ContentPreviewDecoder {

    public static final int DEFAULT_MAX_PREVIEW_BYTES = 256 * 1024;
    public static final int DEFAULT_MAX_PREVIEW_CHARS = 50_000;

    private ContentPreviewDecoder() {
    }

    public static byte[] decode(Map<String, String> headers, byte[] content) {
        return decode(headers, content, DEFAULT_MAX_PREVIEW_BYTES);
    }

    public static String decodeText(Map<String, String> headers, byte[] content, Charset charset) {
        return toPreviewText(decode(headers, content), charset);
    }

    public static String toPreviewText(byte[] decodedContent, Charset charset) {
        String text = new String(decodedContent, charset);
        return text.length() <= DEFAULT_MAX_PREVIEW_CHARS
                ? text : text.substring(0, DEFAULT_MAX_PREVIEW_CHARS);
    }

    static byte[] decode(Map<String, String> headers, byte[] content, int maxBytes) {
        if (content == null || content.length == 0 || maxBytes <= 0) {
            return new byte[0];
        }
        try (InputStream input = decodingStream(headers, content);
             ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(content.length, maxBytes))) {
            byte[] buffer = new byte[8192];
            int remaining = maxBytes;
            while (remaining > 0) {
                int read = input.read(buffer, 0, Math.min(buffer.length, remaining));
                if (read < 0) {
                    break;
                }
                output.write(buffer, 0, read);
                remaining -= read;
            }
            return output.toByteArray();
        } catch (IOException | RuntimeException ignored) {
            int length = Math.min(content.length, maxBytes);
            byte[] fallback = new byte[length];
            System.arraycopy(content, 0, fallback, 0, length);
            return fallback;
        }
    }

    private static InputStream decodingStream(Map<String, String> headers, byte[] content) throws IOException {
        InputStream input = new ByteArrayInputStream(content);
        String encoding = contentEncoding(headers);
        return switch (encoding) {
            case "gzip" -> new GZIPInputStream(input);
            case "deflate" -> new InflaterInputStream(input);
            case "br" -> new BrotliCompressorInputStream(input);
            default -> input;
        };
    }

    private static String contentEncoding(Map<String, String> headers) {
        if (headers == null) {
            return "";
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if ("content-encoding".equalsIgnoreCase(entry.getKey())) {
                return entry.getValue() == null ? "" : entry.getValue().trim().toLowerCase(Locale.ROOT);
            }
        }
        return "";
    }
}
