package com.catas.wicked.proxy.render.tab;

import com.catas.wicked.common.bean.message.RenderMessage;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.util.ImageUtils;
import com.catas.wicked.common.util.WebUtils;
import com.catas.wicked.proxy.gui.componet.SideBar;
import com.catas.wicked.proxy.gui.componet.richtext.DisplayCodeArea;
import com.catas.wicked.proxy.gui.controller.DetailTabController;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.SingleSelectionModel;
import javafx.scene.control.Tab;
import javafx.scene.layout.AnchorPane;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.entity.ContentType;
import org.ehcache.Cache;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static com.catas.wicked.common.constant.ProxyConstant.OVERSIZE_MSG;

@Slf4j
@Singleton
public class RequestTabRenderer extends AbstractTabRenderer {

    @Inject
    private DetailTabController detailTabController;

    @Inject
    private Cache<String, RequestMessage> requestCache;


    @Inject
    private ApplicationConfig appConfig;

    @Override
    public void render(RenderMessage renderMsg) {
        // System.out.println("-- render request --");
        detailTabController.getReqHeaderMsgLabel().setVisible(renderMsg.isEmpty());
        detailTabController.getReqContentMsgLabel().setVisible(renderMsg.isEmpty());
        detailTabController.getReqMsgLabelBox().setVisible(renderMsg.isEmpty());
        detailTabController.getReqOutputMsgLabel().setVisible(false);
        if (renderMsg.isEmpty()) {
            setEmptyMsgLabel(detailTabController.getReqHeaderMsgLabel());
            setEmptyMsgLabel(detailTabController.getReqContentMsgLabel());
            return;
        }
        if (renderMsg.isPath()) {
            return;
        }
        detailTabController.showRequestOnlyTabs();
        RequestMessage request = requestCache.get(renderMsg.getRequestId());
        displayRequest(request);
    }

    /**
     * exhibit request info
     */
    public void displayRequest(RequestMessage request) {
        if (request == null) {
            return;
        }

        // display headers
        Map<String, String> headers = request.getHeaders();
        renderHeaders(headers, detailTabController.getReqHeaderTable());
        detailTabController.getReqHeaderArea().replaceText(WebUtils.getHeaderText(headers), true);

        // display query-params if exist
        String query = request.getUrl().getQuery();
        detailTabController.getReqParamArea().replaceText(query, true);

        // display request content
        // display oversize msg
        if (request.isOversize()) {
            setMsgLabel(detailTabController.getReqContentMsgLabel(), OVERSIZE_MSG, detailTabController.getReqMsgLabelBox());
            return;
        }

        ContentType contentType = WebUtils.getContentType(headers);
        byte[] content = WebUtils.parseContent(request.getHeaders(), request.getBody());
        Node target;
        if (contentType != null && contentType.getMimeType().startsWith("image/")) {
            target = detailTabController.getReqImageView();
        } else {
            target = detailTabController.getReqPayloadCodeArea();
        }
        SideBar.Strategy strategy = predictCodeStyle(contentType, content.length);
        detailTabController.getReqContentSideBar().setStrategy(strategy);
        renderRequestContent(content, contentType, target);

        boolean hasQuery = query != null && !query.isEmpty();
        boolean hasContent = content.length > 0;
        // System.out.printf("hasQuery: %s, hasContent: %s\n", hasQuery, hasContent);
        SingleSelectionModel<Tab> selectionModel = detailTabController.getReqPayloadTabPane().getSelectionModel();

        String title = "Payload";
        detailTabController.getReqContentMsgLabel().setVisible(false);
        if (hasQuery && hasContent) {
            detailTabController.getReqPayloadTabPane().setTabMaxHeight(20);
            detailTabController.getReqPayloadTabPane().setTabMinHeight(20);
        } else if (hasQuery) {
            selectionModel.clearAndSelect(1);
            detailTabController.getReqPayloadTabPane().setTabMaxHeight(0);
            title = "Query Parameters";
        } else if (hasContent) {
            selectionModel.clearAndSelect(0);
            detailTabController.getReqPayloadTabPane().setTabMaxHeight(0);
            title = "Content";
        } else {
            // detailTabController.getReqPayloadTitlePane().setExpanded(false);
            detailTabController.getReqContentMsgLabel().setVisible(true);
        }

        String finalTitle = title;
        Platform.runLater(() -> {
            detailTabController.getReqPayloadTitlePane().setText(finalTitle);
        });
    }

    private void renderRequestContent(byte[] content, ContentType contentType, Node target) {
        if (target == null) {
            return;
        }
        target.setVisible(true);
        Parent parent = target.getParent();
        for (Node child : ((AnchorPane) parent).getChildren()) {
            if (!(child instanceof SideBar) && child != target) {
                child.setVisible(false);
            }
        }

        Charset charset = contentType != null && contentType.getCharset() != null ?
                contentType.getCharset() : StandardCharsets.UTF_8;
        if (target == detailTabController.getReqPayloadCodeArea()) {
            String contentStr = new String(content, charset);
            // TODO bug-fix codeStyle 是之前的
            ((DisplayCodeArea) target).setContentType(contentType);
            detailTabController.getReqPayloadCodeArea().replaceText(contentStr, true);
        } else if (target == detailTabController.getReqContentTable()) {
            assert contentType != null;
            if (StringUtils.equals(ContentType.APPLICATION_FORM_URLENCODED.getMimeType(), contentType.getMimeType())) {
                // parse url-encode
                Map<String, String> formData = WebUtils.parseQueryParams(new String(content, charset));
                renderHeaders(formData, detailTabController.getReqContentTable());
            } else {
                // parse multipart-form
                try {
                    Map<String, String> formData = WebUtils.parseMultipartForm(
                            content, contentType.getParameter("boundary"), charset);
                    renderHeaders(formData, detailTabController.getReqContentTable());
                } catch (IOException e) {
                    log.error("Error in parsing multipart-form data.", e);
                }
            }
        } else if (target == detailTabController.getReqImageView()) {
            InputStream inputStream = new ByteArrayInputStream(content);
            try {
                assert contentType != null;
                String mimeType = contentType.getMimeType();
                detailTabController.getReqImageView().setImage(inputStream, mimeType);
            } catch (Exception e) {
                detailTabController.getReqMsgLabelBox().setVisible(true);
                detailTabController.getReqOutputMsgLabel().setVisible(true);
                setMsgLabel(detailTabController.getReqContentMsgLabel(),
                        "Image load error: " + contentType.getMimeType() + ", ", detailTabController.getReqMsgLabelBox());
            }
        }
    }
}
