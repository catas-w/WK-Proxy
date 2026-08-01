package com.catas.wicked.proxy.message;

import com.catas.wicked.common.bean.ProcessInfo;
import com.catas.wicked.common.bean.RequestCell;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.bean.message.ResponseMessage;
import com.catas.wicked.common.constant.ClientStatus;
import com.catas.wicked.proxy.gui.componet.FilterableTreeItem;
import com.catas.wicked.proxy.gui.controller.RequestViewController;
import javafx.scene.control.TreeItem;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ApplicationMessageTreeLifecycleTest {

    @Test
    public void constructionDoesNotAccessFxmlControls() {
        RequestViewController controller = new RequestViewController();

        ApplicationMessageTree tree = new ApplicationMessageTree(controller);

        assertNotNull(tree);
    }

    @Test
    public void cleanLeavesRetainsGroupsAndReusesThemForNewRequests() {
        TestRequestViewController controller = new TestRequestViewController();
        ApplicationMessageTree tree = new ApplicationMessageTree(controller, Runnable::run);
        RequestMessage first = request("one", "api.example.com", "/one", "Example App", "/Applications/Example.app");
        RequestMessage second = request("two", "api.example.com", "/two", "Example App", "/Applications/Example.app");
        tree.add(first);
        tree.add(second);

        TreeItem<RequestCell> application = controller.root.getChildren().get(0);
        TreeItem<RequestCell> host = application.getChildren().get(0);
        application.setExpanded(true);
        host.setExpanded(true);

        tree.cleanLeaves();

        assertEquals(1, controller.root.getChildren().size());
        assertSame(application, controller.root.getChildren().get(0));
        assertSame(host, application.getChildren().get(0));
        assertTrue(application.isExpanded());
        assertTrue(host.isExpanded());
        assertEquals(0, application.getChildren().get(0).getChildren().size());
        assertEquals(0, application.getValue().getCount());
        assertEquals(0, host.getValue().getCount());
        assertTrue(tree.requestIds(application.getValue()).isEmpty());

        RequestMessage replacement = request(
                "three", "api.example.com", "/three", "Example App", "/Applications/Example.app");
        tree.add(replacement);

        assertSame(application, controller.root.getChildren().get(0));
        assertSame(host, application.getChildren().get(0));
        assertEquals(1, host.getChildren().size());
        assertEquals(1, application.getValue().getCount());
        assertEquals(1, host.getValue().getCount());
        assertEquals(Set.of("three"), tree.requestIds(application.getValue()));

        tree.add(request("four", "other.example.com", "/four", "Example App", "/Applications/Example.app"));
        assertSame(application, controller.root.getChildren().get(0));
        assertEquals(2, application.getChildren().size());
        assertEquals(2, application.getValue().getCount());

        tree.add(request("five", "api.second.test", "/five", "Second App", "/Applications/Second.app"));
        assertEquals(2, controller.root.getChildren().size());
        assertSame(application, controller.root.getChildren().get(0));
        assertEquals("Second App", controller.root.getChildren().get(1).getValue().getPath());
    }

    @Test
    public void clearAfterCleaningLeavesRemovesAllGroups() {
        TestRequestViewController controller = new TestRequestViewController();
        ApplicationMessageTree tree = new ApplicationMessageTree(controller, Runnable::run);
        tree.add(request("one", "api.example.com", "/one", "Example App", "/Applications/Example.app"));

        tree.cleanLeaves();
        assertFalse(controller.root.getChildren().isEmpty());

        tree.clear();

        assertTrue(controller.root.getChildren().isEmpty());
    }

    @Test
    public void tracksFailedRequestsAcrossUpdatesMovesAndRemoval() {
        TestRequestViewController controller = new TestRequestViewController();
        ApplicationMessageTree tree = new ApplicationMessageTree(controller, Runnable::run);
        RequestMessage failed = request(
                "failed", "api.example.com", "/failed", "Example App", "/Applications/Example.app");
        RequestMessage retained = request(
                "retained", "api.example.com", "/retained", "Example App", "/Applications/Example.app");
        tree.add(failed);
        tree.add(retained);

        failed.updateClientStatus(ClientStatus.Status.TIMEOUT, "timeout");
        tree.updateStatus(failed);

        TreeItem<RequestCell> firstApplication = controller.root.getChildren().get(0);
        TreeItem<RequestCell> firstHost = firstApplication.getChildren().get(0);
        assertEquals(1, firstApplication.getValue().getFailedCount());
        assertEquals(1, firstHost.getValue().getFailedCount());
        assertEquals(RequestCell.TransferState.FAILED,
                tree.item("failed").getValue().getTransferState());

        RequestMessage staleSuccess = request(
                "failed", "api.example.com", "/failed", "Example App", "/Applications/Example.app");
        ResponseMessage response = new ResponseMessage();
        response.setStatus(200);
        staleSuccess.setResponse(response);
        tree.updateStatus(staleSuccess);
        assertEquals(RequestCell.TransferState.FAILED,
                tree.item("failed").getValue().getTransferState());
        assertEquals(1, firstApplication.getValue().getFailedCount());

        failed.setProcessInfo(ProcessInfo.builder()
                .applicationName("Second App")
                .applicationExecutablePath("/Applications/Second.app")
                .lookupStatus(ProcessInfo.LookupStatus.FOUND)
                .build());
        tree.update(failed, false);

        assertEquals(2, controller.root.getChildren().size());
        assertEquals(0, firstApplication.getValue().getFailedCount());
        TreeItem<RequestCell> secondApplication = controller.root.getChildren().get(1);
        assertEquals(1, secondApplication.getValue().getFailedCount());

        tree.remove(Set.of("failed"));
        assertEquals(1, controller.root.getChildren().size());
        assertEquals(0, firstApplication.getValue().getFailedCount());
    }

    private static RequestMessage request(String requestId, String host, String path,
                                          String applicationName, String executablePath) {
        RequestMessage request = new RequestMessage("https://" + host + path);
        request.setRequestId(requestId);
        request.setRemoteHost(host);
        request.setMethod("GET");
        request.setProcessInfo(ProcessInfo.builder()
                .applicationName(applicationName)
                .applicationExecutablePath(executablePath)
                .applicationPid(100L)
                .ownerProcessName(applicationName + " Helper")
                .ownerPid(101L)
                .lookupStatus(ProcessInfo.LookupStatus.FOUND)
                .build());
        return request;
    }

    private static final class TestRequestViewController extends RequestViewController {
        private final FilterableTreeItem<RequestCell> root = new FilterableTreeItem<>();

        @Override
        public FilterableTreeItem<RequestCell> getApplicationTreeRoot() {
            return root;
        }
    }
}
