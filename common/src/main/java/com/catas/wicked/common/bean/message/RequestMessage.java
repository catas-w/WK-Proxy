package com.catas.wicked.common.bean.message;

import com.catas.wicked.common.constant.ClientStatus;
import com.catas.wicked.common.bean.ProcessInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class RequestMessage extends BaseMessage{

    private String method;

    private String contentType;

    private String requestId;

    private String requestUrl;

    private URL url;

    /**
     * remote host
     */
    private String remoteHost;
    /**
     * remote port
     */
    private int remotePort;
    /**
     * remote ip
     */
    private String remoteAddress;
    /**
     * local ip
     */
    private String localAddress;
    /**
     * local port
     */
    private int localPort;

    private String protocol;

    private byte[] body;

    private boolean isEnd;

    private Map<String, String> headers;

    private ResponseMessage response;

    private boolean isEncrypted;

    private boolean isOversize;

    private ClientStatus clientStatus;

    private ProcessInfo processInfo;

    public RequestMessage() {}

    public RequestMessage(String requestUrl) {
        try {
            this.url = new URL(requestUrl);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        this.requestUrl = requestUrl;
        this.setType(MessageType.REQUEST);
    }

    public void updateClientStatus(ClientStatus.Status status) {
        updateClientStatus(status, null);
    }

    public void updateClientStatus(ClientStatus.Status status, String msg) {
        if (clientStatus == null) {
            clientStatus = new ClientStatus();
        }
        clientStatus.setStatus(status);
        if (msg != null) {
            clientStatus.setMsg(msg);
        }
    }
}
