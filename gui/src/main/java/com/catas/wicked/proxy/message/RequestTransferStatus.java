package com.catas.wicked.proxy.message;

import com.catas.wicked.common.bean.RequestCell;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.bean.message.ResponseMessage;
import com.catas.wicked.common.constant.ClientStatus;
import org.apache.commons.lang3.StringUtils;

record RequestTransferStatus(RequestCell.TransferState state, String messageKey, String detail) {

    static RequestTransferStatus from(RequestMessage request) {
        if (request == null) {
            return pending("request-status.pending");
        }

        ResponseMessage response = request.getResponse();
        ClientStatus clientStatus = request.getClientStatus();
        ClientStatus.Status status = clientStatus == null ? null : clientStatus.getStatus();
        if (isFailure(status) || (response != null && Integer.valueOf(-1).equals(response.getStatus()))) {
            String key = status == null || !isFailure(status)
                    ? "request-status.error.unknown" : failureKey(status);
            String detail = clientStatus == null ? null : clientStatus.getMsg();
            if (StringUtils.isBlank(detail) && status == null && response != null) {
                detail = response.getReasonPhrase();
            }
            return new RequestTransferStatus(RequestCell.TransferState.FAILED, key, detail);
        }
        if (response != null) {
            return new RequestTransferStatus(
                    RequestCell.TransferState.SUCCESS, "request-status.success", null);
        }
        if (status == ClientStatus.Status.WAITING) {
            return pending("request-status.connecting");
        }
        if (status == ClientStatus.Status.FINISHED) {
            return pending("request-status.waiting-response");
        }
        return pending("request-status.pending");
    }

    private static RequestTransferStatus pending(String key) {
        return new RequestTransferStatus(RequestCell.TransferState.PENDING, key, null);
    }

    private static boolean isFailure(ClientStatus.Status status) {
        return status != null && status != ClientStatus.Status.WAITING
                && status != ClientStatus.Status.FINISHED;
    }

    private static String failureKey(ClientStatus.Status status) {
        return switch (status) {
            case CONNECT_ERR -> "request-status.error.connect";
            case REJECTED -> "request-status.error.rejected";
            case TIMEOUT -> "request-status.error.timeout";
            case ADDR_NOTFOUND -> "request-status.error.address";
            case CLOSED -> "request-status.error.closed";
            case SSL_HANDSHAKE_ERR -> "request-status.error.ssl";
            case UNKNOWN_ERR -> "request-status.error.unknown";
            default -> "request-status.error.unknown";
        };
    }

    void applyTo(RequestCell cell) {
        if (cell != null) {
            cell.applyTransferStatus(state, messageKey, detail);
        }
    }
}
