package com.catas.wicked.proxy.render.tab;

import com.catas.wicked.common.bean.message.RenderMessage;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.util.WebUtils;
import com.catas.wicked.proxy.gui.componet.SideBar;
import com.catas.wicked.proxy.gui.controller.DetailTabController;
import com.catas.wicked.proxy.render.PreparedRender;
import com.catas.wicked.proxy.service.record.RequestRecordSnapshot;
import com.catas.wicked.proxy.service.record.RequestRecordStore;
import com.catas.wicked.proxy.service.record.ContentPreviewDecoder;
import com.catas.wicked.common.provider.ResourceMessageProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.SingleSelectionModel;
import javafx.scene.control.Tab;
import javafx.scene.layout.AnchorPane;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.catas.wicked.common.constant.ProxyConstant.OVERSIZE_MSG;

@Slf4j
@Singleton
public class RequestTabRenderer extends AbstractTabRenderer {

    private static final String PAYLOAD_TITLE_KEY = "payload.label";
    private static final String QUERY_PARAMETERS_TITLE_KEY = "query-params.label";
    private static final String CONTENT_TITLE_KEY = "content.label";

    @Inject
    private DetailTabController detailTabController;

    @Inject
    private RequestRecordStore requestStore;

    @Inject
    private ResourceMessageProvider resourceMessageProvider;

    @Override
    public PreparedRender prepare(RenderMessage renderMsg) {
        String requestId = renderMsg.getRequestId();
        if (renderMsg.isPath()) {
            return PreparedRender.noop(requestId, renderMsg.isEmpty());
        }
        if (renderMsg.isEmpty()) {
            return new PreparedRender(requestId, true, this::displayEmpty);
        }

        RequestRecordSnapshot snapshot = requestStore.snapshot(requestId);
        RequestRenderData data = prepareRequest(snapshot);
        return new PreparedRender(requestId, false, () -> displayRequest(data));
    }

    private RequestRenderData prepareRequest(RequestRecordSnapshot snapshot) {
        if (snapshot == null || snapshot.message() == null) {
            return null;
        }
        RequestMessage request = snapshot.message();
        Map<String, String> headers = request.getHeaders() == null
                ? Collections.emptyMap()
                : new LinkedHashMap<>(request.getHeaders());
        String query = request.getUrl() == null ? "" : request.getUrl().getQuery();
        query = query == null ? "" : query;
        byte[] body = request.getBody() == null ? new byte[0] : request.getBody();
        ContentType contentType = WebUtils.getContentType(headers);
        boolean image = contentType != null && contentType.getMimeType().startsWith("image/");
        byte[] content = image ? body : ContentPreviewDecoder.decode(headers, body);
        SideBar.Strategy strategy = predictCodeStyle(contentType, content.length);
        Charset charset = contentType != null && contentType.getCharset() != null
                ? contentType.getCharset() : StandardCharsets.UTF_8;
        String contentText = image ? "" : ContentPreviewDecoder.toPreviewText(content, charset);
        return new RequestRenderData(headers, WebUtils.getHeaderText(headers), query, content,
                contentText, contentType, strategy, image, request.isOversize(), request.isEncrypted(),
                snapshot.requestPayloadEvicted());
    }

    private void displayEmpty() {
        detailTabController.getReqHeaderMsgLabel().setVisible(true);
        detailTabController.getReqContentMsgLabel().setVisible(true);
        detailTabController.getReqMsgLabelBox().setVisible(true);
        detailTabController.getReqOutputMsgLabel().setVisible(false);
        clearRequestContent();
        setEmptyMsgLabel(detailTabController.getReqHeaderMsgLabel());
        setEmptyMsgLabel(detailTabController.getReqContentMsgLabel());
    }

    private void displayRequest(RequestRenderData data) {
        if (data == null) {
            displayEmpty();
            return;
        }

        detailTabController.getReqHeaderMsgLabel().setVisible(false);
        detailTabController.getReqContentMsgLabel().setVisible(false);
        detailTabController.getReqMsgLabelBox().setVisible(false);
        detailTabController.getReqOutputMsgLabel().setVisible(false);

        renderHeaders(data.headers(), detailTabController.getReqHeaderTable());
        detailTabController.getReqHeaderArea().replaceText(data.headerText(), true);
        detailTabController.getReqParamArea().replaceText(data.query(), true);

        if (data.payloadEvicted()) {
            clearPayloadViews();
            setMsgLabel(detailTabController.getReqContentMsgLabel(),
                    resourceMessageProvider.getMessage("payload-released.label"),
                    detailTabController.getReqMsgLabelBox());
            updatePayloadTabs(!data.query().isEmpty(), false);
            return;
        }
        if (data.oversize()) {
            clearPayloadViews();
            setMsgLabel(detailTabController.getReqContentMsgLabel(), OVERSIZE_MSG,
                    detailTabController.getReqMsgLabelBox());
            return;
        }
        if (data.encrypted() || data.content().length == 0) {
            clearPayloadViews();
            setEmptyMsgLabel(detailTabController.getReqContentMsgLabel());
            detailTabController.getReqMsgLabelBox().setVisible(true);
            updatePayloadTabs(!data.query().isEmpty(), false);
            return;
        }

        detailTabController.getReqContentSideBar().setStrategy(data.strategy());
        if (data.image()) {
            showPayloadTarget(detailTabController.getReqImageView());
            try {
                detailTabController.getReqImageView().setImage(
                        new ByteArrayInputStream(data.content()), data.contentType().getMimeType());
            } catch (Exception e) {
                detailTabController.getReqOutputMsgLabel().setVisible(true);
                setMsgLabel(detailTabController.getReqContentMsgLabel(),
                        "Image load error: " + data.contentType().getMimeType() + ", ",
                        detailTabController.getReqMsgLabelBox());
            }
        } else {
            showPayloadTarget(detailTabController.getReqPayloadCodeArea());
            detailTabController.getReqPayloadCodeArea().setContentType(data.contentType());
            detailTabController.getReqPayloadCodeArea().replaceText(data.contentText(), true);
        }
        updatePayloadTabs(!data.query().isEmpty(), true);
    }

    private void clearRequestContent() {
        renderHeaders(Collections.emptyMap(), detailTabController.getReqHeaderTable());
        detailTabController.getReqHeaderArea().replaceText("", true);
        detailTabController.getReqParamArea().replaceText("", true);
        clearPayloadViews();
    }

    private void clearPayloadViews() {
        detailTabController.getReqPayloadCodeArea().replaceText("", true);
        detailTabController.getReqPayloadCodeArea().setVisible(true);
        detailTabController.getReqImageView().setVisible(false);
        detailTabController.getReqContentTable().getItems().clear();
    }

    private void showPayloadTarget(Node target) {
        target.setVisible(true);
        Parent parent = target.getParent();
        if (!(parent instanceof AnchorPane anchorPane)) {
            return;
        }
        for (Node child : anchorPane.getChildren()) {
            if (!(child instanceof SideBar) && child != target) {
                child.setVisible(false);
            }
        }
    }

    private void updatePayloadTabs(boolean hasQuery, boolean hasContent) {
        SingleSelectionModel<Tab> selectionModel = detailTabController.getReqPayloadTabPane().getSelectionModel();
        String titleKey = PAYLOAD_TITLE_KEY;
        if (hasQuery && hasContent) {
            detailTabController.getReqPayloadTabPane().setTabMaxHeight(20);
            detailTabController.getReqPayloadTabPane().setTabMinHeight(20);
        } else if (hasQuery) {
            selectionModel.clearAndSelect(1);
            detailTabController.getReqPayloadTabPane().setTabMaxHeight(0);
            titleKey = QUERY_PARAMETERS_TITLE_KEY;
        } else if (hasContent) {
            selectionModel.clearAndSelect(0);
            detailTabController.getReqPayloadTabPane().setTabMaxHeight(0);
            titleKey = CONTENT_TITLE_KEY;
        } else {
            detailTabController.getReqContentMsgLabel().setVisible(true);
        }
        detailTabController.setRequestPayloadTitleKey(titleKey);
    }

    private record RequestRenderData(Map<String, String> headers, String headerText, String query,
                                     byte[] content, String contentText, ContentType contentType,
                                     SideBar.Strategy strategy, boolean image, boolean oversize,
                                     boolean encrypted, boolean payloadEvicted) {
    }
}
