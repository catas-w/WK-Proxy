package com.catas.wicked.common.provider;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Crypt32;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WTypes;
import com.sun.jna.platform.win32.WinCrypt;
import com.sun.jna.platform.win32.WinError;

import java.util.ArrayList;
import java.util.List;

/** Reads the machine-wide Windows trusted root store without relying on SunMSCAPI. */
final class WindowsRootCertificateStore {

    static final int STORE_PROVIDER = WinCrypt.CERT_STORE_PROV_SYSTEM_W;
    static final int ENCODING_TYPE = WinCrypt.X509_ASN_ENCODING | WinCrypt.PKCS_7_ASN_ENCODING;
    static final int OPEN_FLAGS = WinCrypt.CERT_SYSTEM_STORE_LOCAL_MACHINE
            | WinCrypt.CERT_STORE_OPEN_EXISTING_FLAG
            | WinCrypt.CERT_STORE_READONLY_FLAG;
    static final String STORE_NAME = "ROOT";

    private final NativeApi nativeApi;

    WindowsRootCertificateStore() {
        this(new JnaNativeApi());
    }

    WindowsRootCertificateStore(NativeApi nativeApi) {
        this.nativeApi = nativeApi;
    }

    List<byte[]> readCertificates() throws CertificateStoreException {
        Object store = nativeApi.openStore(STORE_PROVIDER, ENCODING_TYPE, OPEN_FLAGS, STORE_NAME);
        if (store == null) {
            throw error("open", nativeApi.lastError());
        }

        List<byte[]> certificates = new ArrayList<>();
        Object previousContext = null;
        CertificateStoreException failure = null;
        try {
            while (true) {
                NativeCertificate certificate = nativeApi.nextCertificate(store, previousContext);
                if (certificate == null) {
                    int errorCode = nativeApi.lastError();
                    if (errorCode != WinError.ERROR_SUCCESS && errorCode != WinError.CRYPT_E_NOT_FOUND) {
                        throw error("enumerate", errorCode);
                    }
                    break;
                }
                // CryptoAPI frees the previous context on the next enumeration call.
                certificates.add(certificate.encoded().clone());
                previousContext = certificate.context();
            }
            return certificates;
        } catch (CertificateStoreException error) {
            failure = error;
            throw error;
        } finally {
            if (!nativeApi.closeStore(store)) {
                CertificateStoreException closeError = error("close", nativeApi.lastError());
                if (failure != null) {
                    failure.addSuppressed(closeError);
                } else {
                    throw closeError;
                }
            }
        }
    }

    private static CertificateStoreException error(String operation, int errorCode) {
        return new CertificateStoreException(operation, errorCode);
    }

    interface NativeApi {
        Object openStore(int provider, int encodingType, int flags, String storeName);

        NativeCertificate nextCertificate(Object store, Object previousContext);

        boolean closeStore(Object store);

        int lastError();
    }

    record NativeCertificate(Object context, byte[] encoded) {
    }

    static final class CertificateStoreException extends Exception {
        private final String operation;
        private final int errorCode;

        CertificateStoreException(String operation, int errorCode) {
            super("Windows certificate store " + operation + " failed, error=" + errorCode);
            this.operation = operation;
            this.errorCode = errorCode;
        }

        String operation() {
            return operation;
        }

        int errorCode() {
            return errorCode;
        }
    }

    private static final class JnaNativeApi implements NativeApi {
        @Override
        public Object openStore(int provider, int encodingType, int flags, String storeName) {
            return Crypt32.INSTANCE.CertOpenStore(
                    new WinCrypt.CertStoreProviderName(provider),
                    encodingType,
                    new WinCrypt.HCRYPTPROV_LEGACY(0),
                    flags,
                    new WTypes.LPWSTR(storeName));
        }

        @Override
        public NativeCertificate nextCertificate(Object store, Object previousContext) {
            Pointer previous = previousContext == null
                    ? null
                    : ((WinCrypt.CERT_CONTEXT.ByReference) previousContext).getPointer();
            WinCrypt.CERT_CONTEXT.ByReference context = Crypt32.INSTANCE.CertEnumCertificatesInStore(
                    (WinCrypt.HCERTSTORE) store, previous);
            if (context == null) {
                return null;
            }
            byte[] encoded = context.pbCertEncoded.getByteArray(0, context.cbCertEncoded);
            return new NativeCertificate(context, encoded);
        }

        @Override
        public boolean closeStore(Object store) {
            return Crypt32.INSTANCE.CertCloseStore((WinCrypt.HCERTSTORE) store, 0);
        }

        @Override
        public int lastError() {
            return Kernel32.INSTANCE.GetLastError();
        }
    }
}
