package com.catas.wicked.proxy.message;

import com.catas.wicked.common.bean.RequestCell;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.bean.message.ResponseMessage;
import com.catas.wicked.common.constant.ClientStatus;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class RequestTransferStatusTest {

    @Test
    public void remainsPendingUntilAResponseArrives() {
        RequestMessage request = request();
        request.updateClientStatus(ClientStatus.Status.FINISHED);

        RequestTransferStatus status = RequestTransferStatus.from(request);

        assertEquals(RequestCell.TransferState.PENDING, status.state());
        assertEquals("request-status.waiting-response", status.messageKey());
    }

    @Test
    public void treatsAllNormalHttpResponsesAsTransferSuccess() {
        for (int code : List.of(200, 304, 404, 500)) {
            RequestMessage request = request();
            ResponseMessage response = new ResponseMessage();
            response.setStatus(code);
            request.setResponse(response);

            assertEquals(RequestCell.TransferState.SUCCESS,
                    RequestTransferStatus.from(request).state());
        }
    }

    @Test
    public void treatsEveryTerminalClientStatusAsFailure() {
        for (ClientStatus.Status clientStatus : ClientStatus.Status.values()) {
            if (clientStatus == ClientStatus.Status.WAITING
                    || clientStatus == ClientStatus.Status.FINISHED) {
                continue;
            }
            RequestMessage request = request();
            request.updateClientStatus(clientStatus, "detail");

            RequestTransferStatus status = RequestTransferStatus.from(request);

            assertEquals(clientStatus.name(), RequestCell.TransferState.FAILED, status.state());
            assertEquals("detail", status.detail());
        }
    }

    @Test
    public void treatsInternalErrorResponseAsFailure() {
        RequestMessage request = request();
        ResponseMessage response = new ResponseMessage();
        response.setStatus(-1);
        response.setReasonPhrase("Proxy failed");
        request.setResponse(response);

        RequestTransferStatus status = RequestTransferStatus.from(request);

        assertEquals(RequestCell.TransferState.FAILED, status.state());
        assertEquals("request-status.error.unknown", status.messageKey());
        assertEquals("Proxy failed", status.detail());
    }

    private static RequestMessage request() {
        RequestMessage request = new RequestMessage("https://example.test/path");
        request.setRequestId("request");
        request.setMethod("GET");
        return request;
    }
}
