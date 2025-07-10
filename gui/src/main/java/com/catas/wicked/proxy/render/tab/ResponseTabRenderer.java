package com.catas.wicked.proxy.render.tab;

import com.catas.wicked.common.bean.message.RenderMessage;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.bean.message.ResponseMessage;
import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.util.WebUtils;
import com.catas.wicked.proxy.gui.componet.SideBar;
import com.catas.wicked.proxy.gui.controller.DetailTabController;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;
import org.ehcache.Cache;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static com.catas.wicked.common.constant.ProxyConstant.OVERSIZE_MSG;

@Slf4j
@Singleton
public class ResponseTabRenderer extends AbstractTabRenderer {

    @Inject
    private DetailTabController detailTabController;

    @Inject
    private Cache<String, RequestMessage> requestCache;

    @Inject
    private ApplicationConfig appConfig;

    @Override
    public void render(RenderMessage renderMsg) {
        // System.out.println("-- render response --");
        detailTabController.getRespHeaderMsgLabel().setVisible(renderMsg.isEmpty());
        detailTabController.getRespMsgLabelBox().setVisible(renderMsg.isEmpty());
        detailTabController.getRespOutputMsgLabel().setVisible(false);
        if (renderMsg.isEmpty()) {
            setEmptyMsgLabel(detailTabController.getRespHeaderMsgLabel());
            setEmptyMsgLabel(detailTabController.getRespContentMsgLabel());
            return;
        }
        if (renderMsg.isPath()) {
            return;
        }
        detailTabController.showRequestOnlyTabs();
        // fix NPE because "request" is null
        RequestMessage request = requestCache.get(renderMsg.getRequestId());
        displayResponse(request);
    }

    public void displayResponse(RequestMessage request) {
        if (request == null) {
            setMsgLabel(detailTabController.getRespHeaderMsgLabel(), "Empty", detailTabController.getRespMsgLabelBox());
            setMsgLabel(detailTabController.getRespContentMsgLabel(), "Empty", detailTabController.getRespMsgLabelBox());
            return;
        }
        ResponseMessage response = request.getResponse();
        if (response == null) {
            setMsgLabel(detailTabController.getRespHeaderMsgLabel(), "Pending...", detailTabController.getRespMsgLabelBox());
            setMsgLabel(detailTabController.getRespContentMsgLabel(), "Pending...", detailTabController.getRespMsgLabelBox());
            return;
        }
        // headers
        Map<String, String> headers = response.getHeaders();
        renderHeaders(headers, detailTabController.getRespHeaderTable());
        detailTabController.getRespHeaderArea().replaceText(WebUtils.getHeaderText(headers), true);

        // content
        byte[] parsedContent = WebUtils.parseContent(response.getHeaders(), response.getContent());
        if (response.isOversize()) {
            setMsgLabel(detailTabController.getRespContentMsgLabel(), OVERSIZE_MSG, detailTabController.getRespMsgLabelBox());
            return;
        }
        ContentType contentType = WebUtils.getContentType(headers);
        SideBar.Strategy strategy = predictCodeStyle(contentType, parsedContent.length);
        detailTabController.getRespSideBar().setStrategy(strategy);

        if (parsedContent.length == 0) {
            // detailTabController.getRespContentMsgLabel().setVisible(true);
            detailTabController.getRespMsgLabelBox().setVisible(true);
            setMsgLabel(detailTabController.getRespContentMsgLabel(), "Empty", detailTabController.getRespMsgLabelBox());
            return;
        }

        if (strategy == SideBar.Strategy.IMG) {
            detailTabController.getRespContentArea().setVisible(false);
            detailTabController.getRespImageView().setVisible(true);
            String mimeType = contentType.getMimeType();
            InputStream inputStream = new ByteArrayInputStream(parsedContent);
            try {
                detailTabController.getRespImageView().setImage(inputStream, mimeType);
            } catch (Exception e) {
                detailTabController.getRespOutputMsgLabel().setVisible(true);
                setMsgLabel(detailTabController.getRespContentMsgLabel(),
                        "Image load error: " + mimeType + ", ", detailTabController.getRespMsgLabelBox());
            }
        } else {
            detailTabController.getRespContentArea().setVisible(true);
            detailTabController.getRespImageView().setVisible(false);
            Charset charset = contentType != null && contentType.getCharset() != null ?
                    contentType.getCharset() : StandardCharsets.UTF_8;
            String contentStr = new String(parsedContent, charset);
            detailTabController.getRespContentArea().replaceText(contentStr, true);
        }
    }
}
