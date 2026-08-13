package com.catas.wicked.common.bean;

import io.netty.handler.codec.http.HttpMethod;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
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

    public enum TransferState {
        PENDING,
        SUCCESS,
        FAILED
    }

    private String requestId;

    private String path;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private transient StringProperty pathProperty;

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
    private transient StringProperty secondaryTextProperty;

    private String statusText;

    private TransferState transferState = TransferState.PENDING;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private transient ObjectProperty<TransferState> transferStateProperty;

    private String transferStatusKey = "request-status.pending";

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private transient StringProperty transferStatusKeyProperty;

    private String transferStatusDetail;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private transient StringProperty transferStatusDetailProperty;

    /** GUI-only source metadata used to resolve an application icon locally. */
    private transient ProcessInfo processInfo;

    private int count;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private transient IntegerProperty countProperty;

    private int failedCount;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private transient IntegerProperty failedCountProperty;

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
        return pathProperty == null ? path : pathProperty.get();
    }

    public void setPath(String path) {
        this.path = path;
        if (pathProperty != null) {
            pathProperty.set(path);
        }
    }

    public StringProperty pathProperty() {
        if (pathProperty == null) {
            pathProperty = new SimpleStringProperty(this, "path", path);
        }
        return pathProperty;
    }

    public String getSecondaryText() {
        return secondaryTextProperty == null ? secondaryText : secondaryTextProperty.get();
    }

    public void setSecondaryText(String secondaryText) {
        this.secondaryText = secondaryText;
        if (secondaryTextProperty != null) {
            secondaryTextProperty.set(secondaryText);
        }
    }

    public StringProperty secondaryTextProperty() {
        if (secondaryTextProperty == null) {
            secondaryTextProperty = new SimpleStringProperty(this, "secondaryText", secondaryText);
        }
        return secondaryTextProperty;
    }

    public TransferState getTransferState() {
        return transferStateProperty == null ? transferState : transferStateProperty.get();
    }

    public void setTransferState(TransferState transferState) {
        TransferState target = transferState == null ? TransferState.PENDING : transferState;
        this.transferState = target;
        if (transferStateProperty != null) {
            transferStateProperty.set(target);
        }
    }

    public ObjectProperty<TransferState> transferStateProperty() {
        if (transferStateProperty == null) {
            transferStateProperty = new SimpleObjectProperty<>(this, "transferState", transferState);
        }
        return transferStateProperty;
    }

    public String getTransferStatusKey() {
        return transferStatusKeyProperty == null ? transferStatusKey : transferStatusKeyProperty.get();
    }

    public void setTransferStatusKey(String transferStatusKey) {
        this.transferStatusKey = transferStatusKey;
        if (transferStatusKeyProperty != null) {
            transferStatusKeyProperty.set(transferStatusKey);
        }
    }

    public StringProperty transferStatusKeyProperty() {
        if (transferStatusKeyProperty == null) {
            transferStatusKeyProperty = new SimpleStringProperty(this, "transferStatusKey", transferStatusKey);
        }
        return transferStatusKeyProperty;
    }

    public String getTransferStatusDetail() {
        return transferStatusDetailProperty == null ? transferStatusDetail : transferStatusDetailProperty.get();
    }

    public void setTransferStatusDetail(String transferStatusDetail) {
        this.transferStatusDetail = transferStatusDetail;
        if (transferStatusDetailProperty != null) {
            transferStatusDetailProperty.set(transferStatusDetail);
        }
    }

    public StringProperty transferStatusDetailProperty() {
        if (transferStatusDetailProperty == null) {
            transferStatusDetailProperty = new SimpleStringProperty(this, "transferStatusDetail", transferStatusDetail);
        }
        return transferStatusDetailProperty;
    }

    public void applyTransferStatus(TransferState state, String statusKey, String detail) {
        TransferState target = state == null ? TransferState.PENDING : state;
        if (target.ordinal() < getTransferState().ordinal()) {
            return;
        }
        setTransferState(target);
        setTransferStatusKey(statusKey);
        setTransferStatusDetail(detail);
    }

    public int getCount() {
        return countProperty == null ? count : countProperty.get();
    }

    public void setCount(int count) {
        this.count = count;
        if (countProperty != null) {
            countProperty.set(count);
        }
    }

    public IntegerProperty countProperty() {
        if (countProperty == null) {
            countProperty = new SimpleIntegerProperty(this, "count", count);
        }
        return countProperty;
    }

    public int getFailedCount() {
        return failedCountProperty == null ? failedCount : failedCountProperty.get();
    }

    public void setFailedCount(int failedCount) {
        int target = Math.max(0, failedCount);
        this.failedCount = target;
        if (failedCountProperty != null) {
            failedCountProperty.set(target);
        }
    }

    public IntegerProperty failedCountProperty() {
        if (failedCountProperty == null) {
            failedCountProperty = new SimpleIntegerProperty(this, "failedCount", failedCount);
        }
        return failedCountProperty;
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
