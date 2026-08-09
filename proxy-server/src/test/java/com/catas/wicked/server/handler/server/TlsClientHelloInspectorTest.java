package com.catas.wicked.server.handler.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;

public class TlsClientHelloInspectorTest {

    private final TlsClientHelloInspector inspector = new TlsClientHelloInspector();

    @Test
    public void acceptsTls12WithoutSupportedVersionsExtension() {
        assertResult(TlsClientHelloInspector.Result.MITM_SUPPORTED,
                tlsRecord(clientHello(0x0303, null)));
    }

    @Test
    public void acceptsTls13UsingSupportedVersionsExtension() {
        assertResult(TlsClientHelloInspector.Result.MITM_SUPPORTED,
                tlsRecord(clientHello(0x0303, new int[]{0x7a7a, 0x0304, 0x0303})));
    }

    @Test
    public void rejectsLegacyOnlyClientHello() {
        assertResult(TlsClientHelloInspector.Result.TUNNEL_UNSUPPORTED,
                tlsRecord(clientHello(0x0301, null)));
        assertResult(TlsClientHelloInspector.Result.TUNNEL_UNSUPPORTED,
                tlsRecord(clientHello(0x0303, new int[]{0x0301, 0x0302})));
    }

    @Test
    public void waitsForFragmentedRecordAndClientHello() {
        byte[] handshake = clientHello(0x0303, new int[]{0x0304});
        byte[] firstRecord = tlsRecord(slice(handshake, 0, 12));
        byte[] secondRecord = tlsRecord(slice(handshake, 12, handshake.length));
        ByteBuf buffer = Unpooled.buffer();
        try {
            buffer.writeBytes(firstRecord, 0, 3);
            Assert.assertEquals(TlsClientHelloInspector.Result.NEED_MORE_DATA, inspector.inspect(buffer));
            buffer.writeBytes(firstRecord, 3, firstRecord.length - 3);
            Assert.assertEquals(TlsClientHelloInspector.Result.NEED_MORE_DATA, inspector.inspect(buffer));
            buffer.writeBytes(secondRecord);
            Assert.assertEquals(TlsClientHelloInspector.Result.MITM_SUPPORTED, inspector.inspect(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    public void distinguishesPlaintextAndMalformedTls() {
        assertResult(TlsClientHelloInspector.Result.TUNNEL_NOT_TLS, "GET / HTTP/1.1\r\n".getBytes());
        assertResult(TlsClientHelloInspector.Result.TUNNEL_MALFORMED,
                new byte[]{22, 3, 3, 0, 4, 2, 0, 0, 0});
    }

    private void assertResult(TlsClientHelloInspector.Result expected, byte[] input) {
        ByteBuf buffer = Unpooled.wrappedBuffer(input);
        try {
            Assert.assertEquals(expected, inspector.inspect(buffer));
        } finally {
            buffer.release();
        }
    }

    private static byte[] clientHello(int legacyVersion, int[] supportedVersions) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeShort(body, legacyVersion);
        body.writeBytes(new byte[32]);
        body.write(0); // session id
        writeShort(body, 2);
        writeShort(body, 0x1301);
        body.write(1);
        body.write(0);

        if (supportedVersions != null) {
            ByteArrayOutputStream extensions = new ByteArrayOutputStream();
            writeShort(extensions, 43);
            writeShort(extensions, 1 + supportedVersions.length * 2);
            extensions.write(supportedVersions.length * 2);
            for (int version : supportedVersions) {
                writeShort(extensions, version);
            }
            writeShort(body, extensions.size());
            body.writeBytes(extensions.toByteArray());
        }

        byte[] bodyBytes = body.toByteArray();
        ByteArrayOutputStream handshake = new ByteArrayOutputStream();
        handshake.write(1);
        writeMedium(handshake, bodyBytes.length);
        handshake.writeBytes(bodyBytes);
        return handshake.toByteArray();
    }

    private static byte[] tlsRecord(byte[] payload) {
        ByteArrayOutputStream record = new ByteArrayOutputStream();
        record.write(22);
        record.write(3);
        record.write(1); // TLS 1.3 also uses a legacy record version here
        writeShort(record, payload.length);
        record.writeBytes(payload);
        return record.toByteArray();
    }

    private static byte[] slice(byte[] source, int start, int end) {
        byte[] result = new byte[end - start];
        System.arraycopy(source, start, result, 0, result.length);
        return result;
    }

    private static void writeShort(ByteArrayOutputStream output, int value) {
        output.write((value >>> 8) & 0xff);
        output.write(value & 0xff);
    }

    private static void writeMedium(ByteArrayOutputStream output, int value) {
        output.write((value >>> 16) & 0xff);
        output.write((value >>> 8) & 0xff);
        output.write(value & 0xff);
    }
}
