package com.catas.wicked.common.bean;

import com.catas.wicked.common.constant.ClientStatus;
import lombok.Data;

/**
 * request-info of a channel
 */
@Data
public class ProxyRequestInfo {

    /**
     * remote host
     */
    private String host;
    /**
     * remote port
     */
    private int port;
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

    private boolean ssl;

    private String path;

    private boolean isRecording;

    private boolean isThrottling;

    private String requestId;

    private ClientType clientType;

    private ClientStatus clientStatus;

    private ProcessInfo processInfo;

    private volatile long requestStartTime;
    private volatile long requestEndTime;
    private volatile long responseStartTime;
    private volatile long responseEndTime;
    private volatile int requestSize;
    private volatile int respSize;

    private volatile ProxyRequestTiming timing;

    private boolean isNewRequest;

    private boolean usingExternalProxy;

    private boolean isClientConnected;

    private boolean hasSentRequestMsg;
    private boolean hasSentRespMsg;

    public boolean isNewAndReset() {
        boolean res = this.isNewRequest;
        this.isNewRequest = false;
        return res;
    }

    public void resetBasicInfo() {
        requestStartTime = 0;
        requestEndTime = 0;
        responseStartTime = 0;
        responseEndTime = 0;
        requestSize = 0;
        respSize = 0;
        hasSentRequestMsg = false;
        hasSentRespMsg = false;
        isNewRequest = true;
        timing = new ProxyRequestTiming(requestId);
    }

    public ProxyRequestTiming timing() {
        ProxyRequestTiming current = timing;
        if (current == null) {
            synchronized (this) {
                current = timing;
                if (current == null) {
                    current = new ProxyRequestTiming(requestId);
                    timing = current;
                }
            }
        }
        return current;
    }

    public void markRequestStart() {
        ProxyRequestTiming current = timing();
        current.markRequestStart();
        requestStartTime = current.getRequestStartTime();
    }

    public void markRequestEnd(ProxyRequestTiming target) {
        target.markRequestEnd();
        if (timing == target) {
            requestStartTime = target.getRequestStartTime();
            requestEndTime = target.getRequestEndTime();
        }
    }

    public void markResponseStart(ProxyRequestTiming target) {
        target.markResponseStart();
        if (timing == target) {
            responseStartTime = target.getResponseStartTime();
        }
    }

    public void markResponseEnd(ProxyRequestTiming target) {
        target.markResponseEnd();
        if (timing == target) {
            responseStartTime = target.getResponseStartTime();
            responseEndTime = target.getResponseEndTime();
        }
    }

    public synchronized void updateRequestTime() {
        long timestamp = System.currentTimeMillis();
        if (requestStartTime == 0L) {
            requestStartTime = timestamp;
        }
        if (timestamp > requestEndTime) {
            requestEndTime = timestamp;
        }
    }

    public synchronized void updateResponseTime() {
        long timestamp = System.currentTimeMillis();
        if (responseStartTime == 0L) {
            responseStartTime = timestamp;
        }
        if (timestamp > responseEndTime) {
            responseEndTime = timestamp;
        }
    }

    public synchronized void updateRequestSize(long size) {
        if (size > 0) {
            requestSize += size;
        }
    }

    public synchronized void updateRespSize(long size) {
        if (size > 0) {
            respSize += size;
        }
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

    public enum ClientType {
        /**
         * 隧道代理，不解析 http
         */
        TUNNEL,

        /**
         * 普通代理，解析 http
         */
        NORMAL
    }
}
