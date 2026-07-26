package com.catas.wicked.common.bean;

import com.catas.wicked.common.provider.ResourceMessageProvider;
import jakarta.inject.Singleton;
import lombok.Getter;

@Getter
@Singleton
public class ApplicationGroupOverviewInfo {

    private final PairEntry application = new PairEntry("Application");
    private final PairEntry applicationPids = new PairEntry("Application PID");
    private final PairEntry processes = new PairEntry("Processes");
    private final PairEntry ownerPids = new PairEntry("Owner PID");
    private final PairEntry executable = new PairEntry("Executable");
    private final PairEntry lookupStatus = new PairEntry("Lookup Status");
    private final PairEntry domainCount = new PairEntry("Domains");

    private final PairEntry host = new PairEntry("Host");
    private final PairEntry protocols = new PairEntry("Protocols");
    private final PairEntry ports = new PairEntry("Ports");

    private final PairEntry totalCnt = new PairEntry("Total");
    private final PairEntry getCnt = new PairEntry("GET");
    private final PairEntry postCnt = new PairEntry("POST");
    private final PairEntry otherCnt = new PairEntry("Other");

    private final PairEntry timeCost = new PairEntry("Time Cost");
    private final PairEntry startTime = new PairEntry("Start");
    private final PairEntry endTime = new PairEntry("End");
    private final PairEntry averageSpeed = new PairEntry("Average Speed");

    private final PairEntry totalSize = new PairEntry("Total");
    private final PairEntry requestsSize = new PairEntry("Requests");
    private final PairEntry responsesSize = new PairEntry("Responses");

    public ApplicationGroupOverviewInfo(ResourceMessageProvider resourceMessageProvider) {
        application.setKey(resourceMessageProvider.getMessage("source-application.label"));
        applicationPids.setKey(resourceMessageProvider.getMessage("source-app-pids.label"));
        processes.setKey(resourceMessageProvider.getMessage("source-processes.label"));
        ownerPids.setKey(resourceMessageProvider.getMessage("source-owner-pids.label"));
        executable.setKey(resourceMessageProvider.getMessage("source-executable.label"));
        lookupStatus.setKey(resourceMessageProvider.getMessage("source-status.label"));
        domainCount.setKey(resourceMessageProvider.getMessage("domain-count.label"));
        host.setKey(resourceMessageProvider.getMessage("host.label"));
        protocols.setKey(resourceMessageProvider.getMessage("protocols.label"));
        ports.setKey(resourceMessageProvider.getMessage("ports.label"));
        totalCnt.setKey(resourceMessageProvider.getMessage("total-cnt.label"));
        getCnt.setKey(resourceMessageProvider.getMessage("get-cnt.label"));
        postCnt.setKey(resourceMessageProvider.getMessage("post-cnt.label"));
        otherCnt.setKey(resourceMessageProvider.getMessage("other-cnt.label"));
        timeCost.setKey(resourceMessageProvider.getMessage("time-cost.label"));
        startTime.setKey(resourceMessageProvider.getMessage("start-time.label"));
        endTime.setKey(resourceMessageProvider.getMessage("end-time.label"));
        averageSpeed.setKey(resourceMessageProvider.getMessage("avg-speed.label"));
        totalSize.setKey(resourceMessageProvider.getMessage("total-size.label"));
        requestsSize.setKey(resourceMessageProvider.getMessage("req-size.label"));
        responsesSize.setKey(resourceMessageProvider.getMessage("resp-size.label"));
    }
}
