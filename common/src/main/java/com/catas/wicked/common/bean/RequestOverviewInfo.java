package com.catas.wicked.common.bean;

import com.catas.wicked.common.provider.ResourceMessageProvider;
import jakarta.inject.Singleton;
import lombok.Getter;

@Getter
@Singleton
public class RequestOverviewInfo {

    private final ResourceMessageProvider resourceMessageProvider;

    private final PairEntry url = new PairEntry("URL");
    private final PairEntry method = new PairEntry("Method");
    private final PairEntry status = new PairEntry("Status");
    private final PairEntry protocol = new PairEntry("Protocol");
    private final PairEntry remoteHost = new PairEntry("Remote Host");
    private final PairEntry remoteAddr = new PairEntry("Remote Address");
    private final PairEntry remotePort = new PairEntry("Remote Port");
    private final PairEntry clientHost = new PairEntry("Client Host");
    private final PairEntry clientPort = new PairEntry("Client Port");

    private final PairEntry timeCost = new PairEntry("Time cost");
    private final PairEntry requestTime = new PairEntry("Request time");
    private final PairEntry requestStart = new PairEntry("Request start");
    private final PairEntry requestEnd = new PairEntry("Request end");
    private final PairEntry respTime = new PairEntry("Response time");
    private final PairEntry respStart = new PairEntry("Response start");
    private final PairEntry respEnd = new PairEntry("Response end");

    private final PairEntry requestSize = new PairEntry("Request size");
    private final PairEntry responseSize = new PairEntry("Response size");
    private final PairEntry averageSpeed = new PairEntry("Average speed");

    public RequestOverviewInfo(ResourceMessageProvider resourceMessageProvider) {
        this.resourceMessageProvider = resourceMessageProvider;
        refreshKey();
    }

    private void refreshKey() {
        method.setKey(resourceMessageProvider.getMessage("method.label"));
        status.setKey(resourceMessageProvider.getMessage("status.label"));
        protocol.setKey(resourceMessageProvider.getMessage("protocol.label"));
        remoteHost.setKey(resourceMessageProvider.getMessage("remote-host.label"));
        remoteAddr.setKey(resourceMessageProvider.getMessage("remote-addr.label"));
        remotePort.setKey(resourceMessageProvider.getMessage("remote-port.label"));
        clientHost.setKey(resourceMessageProvider.getMessage("client-host.label"));
        clientPort.setKey(resourceMessageProvider.getMessage("client-port.label"));

        timeCost.setKey(resourceMessageProvider.getMessage("time-cost.label"));
        requestTime.setKey(resourceMessageProvider.getMessage("req-time-cost.label"));
        requestStart.setKey(resourceMessageProvider.getMessage("req-start-time.label"));
        requestEnd.setKey(resourceMessageProvider.getMessage("req-end-time.label"));
        respTime.setKey(resourceMessageProvider.getMessage("resp-time-cost.label"));
        respStart.setKey(resourceMessageProvider.getMessage("resp-start-time.label"));
        respEnd.setKey(resourceMessageProvider.getMessage("resp-end-time.label"));

        requestSize.setKey(resourceMessageProvider.getMessage("req-size.label"));
        responseSize.setKey(resourceMessageProvider.getMessage("resp-size.label"));
        averageSpeed.setKey(resourceMessageProvider.getMessage("avg-speed.label"));
    }
}
