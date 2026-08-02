package com.catas.wicked.common.bean.message;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@EqualsAndHashCode(callSuper = true)
@Data
public class RenderMessage extends BaseMessage {

    public final static String EMPTY_MSG = "_EMPTY_";

    public final static String PATH_MSG = "PATH_";
    public final static String APPLICATION_MSG = "APPLICATION_";
    public final static String APPLICATION_HOST_MSG = "APPLICATION_HOST_";

    private String requestId;

    private Tab targetTab;

    private transient long renderGeneration;

    private boolean isEmpty;

    private boolean isPath;
    private boolean isApplication;
    private boolean isApplicationHost;

    public RenderMessage() {
    }

    public RenderMessage(String requestId, Tab tab) {
        this(requestId, tab, 0);
    }

    public RenderMessage(String requestId, Tab tab, long renderGeneration) {
        this.requestId = requestId;
        this.targetTab = tab;
        this.renderGeneration = renderGeneration;
        this.isEmpty = StringUtils.equals(requestId, EMPTY_MSG);
        this.isPath = requestId.startsWith(PATH_MSG);
        this.isApplicationHost = requestId.startsWith(APPLICATION_HOST_MSG);
        this.isApplication = !isApplicationHost && requestId.startsWith(APPLICATION_MSG);
    }

    public boolean isApplicationGroup() {
        return isApplication || isApplicationHost;
    }

    public static boolean isOverviewOnly(String requestId) {
        return requestId != null && (requestId.startsWith(PATH_MSG)
                || requestId.startsWith(APPLICATION_MSG)
                || requestId.startsWith(APPLICATION_HOST_MSG));
    }

    @Getter
    public enum Tab {

        EMPTY(0),
        OVERVIEW(1),
        REQUEST(0),
        RESPONSE(2),
        TIMING(3),
        COOKIE(4);

        private final int order;

        Tab(int i) {
            this.order = i;
        }

        public static Tab valueOfIgnoreCase(String value) {
            if (value == null) {
                return null;
            }

            String strip = value.strip();
            for (Tab tab : Tab.values()) {
                if (StringUtils.equalsIgnoreCase(tab.name(), strip)) {
                    return tab;
                }
            }
            return null;
        }
    }
}
