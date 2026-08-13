package com.catas.wicked.proxy.render.tab;

import com.catas.wicked.common.bean.message.RenderMessage;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.bean.message.ResponseMessage;
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
public class ResponseTabRenderer extends AbstractTabRenderer {

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
            return new PreparedRender(requestId, true, () -> displayEmpty("Empty"));
        }

        RequestRecordSnapshot snapshot = requestStore.snapshot(requestId);
        ResponseRenderData data = prepareResponse(snapshot);
        return new PreparedRender(requestId, false, () -> displayResponse(data));
    }

    private ResponseRenderData prepareResponse(RequestRecordSnapshot snapshot) {
        if (snapshot == null || snapshot.message() == null) {
            return ResponseRenderData.forMissingRequest();
        }
        RequestMessage request = snapshot.message();
        ResponseMessage response = request.getResponse();
        if (response == null) {
            return ResponseRenderData.forPendingResponse();
        }
        Map<String, String> headers = response.getHeaders() == null
                ? Collections.emptyMap()
                : new LinkedHashMap<>(response.getHeaders());
        byte[] responseContent = response.getContent() == null ? new byte[0] : response.getContent();
        ContentType contentType = WebUtils.getContentType(headers);
        boolean image = contentType != null && contentType.getMimeType().startsWith("image/");
        byte[] content = image ? responseContent : ContentPreviewDecoder.decode(headers, responseContent);
        SideBar.Strategy strategy = predictCodeStyle(contentType, content.length);
        Charset charset = contentType != null && contentType.getCharset() != null
                ? contentType.getCharset() : StandardCharsets.UTF_8;
        String contentText = image ? "" : ContentPreviewDecoder.toPreviewText(content, charset);
        return new ResponseRenderData(false, false, request.isEncrypted(), response.isOversize(),
                snapshot.responsePayloadEvicted(),
                headers, WebUtils.getHeaderText(headers), content, contentText, contentType,
                strategy, image);
    }

    private void displayResponse(ResponseRenderData data) {
        if (data.missingRequest()) {
            displayEmpty("Empty");
            return;
        }
        if (data.pending()) {
            displayEmpty("Pending...");
            return;
        }

        detailTabController.getRespHeaderMsgLabel().setVisible(false);
        detailTabController.getRespMsgLabelBox().setVisible(false);
        detailTabController.getRespOutputMsgLabel().setVisible(false);
        renderHeaders(data.headers(), detailTabController.getRespHeaderTable());
        detailTabController.getRespHeaderArea().replaceText(data.headerText(), true);

        if (data.payloadEvicted()) {
            clearResponseContent();
            setMsgLabel(detailTabController.getRespContentMsgLabel(),
                    resourceMessageProvider.getMessage("payload-released.label"),
                    detailTabController.getRespMsgLabelBox());
            return;
        }
        if (data.oversize()) {
            clearResponseContent();
            setMsgLabel(detailTabController.getRespContentMsgLabel(), OVERSIZE_MSG,
                    detailTabController.getRespMsgLabelBox());
            return;
        }
        if (data.encrypted() || data.content().length == 0) {
            clearResponseContent();
            setMsgLabel(detailTabController.getRespContentMsgLabel(), "Empty",
                    detailTabController.getRespMsgLabelBox());
            return;
        }

        detailTabController.getRespSideBar().setStrategy(data.strategy());
        if (data.image()) {
            detailTabController.getRespContentArea().replaceText("", true);
            detailTabController.getRespContentArea().setVisible(false);
            detailTabController.getRespImageView().setVisible(true);
            try {
                detailTabController.getRespImageView().setImage(
                        new ByteArrayInputStream(data.content()), data.contentType().getMimeType());
            } catch (Exception e) {
                detailTabController.getRespOutputMsgLabel().setVisible(true);
                setMsgLabel(detailTabController.getRespContentMsgLabel(),
                        "Image load error: " + data.contentType().getMimeType() + ", ",
                        detailTabController.getRespMsgLabelBox());
            }
        } else {
            detailTabController.getRespContentArea().setVisible(true);
            detailTabController.getRespImageView().setVisible(false);
            detailTabController.getRespContentArea().setContentType(data.contentType());
            detailTabController.getRespContentArea().replaceText(data.contentText(), true);
        }
    }

    private void displayEmpty(String message) {
        detailTabController.getRespHeaderMsgLabel().setVisible(true);
        detailTabController.getRespMsgLabelBox().setVisible(true);
        detailTabController.getRespOutputMsgLabel().setVisible(false);
        renderHeaders(Collections.emptyMap(), detailTabController.getRespHeaderTable());
        detailTabController.getRespHeaderArea().replaceText("", true);
        clearResponseContent();
        setMsgLabel(detailTabController.getRespHeaderMsgLabel(), message,
                detailTabController.getRespMsgLabelBox());
        setMsgLabel(detailTabController.getRespContentMsgLabel(), message,
                detailTabController.getRespMsgLabelBox());
    }

    private void clearResponseContent() {
        detailTabController.getRespContentArea().replaceText("", true);
        detailTabController.getRespContentArea().setVisible(true);
        detailTabController.getRespImageView().setVisible(false);
    }

    private record ResponseRenderData(boolean missingRequest, boolean pending, boolean encrypted,
                                      boolean oversize, boolean payloadEvicted, Map<String, String> headers,
                                      String headerText, byte[] content, String contentText,
                                      ContentType contentType, SideBar.Strategy strategy,
                                      boolean image) {
        private static ResponseRenderData forMissingRequest() {
            return new ResponseRenderData(true, false, false, false, false, Collections.emptyMap(),
                    "", new byte[0], "", null, SideBar.Strategy.TEXT, false);
        }

        private static ResponseRenderData forPendingResponse() {
            return new ResponseRenderData(false, true, false, false, false, Collections.emptyMap(),
                    "", new byte[0], "", null, SideBar.Strategy.TEXT, false);
        }
    }
}
