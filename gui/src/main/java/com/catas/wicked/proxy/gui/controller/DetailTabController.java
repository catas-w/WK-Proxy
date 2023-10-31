package com.catas.wicked.proxy.gui.controller;

import com.catas.wicked.common.bean.HeaderEntry;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.bean.message.ResponseMessage;
import com.catas.wicked.common.util.WebUtils;
import com.catas.wicked.proxy.gui.componet.MessageLabel;
import com.catas.wicked.proxy.gui.componet.ZoomImageView;
import com.catas.wicked.proxy.gui.componet.richtext.DisplayCodeArea;
import com.catas.wicked.proxy.render.RequestRenderer;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXTabPane;
import com.jfoenix.controls.JFXTextArea;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.SingleSelectionModel;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.entity.ContentType;
import org.fxmisc.richtext.CodeArea;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ResourceBundle;

@Singleton
public class DetailTabController implements Initializable {

    public DisplayCodeArea testCodeArea;
    @FXML
    private JFXComboBox<Labeled> respComboBox;
    @FXML
    private ZoomImageView respImageView;
    @FXML
    private MessageLabel overViewMsgLabel;
    @FXML
    private SplitPane respSplitPane;
    @FXML
    private SplitPane reqSplitPane;
    @FXML
    private CodeArea overviewArea;
    @FXML
    private TitledPane reqPayloadPane;
    @FXML
    private JFXTabPane reqPayloadTabPane;
    @FXML
    private TitledPane respHeaderPane;
    @FXML
    private TitledPane reqParamPane;
    @FXML
    private TitledPane respDataPane;
    @FXML
    private TitledPane reqHeaderPane;
    @FXML
    private TableView<HeaderEntry> reqHeaderTable;
    @FXML
    private DisplayCodeArea reqHeaderArea;
    @FXML
    private DisplayCodeArea reqParamArea;
    @FXML
    private DisplayCodeArea reqPayloadArea;
    @FXML
    private JFXTextArea reqTimingArea;
    @FXML
    private DisplayCodeArea respHeaderArea;
    @FXML
    private DisplayCodeArea respContentArea;
    @FXML
    private TableView<HeaderEntry> respHeaderTable;

    @Inject
    private RequestRenderer requestRenderer;

    private final Map<SplitPane, double[]> dividerPositionMap =new HashMap<>();

    private boolean dividerUpdating;

    private boolean midTitleCollapse;

    private static final String sampleCode = String.join("\n", new String[] {
            "Request Url:    http://google.com/path/index/1?query=aa&time=bb",
            "Request Method:    POST",
            "Status Code:    200",
            "Remote Address:    192.168.1.234:80",
            "Refer Policy:   cross-origin boolean",
    });

    private static final String sampleJson = "{\"resource_response\":{\"status\":true,\"code\":0,\"message\":\"确定\",\"endpoint_name\":\"v3_get_user_handler\",\"data\":\"{\\\"is_name_eligible_for_lead_form_autofill\\\":true,\\\"full_name\\\":\\\"铁柱 王\\\",\\\"email\\\":\\\"cvn78f35c@gmail.com\\\",\\\"fields\\\":[\\\"name\\\",\\\".email\\\",\\\"ss\\\"]}\",\"x_pinterest_sli_endpoint_name\":\"v3_get_user_handler\",\"http_status\":200},\"resource\":{\"name\":\"{\\\"name\\\":\\\"jack\\\"}\",\"options\":{\"bookmarks\":[\"-end-\"],\"url\":\"/v3/users/me/\",\"data\":{\"fields\":[\"user.full_name\",\"user.email\",\"user.is_name_eligible_for_lead_form_autofill\",\"useraaa\"]}}},\"request_identifier\":\"2547094186404461\"}";

    private static final String sampleXml = String.join("\n", new String[] {
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>",
            "<!-- Sample XML -->",
            "< orders >",
            "	<Order number=\"1\" table=\"center\">",
            "		<items>",
            "			<Item>",
            "				<type>ESPRESSO</type>",
            "				<shots>2</shots>",
            "				<iced>false</iced>",
            "				<orderNumber>1</orderNumber>",
            "			</Item>",
            "			<Item>",
            "				<type>CAPPUCCINO</type>",
            "				<shots>1</shots>",
            "				<iced>false</iced>",
            "				<orderNumber>1</orderNumber>",
            "			</Item>",
            "			<Item>",
            "			<type>LATTE</type>",
            "				<shots>2</shots>",
            "				<iced>false</iced>",
            "				<orderNumber>1</orderNumber>",
            "			</Item>",
            "			<Item>",
            "				<type>MOCHA</type>",
            "				<shots>3</shots>",
            "				<iced>true</iced>",
            "				<orderNumber>1</orderNumber>",
            "			</Item>",
            "		</items>",
            "	</Order>",
            "</orders>"
    });

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        dividerPositionMap.put(reqSplitPane, reqSplitPane.getDividerPositions().clone());
        dividerPositionMap.put(respSplitPane, respSplitPane.getDividerPositions().clone());

        addTitleListener(reqHeaderPane, reqSplitPane);
        addTitleListener(reqPayloadPane, reqSplitPane);
        addTitleListener(respHeaderPane, respSplitPane);
        addTitleListener(respDataPane, respSplitPane);

        resetComboBox(respComboBox);

        testCodeArea.replaceText(sampleXml);

        Map<String, String> map = new LinkedHashMap<>();
        map.put("aa", "bb");
        map.put("aa2", "bb");
        map.put("aa3", "bb");
        map.put("aa4", "bb");
        map.put("aa5", "bb");
        map.put("aa6", "bb");
        map.put("aa7", "bb");
        map.put("aa8", "bb");
        map.put("aa9", "bb");
        map.put("aa10", "bb");
        map.put("aa411", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36");

        // RequestMessage requestMessage = new RequestMessage("http://google.com/page");
        RequestMessage requestMessage = new RequestMessage("http://google.com/page?name=aa&age=22");

        requestMessage.setHeaders(map);
        requestMessage.setBody(sampleJson.getBytes(StandardCharsets.UTF_8));

        ResponseMessage responseMessage = new ResponseMessage();
        responseMessage.setStatus(200);
        responseMessage.setHeaders(map);
        responseMessage.setContent(sampleXml.getBytes(StandardCharsets.UTF_8));

        requestMessage.setResponse(responseMessage);
        displayRequest(requestMessage);
    }

    private void resetComboBox(ComboBox<Labeled> comboBox) {
        if (comboBox.getItems().isEmpty()) {
            // comboBox.setButtonCell(new Gra);
            comboBox.getItems().add(new Label("Text"));
            comboBox.getItems().add(new Label("JSON"));
            comboBox.getItems().add(new Label("Html"));
            comboBox.getItems().add(new Label("Xml"));
        }
        comboBox.getSelectionModel().selectFirst();
    }

    /**
     * synchronized dividers
     * @deprecated
     */
    private void bindDividerPosition(SplitPane splitPane) {
        if (splitPane.getDividers().size() < 2) {
            return;
        }
        ObservableList<SplitPane.Divider> dividers = splitPane.getDividers();
        dividers.get(0).positionProperty().addListener(((observable, oldValue, newValue) -> {
            if (dividerUpdating || splitPane.getDividers().size() < 2 || reqParamPane.isExpanded()) {
                return;
            }
            // System.out.println("Divider-0: " + newValue);
            if (newValue.doubleValue() > 0.95) {
                dividers.get(0).setPosition(0.95);
                dividers.get(1).setPosition(1.0);
                return;
            }
            dividerUpdating = true;
            double delta = newValue.doubleValue() - oldValue.doubleValue();
            dividers.get(1).setPosition(dividers.get(1).positionProperty().doubleValue() + delta);
            dividerUpdating = false;
        }));

        dividers.get(1).positionProperty().addListener(((observable, oldValue, newValue) -> {
            if (dividerUpdating || splitPane.getDividers().size() < 2 || reqParamPane.isExpanded()) {
                return;
            }
            // System.out.println("Divider-1: " + newValue);
            if (!midTitleCollapse) {
                return;
            }
            if (newValue.doubleValue() < 0.05) {
                dividers.get(0).setPosition(0.0);
                dividers.get(1).setPosition(0.05);
                return;
            }
            dividerUpdating = true;
            double delta = newValue.doubleValue() - oldValue.doubleValue();
            dividers.get(0).setPosition(dividers.get(0).positionProperty().doubleValue() + delta);
            dividerUpdating = false;
        }));
    }

    private void addTitleListener(TitledPane pane, SplitPane splitPane) {
        pane.expandedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                // open
                pane.maxHeightProperty().set(Double.POSITIVE_INFINITY);
                if (splitPane.getItems().size() == 2) {
                    splitPane.setDividerPositions(dividerPositionMap.get(splitPane));
                } else if (splitPane.getItems().size() == 3) {
                    // TODO bug: titledPane-1 expanded, titledPane-2,3 closed, tiledPane-3 cannot expand
                    int expandedNum = getExpandedNum(splitPane);
                    System.out.println("Expanded num: " + expandedNum);
                    if (expandedNum != 2) {
                        midTitleCollapse = false;
                        splitPane.setDividerPositions(0.33333, 0.66666);
                        return;
                    }
                    if (!reqParamPane.isExpanded()) {
                        ObservableList<SplitPane.Divider> dividers = splitPane.getDividers();
                        dividers.get(0).setPosition(0.5);
                        dividers.get(1).setPosition(0.5);
                    }
                }
            } else {
                // close
                pane.maxHeightProperty().set(Double.NEGATIVE_INFINITY);
                dividerPositionMap.put(splitPane, splitPane.getDividerPositions().clone());
                if (splitPane.getItems().size() == 3) {
                    if (getExpandedNum(splitPane) == 2 && !reqParamPane.isExpanded()) {
                        midTitleCollapse = true;
                    }
                }
            }
        });
    }
    private int getExpandedNum(SplitPane splitPane) {
        int res = 0;
        for (Node item : splitPane.getItems()) {
            if (item instanceof TitledPane titledPane) {
                res += titledPane.isExpanded() ? 1 : 0;
            }
        }
        return res;
    }

    /**
     * exhibit default info
     */
    public void reset() {

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
        requestRenderer.renderHeaders(headers, reqHeaderTable);
        // requestRenderer.renderHeaders(WebUtils.getHeaderText(headers), reqHeaderArea);
        reqHeaderArea.replaceText(WebUtils.getHeaderText(headers));

        // display query-params if exist
        String query = request.getUrl().getQuery();
        Map<String, String> queryParams = WebUtils.parseQueryParams(query);
        if (!queryParams.isEmpty()) {
            StringBuilder queryBuilder = new StringBuilder();
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                queryBuilder.append(entry.getKey());
                queryBuilder.append(": ");
                queryBuilder.append(entry.getValue());
                queryBuilder.append("\n");
            }
            // requestRenderer.renderHeaders(queryBuilder.toString(), reqParamArea);
            reqParamArea.replaceText(queryBuilder.toString());
        }

        // display request content
        byte[] content = WebUtils.parseContent(request.getHeaders(), request.getBody());
        String contentStr = new String(content, StandardCharsets.UTF_8);
        // requestRenderer.renderContent(contentStr, reqPayloadArea);
        reqPayloadArea.replaceText(contentStr);

        boolean hasQuery = !queryParams.isEmpty();
        boolean hasContent = content.length > 0;
        // TODO bug-fix
        System.out.printf("hasQuery: %s, hasContent: %s\n", hasQuery, hasContent);
        SingleSelectionModel<Tab> selectionModel = reqPayloadTabPane.getSelectionModel();

        String title = "Payload";
        if (hasQuery && hasContent) {
            reqPayloadTabPane.setTabMaxHeight(20);
            reqPayloadTabPane.setTabMinHeight(20);
        } else if (hasQuery) {
            selectionModel.clearAndSelect(1);
            title = "Query Parameters";
        } else if (hasContent) {
            selectionModel.clearAndSelect(0);
            // TODO form-data
            title = "Content";
        } else {
            reqPayloadTabPane.setTabMaxHeight(0);
        }
        reqPayloadPane.setText(title);

        displayOverView(request);
        displayResponse(request.getResponse());
    }

    public void displayResponse(ResponseMessage response) {
        if (response == null) {
            // requestRenderer.renderContent("<Waiting For Response...>", respContentArea);
            respContentArea.replaceText("<Waiting For Response...>");
            return;
        }
        // headers
        Map<String, String> headers = response.getHeaders();
        requestRenderer.renderHeaders(headers, respHeaderTable);
        // requestRenderer.renderHeaders(WebUtils.getHeaderText(headers), respHeaderArea);
        respHeaderArea.replaceText(WebUtils.getHeaderText(headers));

        // content
        String contentTypeHeader = response.getHeaders().get("Content-Type");
        String mimeType = null;
        Charset charset = null;
        if (StringUtils.isNotBlank(contentTypeHeader)) {
            ContentType contentType = ContentType.parse(contentTypeHeader);
            mimeType = contentType.getMimeType();
            charset = contentType.getCharset();
        }

        byte[] parsedContent = WebUtils.parseContent(response.getHeaders(), response.getContent());
        if (StringUtils.isNotBlank(mimeType) && mimeType.startsWith("image/")) {
            respContentArea.setVisible(false);
            respImageView.setVisible(true);
            respImageView.setImage(new ByteArrayInputStream(parsedContent));
        } else {
            respContentArea.setVisible(true);
            respImageView.setVisible(false);
            String contentStr = new String(parsedContent,  charset == null ? StandardCharsets.UTF_8: charset);
            // requestRenderer.renderContent(contentStr, respContentArea);
            respContentArea.replaceText(contentStr);
        }
    }

    public void displayOverView(RequestMessage request) {
        String protocol = request.getProtocol();
        String url = request.getRequestUrl();
        String method = request.getMethod();
        String title = String.format("%s %s %s", protocol, url, method);
        String code = request.getResponse() == null ? "Waiting" : String.valueOf(request.getResponse().getStatus());

        String cont = title + "\n" + code;
        requestRenderer.renderContent(cont, overviewArea);
    }

    /**
     * TODO switch to display parsed query
     */
    public void displayParsedQuery(ActionEvent event) {

    }

    public void displayOriginQuery(ActionEvent event) {

    }
}
