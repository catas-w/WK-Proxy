package com.catas.wicked.common.provider;

import com.sun.jna.platform.win32.WinCrypt;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class WindowsRootCertificateStoreUnitTest {

    @Test
    public void opensReadOnlyLocalMachineRootAndCopiesEveryCertificate() throws Exception {
        FakeNativeApi api = new FakeNativeApi(List.of(new byte[]{1, 2}, new byte[]{3, 4}));
        WindowsRootCertificateStore store = new WindowsRootCertificateStore(api);

        List<byte[]> certificates = store.readCertificates();

        assertEquals(WinCrypt.CERT_STORE_PROV_SYSTEM_W, api.provider);
        assertEquals(WindowsRootCertificateStore.ENCODING_TYPE, api.encodingType);
        assertEquals(WinCrypt.CERT_SYSTEM_STORE_LOCAL_MACHINE
                | WinCrypt.CERT_STORE_OPEN_EXISTING_FLAG
                | WinCrypt.CERT_STORE_READONLY_FLAG, api.flags);
        assertEquals("ROOT", api.storeName);
        assertEquals(1, api.closeCount);
        assertArrayEquals(new byte[]{1, 2}, certificates.get(0));
        assertArrayEquals(new byte[]{3, 4}, certificates.get(1));
        assertNotSame(api.encoded.get(0), certificates.get(0));
        assertEquals(Arrays.asList(null, api.contexts.get(0), api.contexts.get(1)), api.previousContexts);
    }

    @Test
    public void openFailureReportsWindowsErrorWithoutClosingInvalidHandle() {
        FakeNativeApi api = new FakeNativeApi(List.of());
        api.openResult = null;
        api.errorCode = 5;

        WindowsRootCertificateStore.CertificateStoreException error = expectFailure(api);

        assertEquals("open", error.operation());
        assertEquals(5, error.errorCode());
        assertEquals(0, api.closeCount);
    }

    @Test
    public void enumerationFailureStillClosesStoreOnce() {
        FakeNativeApi api = new FakeNativeApi(List.of(new byte[]{1}));
        api.enumerationError = 13;

        WindowsRootCertificateStore.CertificateStoreException error = expectFailure(api);

        assertEquals("enumerate", error.operation());
        assertEquals(13, error.errorCode());
        assertEquals(1, api.closeCount);
    }

    @Test
    public void closeFailureTurnsSuccessfulReadIntoFailure() {
        FakeNativeApi api = new FakeNativeApi(List.of());
        api.closeResult = false;
        api.closeError = 6;

        WindowsRootCertificateStore.CertificateStoreException error = expectFailure(api);

        assertEquals("close", error.operation());
        assertEquals(6, error.errorCode());
        assertEquals(1, api.closeCount);
    }

    private static WindowsRootCertificateStore.CertificateStoreException expectFailure(FakeNativeApi api) {
        try {
            new WindowsRootCertificateStore(api).readCertificates();
            fail("Expected certificate store failure");
            return null;
        } catch (WindowsRootCertificateStore.CertificateStoreException error) {
            return error;
        }
    }

    private static final class FakeNativeApi implements WindowsRootCertificateStore.NativeApi {
        private final Object store = new Object();
        private final List<byte[]> encoded;
        private final List<Object> contexts = new ArrayList<>();
        private final List<Object> previousContexts = new ArrayList<>();
        private Object openResult = store;
        private boolean closeResult = true;
        private int errorCode;
        private int enumerationError;
        private int closeError;
        private int index;
        private int provider;
        private int encodingType;
        private int flags;
        private String storeName;
        private int closeCount;

        private FakeNativeApi(List<byte[]> encoded) {
            this.encoded = new ArrayList<>(encoded);
            for (int i = 0; i < encoded.size(); i++) {
                contexts.add(new Object());
            }
        }

        @Override
        public Object openStore(int provider, int encodingType, int flags, String storeName) {
            this.provider = provider;
            this.encodingType = encodingType;
            this.flags = flags;
            this.storeName = storeName;
            return openResult;
        }

        @Override
        public WindowsRootCertificateStore.NativeCertificate nextCertificate(Object store,
                                                                               Object previousContext) {
            assertTrue(this.store == store);
            previousContexts.add(previousContext);
            if (index < encoded.size()) {
                return new WindowsRootCertificateStore.NativeCertificate(
                        contexts.get(index), encoded.get(index++));
            }
            errorCode = enumerationError == 0 ? com.sun.jna.platform.win32.WinError.CRYPT_E_NOT_FOUND
                    : enumerationError;
            return null;
        }

        @Override
        public boolean closeStore(Object store) {
            assertTrue(this.store == store);
            closeCount++;
            if (!closeResult) {
                errorCode = closeError;
            }
            return closeResult;
        }

        @Override
        public int lastError() {
            return errorCode;
        }
    }
}
