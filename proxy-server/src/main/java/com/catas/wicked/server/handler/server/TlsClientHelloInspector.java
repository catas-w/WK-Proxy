package com.catas.wicked.server.handler.server;

import io.netty.buffer.ByteBuf;

import java.io.ByteArrayOutputStream;

/**
 * Inspects a buffered TLS ClientHello before the proxy installs its MITM SSL handler.
 */
final class TlsClientHelloInspector {

    static final int MAX_PROBE_BYTES = 64 * 1024;

    private static final int TLS_HEADER_SIZE = 5;
    private static final int HANDSHAKE_HEADER_SIZE = 4;
    private static final int HANDSHAKE_CONTENT_TYPE = 22;
    private static final int CLIENT_HELLO_TYPE = 1;
    private static final int SUPPORTED_VERSIONS_EXTENSION = 43;
    private static final int TLS_1_2 = 0x0303;
    private static final int TLS_1_3 = 0x0304;

    enum Result {
        NEED_MORE_DATA,
        MITM_SUPPORTED,
        TUNNEL_UNSUPPORTED,
        TUNNEL_NOT_TLS,
        TUNNEL_MALFORMED
    }

    Result inspect(ByteBuf input) {
        int readable = input.readableBytes();
        if (readable == 0) {
            return Result.NEED_MORE_DATA;
        }
        if (readable > MAX_PROBE_BYTES) {
            return Result.TUNNEL_MALFORMED;
        }

        int start = input.readerIndex();
        if (input.getUnsignedByte(start) != HANDSHAKE_CONTENT_TYPE) {
            return Result.TUNNEL_NOT_TLS;
        }

        ByteArrayOutputStream handshake = new ByteArrayOutputStream(Math.min(readable, 4096));
        int offset = start;
        int end = start + readable;
        while (offset < end) {
            if (end - offset < TLS_HEADER_SIZE) {
                return Result.NEED_MORE_DATA;
            }
            if (input.getUnsignedByte(offset) != HANDSHAKE_CONTENT_TYPE) {
                return Result.TUNNEL_MALFORMED;
            }
            if (input.getUnsignedByte(offset + 1) != 3) {
                return Result.TUNNEL_UNSUPPORTED;
            }

            int recordLength = input.getUnsignedShort(offset + 3);
            if (recordLength <= 0 || recordLength > MAX_PROBE_BYTES) {
                return Result.TUNNEL_MALFORMED;
            }
            if (end - offset - TLS_HEADER_SIZE < recordLength) {
                return Result.NEED_MORE_DATA;
            }

            int payloadOffset = offset + TLS_HEADER_SIZE;
            byte[] payload = new byte[recordLength];
            input.getBytes(payloadOffset, payload);
            handshake.write(payload, 0, payload.length);

            byte[] bytes = handshake.toByteArray();
            if (bytes.length >= HANDSHAKE_HEADER_SIZE) {
                if ((bytes[0] & 0xff) != CLIENT_HELLO_TYPE) {
                    return Result.TUNNEL_MALFORMED;
                }
                int helloLength = unsignedMedium(bytes, 1);
                if (helloLength <= 0 || helloLength + HANDSHAKE_HEADER_SIZE > MAX_PROBE_BYTES) {
                    return Result.TUNNEL_MALFORMED;
                }
                if (bytes.length >= helloLength + HANDSHAKE_HEADER_SIZE) {
                    return inspectClientHello(bytes, HANDSHAKE_HEADER_SIZE, helloLength);
                }
            }
            offset = payloadOffset + recordLength;
        }
        return Result.NEED_MORE_DATA;
    }

    private Result inspectClientHello(byte[] data, int offset, int length) {
        int end = offset + length;
        Cursor cursor = new Cursor(data, offset, end);
        try {
            int legacyVersion = cursor.readUnsignedShort();
            cursor.skip(32); // random
            cursor.skipVector8(); // legacy session id

            int cipherLength = cursor.readUnsignedShort();
            if (cipherLength == 0 || (cipherLength & 1) != 0) {
                return Result.TUNNEL_MALFORMED;
            }
            cursor.skip(cipherLength);
            cursor.skipVector8(); // legacy compression methods

            if (!cursor.hasRemaining()) {
                return supportsMitm(legacyVersion)
                        ? Result.MITM_SUPPORTED : Result.TUNNEL_UNSUPPORTED;
            }

            int extensionsLength = cursor.readUnsignedShort();
            int extensionsEnd = cursor.position() + extensionsLength;
            if (extensionsEnd != end) {
                return Result.TUNNEL_MALFORMED;
            }

            boolean foundSupportedVersions = false;
            boolean supported = false;
            while (cursor.position() < extensionsEnd) {
                int type = cursor.readUnsignedShort();
                int extensionLength = cursor.readUnsignedShort();
                int extensionEnd = cursor.position() + extensionLength;
                if (extensionEnd > extensionsEnd) {
                    return Result.TUNNEL_MALFORMED;
                }
                if (type == SUPPORTED_VERSIONS_EXTENSION) {
                    foundSupportedVersions = true;
                    int versionsLength = cursor.readUnsignedByte();
                    if (versionsLength == 0 || (versionsLength & 1) != 0
                            || cursor.position() + versionsLength != extensionEnd) {
                        return Result.TUNNEL_MALFORMED;
                    }
                    while (cursor.position() < extensionEnd) {
                        supported |= supportsMitm(cursor.readUnsignedShort());
                    }
                } else {
                    cursor.skip(extensionLength);
                }
                if (cursor.position() != extensionEnd) {
                    return Result.TUNNEL_MALFORMED;
                }
            }
            if (foundSupportedVersions) {
                return supported ? Result.MITM_SUPPORTED : Result.TUNNEL_UNSUPPORTED;
            }
            return supportsMitm(legacyVersion)
                    ? Result.MITM_SUPPORTED : Result.TUNNEL_UNSUPPORTED;
        } catch (IndexOutOfBoundsException ex) {
            return Result.TUNNEL_MALFORMED;
        }
    }

    private static boolean supportsMitm(int version) {
        return version == TLS_1_2 || version == TLS_1_3;
    }

    private static int unsignedMedium(byte[] data, int offset) {
        return ((data[offset] & 0xff) << 16)
                | ((data[offset + 1] & 0xff) << 8)
                | (data[offset + 2] & 0xff);
    }

    private static final class Cursor {
        private final byte[] data;
        private final int end;
        private int position;

        private Cursor(byte[] data, int position, int end) {
            this.data = data;
            this.position = position;
            this.end = end;
        }

        private int position() {
            return position;
        }

        private boolean hasRemaining() {
            return position < end;
        }

        private int readUnsignedByte() {
            require(1);
            return data[position++] & 0xff;
        }

        private int readUnsignedShort() {
            require(2);
            int value = ((data[position] & 0xff) << 8) | (data[position + 1] & 0xff);
            position += 2;
            return value;
        }

        private void skipVector8() {
            skip(readUnsignedByte());
        }

        private void skip(int length) {
            require(length);
            position += length;
        }

        private void require(int length) {
            if (length < 0 || position + length > end) {
                throw new IndexOutOfBoundsException("Malformed ClientHello");
            }
        }
    }
}
