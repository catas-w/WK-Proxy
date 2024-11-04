package com.catas.wicked.proxy.service;

import com.catas.wicked.common.bean.message.BaseMessage;
import com.catas.wicked.common.bean.message.RenderMessage;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.pipeline.MessageQueue;
import com.catas.wicked.common.pipeline.Topic;
import com.catas.wicked.proxy.gui.controller.DetailTabController;
import com.catas.wicked.proxy.gui.controller.DetailWebViewController;
import com.catas.wicked.proxy.render.TabRenderer;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.ehcache.Cache;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;


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

    @Inject
    private Cache<String, RequestMessage> requestCache;

    private String currentRequestId;

    @Inject
    private ApplicationConfig appConfig;

    @Inject
    private MessageQueue messageQueue;
    private BlockingQueue<BaseMessage> queue;

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


    @PostConstruct
    public void init() {
        // this.queue = new LinkedBlockingQueue<>();
        this.renderFuncMap = new HashMap<>();
        renderFuncMap.put(RenderMessage.Tab.REQUEST, requestTabRenderer);
        renderFuncMap.put(RenderMessage.Tab.RESPONSE, responseTabRenderer);
        renderFuncMap.put(RenderMessage.Tab.OVERVIEW, overViewTabRenderer);
        renderFuncMap.put(RenderMessage.Tab.TIMING, timingTabRenderer);

        messageQueue.subscribe(Topic.RENDER, msg -> {
            if (msg instanceof RenderMessage renderMsg) {
                log.info("rendingMsg: {}", msg);
                TabRenderer renderer = renderFuncMap.get(renderMsg.getTargetTab());
                if (renderer != null) {
                    renderer.render(renderMsg);
                } else {
                    log.warn("consumer not exist");
                }
            } else {
                log.warn("cannot to process message type: {}", msg);
            }
        });
    }

    /**
     * update request tab by requestId
     * @param requestId requestId, nullable
     */
    public void updateRequestTab(String requestId) {
        // String curRequestId = appConfig.getCurrentRequestId().get();
        String curRequestId = appConfig.getObservableConfig().getCurrentRequestId();
        appConfig.getObservableConfig().currentRequestIdProperty().set(requestId);

        if (StringUtils.equals(curRequestId, requestId)) {
            return;
        }
        // appConfig.getCurrentRequestId().set(requestId);

        String toSend = requestId;
        if (requestId == null) {
            toSend = RenderMessage.EMPTY_MSG;
        }
        messageQueue.clearMsg(Topic.RENDER);

        // display path info
        if (requestId != null && requestId.startsWith(RenderMessage.PATH_MSG)) {
            messageQueue.pushMsg(Topic.RENDER, new RenderMessage(toSend, RenderMessage.Tab.OVERVIEW));
            return;
        }

        // current requestView tab
        String curTab = detailTabController.getActiveRequestTab();
        RenderMessage.Tab firstTargetTab = RenderMessage.Tab.valueOfIgnoreCase(curTab);

        Queue<RenderMessage> messages = new PriorityQueue<>(Comparator.comparingInt(o -> o.getTargetTab().getOrder()));
        messages.offer(new RenderMessage(toSend, RenderMessage.Tab.OVERVIEW));
        messages.offer(new RenderMessage(toSend, RenderMessage.Tab.REQUEST));
        messages.offer(new RenderMessage(toSend, RenderMessage.Tab.RESPONSE));
        messages.offer(new RenderMessage(toSend, RenderMessage.Tab.TIMING));

        // render current tab first
        Iterator<RenderMessage> iterator = messages.iterator();
        while (iterator.hasNext()) {
            RenderMessage msg = iterator.next();
            if (msg.getTargetTab() == firstTargetTab) {
                // pushMsg(msg);
                messageQueue.pushMsg(Topic.RENDER, msg);
                iterator.remove();
            }
        }

        while (!messages.isEmpty()) {
            // pushMsg(messages.poll());
            messageQueue.pushMsg(Topic.RENDER, messages.poll());
        }
    }
}
