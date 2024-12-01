package com.catas.wicked.common.config;

import com.catas.wicked.common.constant.ServerStatus;
import com.catas.wicked.common.constant.SystemProxyStatus;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AppObservableConfig {

    /**
     * status of proxy server
     */
    private final SimpleObjectProperty<ServerStatus> serverStatus = new SimpleObjectProperty<>();

    private final SimpleObjectProperty<SystemProxyStatus> systemProxyStatus = new SimpleObjectProperty<>();

    private final SimpleStringProperty currentRequestId = new SimpleStringProperty(null);

    private final SimpleBooleanProperty certInstalledStatus = new SimpleBooleanProperty(true);

    public AppObservableConfig() {
    }

    public ServerStatus getServerStatus() {
        return serverStatus.get();
    }

    public SimpleObjectProperty<ServerStatus> serverStatusProperty() {
        return serverStatus;
    }

    public void setServerStatus(ServerStatus serverStatus) {
        this.serverStatus.set(serverStatus);
    }

    public SystemProxyStatus getSystemProxyStatus() {
        return systemProxyStatus.get();
    }

    public SimpleObjectProperty<SystemProxyStatus> systemProxyStatusProperty() {
        return systemProxyStatus;
    }

    public void setSystemProxyStatus(SystemProxyStatus systemProxyStatus) {
        this.systemProxyStatus.set(systemProxyStatus);
    }

    public String getCurrentRequestId() {
        return currentRequestId.get();
    }

    public SimpleStringProperty currentRequestIdProperty() {
        return currentRequestId;
    }

    public void setCurrentRequestId(String currentRequestId) {
        this.currentRequestId.set(currentRequestId);
    }

    public boolean isCertInstalledStatus() {
        return certInstalledStatus.get();
    }

    public SimpleBooleanProperty certInstalledStatusProperty() {
        return certInstalledStatus;
    }

    public void setCertInstalledStatus(boolean certInstalledStatus) {
        this.certInstalledStatus.set(certInstalledStatus);
    }
}
