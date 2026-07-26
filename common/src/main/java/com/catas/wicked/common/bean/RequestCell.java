package com.catas.wicked.common.bean;

import io.netty.handler.codec.http.HttpMethod;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

@Data
public class RequestCell {

    public enum NodeType {
        URL_PATH,
        APPLICATION,
        HOST,
        REQUEST
    }

    private String requestId;

    private String path;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private final transient StringProperty pathProperty = new SimpleStringProperty(this, "path");

    private String fullPath;

    private String method;

    private boolean isLeaf;

    private String styleClass;

    private NodeType nodeType = NodeType.REQUEST;

    private String nodeKey;

    private String secondaryText;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private final transient StringProperty secondaryTextProperty =
            new SimpleStringProperty(this, "secondaryText");

    private String statusText;

    /** GUI-only source metadata used to resolve an application icon locally. */
    private transient ProcessInfo processInfo;

    private int count;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private final transient IntegerProperty countProperty = new SimpleIntegerProperty(this, "count");

    private String searchText;

    /**
     * if Show animation or not
     */
    private boolean isOnCreated;

    private long createdTime;

    private static Map<String, String> styleMap;

    static {
        styleMap = new HashMap<>();
        styleMap.put(HttpMethod.GET.name(), "method-label-get");
        styleMap.put(HttpMethod.POST.name(), "method-label-post");
        styleMap.put(HttpMethod.PUT.name(), "method-label-put");
        styleMap.put(HttpMethod.DELETE.name(), "method-label-delete");
    }

    public String getStyleClass() {
        return styleMap.getOrDefault(method, "");
    }

    public RequestCell(String path, String method) {
        setPath(path);
        this.method = method;
        this.createdTime = System.currentTimeMillis();
    }

    public String getPath() {
        return pathProperty.get();
    }

    public void setPath(String path) {
        this.path = path;
        pathProperty.set(path);
    }

    public StringProperty pathProperty() {
        return pathProperty;
    }

    public String getSecondaryText() {
        return secondaryTextProperty.get();
    }

    public void setSecondaryText(String secondaryText) {
        this.secondaryText = secondaryText;
        secondaryTextProperty.set(secondaryText);
    }

    public StringProperty secondaryTextProperty() {
        return secondaryTextProperty;
    }

    public int getCount() {
        return countProperty.get();
    }

    public void setCount(int count) {
        this.count = count;
        countProperty.set(count);
    }

    public IntegerProperty countProperty() {
        return countProperty;
    }

    public String getMethod() {
        if (StringUtils.isNotBlank(method) && method.length() > 4) {
            return method.substring(0, 3);
        }
        return method;
    }

    public boolean isOnCreated() {
        return System.currentTimeMillis() - createdTime < 100;
    }

    public boolean matchesFilter(String filter) {
        if (StringUtils.isBlank(filter)) {
            return true;
        }
        String candidate = StringUtils.defaultString(searchText,
                StringUtils.defaultString(fullPath, getPath()));
        return candidate.toLowerCase().contains(filter.trim().toLowerCase());
    }
}
