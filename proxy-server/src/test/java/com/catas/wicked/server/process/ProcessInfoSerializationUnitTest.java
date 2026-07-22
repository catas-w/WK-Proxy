package com.catas.wicked.server.process;

import com.catas.wicked.common.bean.ProcessInfo;
import com.catas.wicked.common.bean.message.RequestMessage;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class ProcessInfoSerializationUnitTest {

    @Test
    public void requestMessageRetainsProcessInfoAfterSerialization() throws Exception {
        RequestMessage request = new RequestMessage("http://example.test/");
        request.setRequestId("request-1");
        request.setProcessInfo(ProcessInfo.builder()
                .ownerPid(123)
                .ownerProcessName("curl")
                .ownerExecutablePath("/usr/bin/curl")
                .applicationPid(123)
                .applicationName("curl")
                .applicationExecutablePath("/usr/bin/curl")
                .lookupStatus(ProcessInfo.LookupStatus.FOUND)
                .build());

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(request);
        }
        RequestMessage restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (RequestMessage) input.readObject();
        }

        Assert.assertEquals(request.getProcessInfo(), restored.getProcessInfo());
        Assert.assertEquals("request-1", restored.getRequestId());
    }
}
