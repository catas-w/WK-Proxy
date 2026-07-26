package com.catas.wicked.proxy.message;

import com.catas.wicked.common.bean.ProcessInfo;
import com.catas.wicked.common.bean.RequestCell;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.bean.message.ResponseMessage;
import io.netty.handler.codec.http.HttpMethod;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ApplicationGroupStatisticsTest {

    @Test
    public void aggregatesCompletedAndPendingRequestsAtSnapshotScope() {
        ProcessInfo browser = process("Browser Helper", 11, 1);
        ProcessInfo renderer = process("Browser Renderer", 12, 1);
        RequestMessage completed = request("one", "GET", "HTTPS", 443, 100, 1_000, browser);
        ResponseMessage response = new ResponseMessage();
        response.setSize(200);
        response.setEndTime(1_300);
        completed.setResponse(response);
        RequestMessage pending = request("two", "PATCH", "HTTP", 80, 50, 900, renderer);
        Map<String, RequestMessage> requests = new LinkedHashMap<>();
        requests.put("one", completed);
        requests.put("two", pending);

        ApplicationGroupSnapshot snapshot = new ApplicationGroupSnapshot(RequestCell.NodeType.APPLICATION,
                "browser", "Browser", null, browser, Set.of("one.test", "two.test"),
                Set.of("one", "two"));
        ApplicationGroupOverview overview = ApplicationGroupStatistics.aggregate(snapshot, requests::get);

        assertEquals(2, overview.statistics().getCount());
        assertEquals(Integer.valueOf(1), overview.statistics().getCountMap().get(HttpMethod.GET));
        assertEquals(Integer.valueOf(1), overview.statistics().getCountMap().get(HttpMethod.PATCH));
        assertEquals(300, overview.statistics().getTimeCost());
        assertEquals(900, overview.statistics().getStartTime().getTime());
        assertEquals(1_300, overview.statistics().getEndTime().getTime());
        assertEquals(350, overview.statistics().getTotalSize());
        assertEquals(150, overview.statistics().getRequestsSize());
        assertEquals(200, overview.statistics().getResponsesSize());
        assertEquals(2, overview.domainCount());
        assertEquals(Set.of("HTTPS", "HTTP"), overview.protocols());
        assertEquals(Set.of(443, 80), overview.ports());
        assertTrue(overview.processNames().containsAll(Set.of("Browser Helper", "Browser Renderer")));
        assertTrue(overview.ownerPids().containsAll(Set.of(11L, 12L)));
        assertEquals(Set.of(1L), overview.applicationPids());
    }

    @Test
    public void sameHostSnapshotsRemainIsolatedByRequestIds() {
        RequestMessage browserRequest = request("browser-request", "GET", "HTTPS", 443,
                10, 100, process("Browser", 1, 1));
        RequestMessage clientRequest = request("client-request", "POST", "HTTPS", 443,
                20, 200, process("Client", 2, 2));
        Map<String, RequestMessage> requests = Map.of(
                "browser-request", browserRequest,
                "client-request", clientRequest);

        ApplicationGroupSnapshot browser = new ApplicationGroupSnapshot(RequestCell.NodeType.HOST,
                "browser-host", "Browser", "example.test", browserRequest.getProcessInfo(),
                Set.of("example.test"), Set.of("browser-request"));
        ApplicationGroupSnapshot client = new ApplicationGroupSnapshot(RequestCell.NodeType.HOST,
                "client-host", "Client", "example.test", clientRequest.getProcessInfo(),
                Set.of("example.test"), Set.of("client-request"));

        ApplicationGroupOverview browserOverview = ApplicationGroupStatistics.aggregate(browser, requests::get);
        ApplicationGroupOverview clientOverview = ApplicationGroupStatistics.aggregate(client, requests::get);

        assertEquals(1, browserOverview.statistics().getCount());
        assertEquals(Integer.valueOf(1), browserOverview.statistics().getCountMap().get(HttpMethod.GET));
        assertEquals(1, clientOverview.statistics().getCount());
        assertEquals(Integer.valueOf(1), clientOverview.statistics().getCountMap().get(HttpMethod.POST));
    }

    private static RequestMessage request(String id, String method, String protocol, int port, long size,
                                          long startTime, ProcessInfo processInfo) {
        RequestMessage request = new RequestMessage();
        request.setRequestId(id);
        request.setMethod(method);
        request.setProtocol(protocol);
        request.setRemotePort(port);
        request.setSize(size);
        request.setStartTime(startTime);
        request.setProcessInfo(processInfo);
        return request;
    }

    private static ProcessInfo process(String name, long ownerPid, long applicationPid) {
        return ProcessInfo.builder()
                .lookupStatus(ProcessInfo.LookupStatus.FOUND)
                .applicationName("Browser")
                .applicationExecutablePath("/Applications/Browser.app/Contents/MacOS/Browser")
                .ownerProcessName(name)
                .ownerPid(ownerPid)
                .applicationPid(applicationPid)
                .build();
    }
}
