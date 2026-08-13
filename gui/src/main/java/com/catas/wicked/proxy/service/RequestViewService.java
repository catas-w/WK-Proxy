package com.catas.wicked.proxy.service;

import com.catas.wicked.common.bean.message.RenderMessage;
import com.catas.wicked.common.bean.RequestCell;
import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.proxy.gui.controller.DetailTabController;
import com.catas.wicked.proxy.gui.controller.DetailWebViewController;
import com.catas.wicked.proxy.render.TabRenderer;
import com.catas.wicked.proxy.render.PreparedRender;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import javafx.application.Platform;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;


/**
 * Update gui of tab-pane
 */
@Data
@Slf4j
@Singleton
public class RequestViewService {

    @Inject
    private DetailTabController detailTabController;

    @Inject
    private DetailWebViewController detailWebViewController;

    private String currentRequestId;

    @Inject
    private ApplicationConfig appConfig;

    @Inject
    private LocalizationService localization;

    @Named("request")
    @Inject
    private TabRenderer requestTabRenderer;

    @Named("response")
    @Inject
    private TabRenderer responseTabRenderer;

    @Named("overView")
    @Inject
    private TabRenderer overViewTabRenderer;

    @Named("timing")
    @Inject
    private TabRenderer timingTabRenderer;

    private Map<RenderMessage.Tab, TabRenderer> renderFuncMap;

    private static final String REQ_HEADER = "requestHeaders";
    private static final String REQ_DETAIL = "requestDetail";
    private static final String RESP_HEADER = "responseHeaders";
    private static final String RESP_DETAIL = "responseDetail";

    private static final String ERROR_DATA = "<Error loading data>";
    private final AtomicBoolean applicationGroupRefreshScheduled = new AtomicBoolean();
    private final AtomicBoolean currentRequestRefreshScheduled = new AtomicBoolean();
    private final AtomicLong renderGeneration = new AtomicLong();
    private final ThreadPoolExecutor renderExecutor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(1), runnable -> {
                Thread thread = new Thread(runnable, "request-detail-render");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.DiscardOldestPolicy());
    private boolean tabListenerInstalled;


    @PostConstruct
    public void init() {
        // this.queue = new LinkedBlockingQueue<>();
        this.renderFuncMap = new HashMap<>();
        renderFuncMap.put(RenderMessage.Tab.REQUEST, requestTabRenderer);
        renderFuncMap.put(RenderMessage.Tab.RESPONSE, responseTabRenderer);
        renderFuncMap.put(RenderMessage.Tab.OVERVIEW, overViewTabRenderer);
        renderFuncMap.put(RenderMessage.Tab.TIMING, timingTabRenderer);

        localization.languageProperty().addListener((observable, oldValue, newValue) ->
                refreshCurrentOverview());
    }

    @PreDestroy
    void shutdownRenderer() {
        renderExecutor.shutdownNow();
    }

    private void installTabListener() {
        if (tabListenerInstalled) {
            return;
        }
        tabListenerInstalled = true;
        detailTabController.getMainTabPane().getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> renderActiveTab());
    }

    private void submitRender(RenderMessage renderMsg) {
        renderExecutor.getQueue().clear();
        renderExecutor.execute(() -> {
            TabRenderer renderer = renderFuncMap.get(renderMsg.getTargetTab());
            if (renderer == null) {
                return;
            }
            long started = System.nanoTime();
            PreparedRender preparedRender = renderer.prepare(renderMsg);
            log.debug("Prepared {} for {} in {} ms", renderMsg.getTargetTab(),
                    renderMsg.getRequestId(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            Platform.runLater(() -> applyIfCurrent(renderMsg, preparedRender));
        });
    }

    private void applyIfCurrent(RenderMessage renderMsg, PreparedRender preparedRender) {
        String selectedRequestId = appConfig.getObservableConfig().getCurrentRequestId();
        if (!matchesSelection(renderMsg, selectedRequestId, renderGeneration.get())) {
            return;
        }
        preparedRender.apply();
    }

    static boolean matchesSelection(RenderMessage renderMsg, String selectedRequestId,
                                    long currentGeneration) {
        return renderMsg.getRenderGeneration() == currentGeneration && (renderMsg.isEmpty()
                ? selectedRequestId == null
                : StringUtils.equals(renderMsg.getRequestId(), selectedRequestId));
    }

    /**
     * update request tab by requestId
     * @param requestId requestId, nullable
     */
    public void updateRequestTab(String requestId) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> updateRequestTab(requestId));
            return;
        }
        installTabListener();
        String curRequestId = appConfig.getObservableConfig().getCurrentRequestId();
        appConfig.getObservableConfig().currentRequestIdProperty().set(requestId);
        detailTabController.setRequestDetailsAvailable(requestDetailsAvailable(requestId));

        if (StringUtils.equals(curRequestId, requestId)) {
            return;
        }
        long generation = renderGeneration.incrementAndGet();
        String toSend = requestId == null ? RenderMessage.EMPTY_MSG : requestId;

        if (RenderMessage.isOverviewOnly(requestId)) {
            submitRender(new RenderMessage(toSend, RenderMessage.Tab.OVERVIEW, generation));
            return;
        }
        submitRender(new RenderMessage(toSend, detailTabController.getActiveRenderTab(), generation));
    }

    static boolean requestDetailsAvailable(String requestId) {
        return requestId != null && !RenderMessage.isOverviewOnly(requestId);
    }

    private void renderActiveTab() {
        String requestId = appConfig.getObservableConfig().getCurrentRequestId();
        if (requestId == null || RenderMessage.isOverviewOnly(requestId)) {
            return;
        }
        submitRender(new RenderMessage(requestId, detailTabController.getActiveRenderTab(),
                renderGeneration.get()));
    }

    public void updateApplicationGroupTab(RequestCell requestCell) {
        if (requestCell == null) {
            updateRequestTab(null);
            return;
        }
        String prefix = requestCell.getNodeType() == RequestCell.NodeType.HOST
                ? RenderMessage.APPLICATION_HOST_MSG : RenderMessage.APPLICATION_MSG;
        updateRequestTab(prefix + requestCell.getNodeKey());
    }

    public void refreshCurrentApplicationGroup() {
        String selectionId = appConfig.getObservableConfig().getCurrentRequestId();
        if (selectionId == null || (!selectionId.startsWith(RenderMessage.APPLICATION_MSG)
                && !selectionId.startsWith(RenderMessage.APPLICATION_HOST_MSG))
                || !applicationGroupRefreshScheduled.compareAndSet(false, true)) {
            return;
        }
        Platform.runLater(() -> {
            applicationGroupRefreshScheduled.set(false);
            String currentSelection = appConfig.getObservableConfig().getCurrentRequestId();
            if (!StringUtils.equals(selectionId, currentSelection)) {
                return;
            }
            if (detailTabController.getActiveRenderTab() == RenderMessage.Tab.OVERVIEW) {
                submitRender(new RenderMessage(currentSelection, RenderMessage.Tab.OVERVIEW,
                        renderGeneration.get()));
            }
        });
    }

    public void refreshCurrentRequest(String requestId) {
        String selectionId = appConfig.getObservableConfig().getCurrentRequestId();
        if (requestId == null || !StringUtils.equals(requestId, selectionId)
                || RenderMessage.isOverviewOnly(selectionId)
                || !currentRequestRefreshScheduled.compareAndSet(false, true)) {
            return;
        }
        Platform.runLater(() -> {
            currentRequestRefreshScheduled.set(false);
            if (!StringUtils.equals(requestId,
                    appConfig.getObservableConfig().getCurrentRequestId())) {
                return;
            }
            submitRender(new RenderMessage(requestId, detailTabController.getActiveRenderTab(),
                    renderGeneration.get()));
        });
    }

    private void refreshCurrentOverview() {
        String selectionId = appConfig.getObservableConfig().getCurrentRequestId();
        if (selectionId == null) {
            return;
        }
        if (detailTabController.getActiveRenderTab() == RenderMessage.Tab.OVERVIEW) {
            submitRender(new RenderMessage(selectionId, RenderMessage.Tab.OVERVIEW,
                    renderGeneration.get()));
        }
    }
}
