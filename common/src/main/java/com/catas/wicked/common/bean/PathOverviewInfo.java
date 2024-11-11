package com.catas.wicked.common.bean;

import com.catas.wicked.common.provider.ResourceMessageProvider;
import jakarta.inject.Singleton;
import lombok.Getter;

@Getter
@Singleton
public class PathOverviewInfo {

    private final ResourceMessageProvider resourceMessageProvider;

    private final PairEntry host = new PairEntry("Host");
    private final PairEntry port = new PairEntry("Port");
    private final PairEntry path = new PairEntry("Path");
    private final PairEntry protocol = new PairEntry("Protocol");
    private final PairEntry totalCnt = new PairEntry("Total");
    private final PairEntry getCnt = new PairEntry("GET");
    private final PairEntry postCnt = new PairEntry("POST");

    private final PairEntry timeCost = new PairEntry("Time Cost");
    private final PairEntry startTime = new PairEntry("Start");
    private final PairEntry endTime = new PairEntry("End");
    private final PairEntry averageSpeed = new PairEntry("Average Speed");

    private final PairEntry totalSize = new PairEntry("Total");
    private final PairEntry requestsSize = new PairEntry("Requests");
    private final PairEntry responsesSize = new PairEntry("Responses");

    public PathOverviewInfo(ResourceMessageProvider resourceMessageProvider) {
        this.resourceMessageProvider = resourceMessageProvider;
        refreshKey();
    }

    private void refreshKey() {
        host.setKey(resourceMessageProvider.getMessage("host.label"));
        port.setKey(resourceMessageProvider.getMessage("port.label"));
        path.setKey(resourceMessageProvider.getMessage("path.label"));
        protocol.setKey(resourceMessageProvider.getMessage("protocol.label"));
        totalCnt.setKey(resourceMessageProvider.getMessage("total-cnt.label"));
        getCnt.setKey(resourceMessageProvider.getMessage("get-cnt.label"));
        postCnt.setKey(resourceMessageProvider.getMessage("post-cnt.label"));

        timeCost.setKey(resourceMessageProvider.getMessage("time-cost.label"));
        startTime.setKey(resourceMessageProvider.getMessage("start-time.label"));
        endTime.setKey(resourceMessageProvider.getMessage("end-time.label"));
        averageSpeed.setKey(resourceMessageProvider.getMessage("avg-speed.label"));

        totalSize.setKey(resourceMessageProvider.getMessage("total-size.label"));
        requestsSize.setKey(resourceMessageProvider.getMessage("req-size.label"));
        responsesSize.setKey(resourceMessageProvider.getMessage("resp-size.label"));
    }
}
