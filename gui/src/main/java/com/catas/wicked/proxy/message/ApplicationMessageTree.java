package com.catas.wicked.proxy.message;

import com.catas.wicked.common.bean.RequestCell;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.proxy.gui.componet.FilterableTreeItem;
import com.catas.wicked.proxy.gui.controller.RequestViewController;
import javafx.application.Platform;
import javafx.scene.control.TreeItem;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Maintains the independent application -> host -> request view. */
public class ApplicationMessageTree {

    private static final String KEY_SEPARATOR = "\u0000";

    private final Map<String, ApplicationGroup> applications = new LinkedHashMap<>();
    private final Map<String, RequestEntry> requests = new LinkedHashMap<>();
    private final RequestViewController controller;

    public ApplicationMessageTree(RequestViewController controller) {
        this.controller = controller;
    }

    public synchronized void add(RequestMessage message) {
        if (message == null || StringUtils.isBlank(message.getRequestId()) || requests.containsKey(message.getRequestId())) {
            return;
        }
        ApplicationSource identity = ApplicationSource.from(message.getProcessInfo());
        ApplicationGroup application = applications.computeIfAbsent(identity.key(), key -> createApplication(identity));
        String host = hostOf(message);
        HostGroup hostGroup = application.hosts.computeIfAbsent(host, key -> createHost(application, host));
        RequestEntry entry = createRequest(application, hostGroup, message);
        hostGroup.requests.put(message.getRequestId(), entry);
        requests.put(message.getRequestId(), entry);

        Platform.runLater(() -> {
            incrementCounts(application, hostGroup);
            attachApplication(application);
            attachHost(application, hostGroup);
            hostGroup.item.getInternalChildren().add(entry.item);
        });
    }

    public synchronized void update(RequestMessage message, boolean restoreSelection) {
        if (message == null || StringUtils.isBlank(message.getRequestId())) {
            return;
        }
        RequestEntry current = requests.get(message.getRequestId());
        if (current == null) {
            add(message);
            return;
        }
        ApplicationSource identity = ApplicationSource.from(message.getProcessInfo());
        if (StringUtils.equals(current.application.key, identity.key())) {
            Platform.runLater(() -> {
                synchronized (ApplicationMessageTree.this) {
                    if (applications.get(identity.key()) == current.application) {
                        updateApplicationCell(current.application, identity);
                    }
                }
            });
            return;
        }

        removeInternal(current);
        add(message);
        if (restoreSelection) {
            Platform.runLater(() -> select(message.getRequestId()));
        }
    }

    public synchronized void remove(Collection<String> requestIds) {
        if (requestIds == null || requestIds.isEmpty()) {
            return;
        }
        for (String requestId : new ArrayList<>(requestIds)) {
            RequestEntry entry = requests.get(requestId);
            if (entry != null) {
                removeInternal(entry);
            }
        }
    }

    public synchronized Set<String> requestIds(RequestCell cell) {
        if (cell == null) {
            return Collections.emptySet();
        }
        if (cell.getNodeType() == RequestCell.NodeType.REQUEST) {
            return StringUtils.isBlank(cell.getRequestId())
                    ? Collections.emptySet() : Set.of(cell.getRequestId());
        }
        if (cell.getNodeType() == RequestCell.NodeType.APPLICATION) {
            ApplicationGroup application = applications.get(cell.getNodeKey());
            return application == null ? Collections.emptySet() : collect(application.hosts.values());
        }
        if (cell.getNodeType() == RequestCell.NodeType.HOST) {
            int separator = cell.getNodeKey().indexOf(KEY_SEPARATOR);
            if (separator < 0) {
                return Collections.emptySet();
            }
            ApplicationGroup application = applications.get(cell.getNodeKey().substring(0, separator));
            HostGroup host = application == null ? null : application.hosts.get(cell.getNodeKey().substring(separator + 1));
            return host == null ? Collections.emptySet() : new LinkedHashSet<>(host.requests.keySet());
        }
        return Collections.emptySet();
    }

    synchronized ApplicationGroupSnapshot snapshot(RequestCell.NodeType nodeType, String nodeKey) {
        if (nodeType == RequestCell.NodeType.APPLICATION) {
            ApplicationGroup application = applications.get(nodeKey);
            if (application == null) {
                return null;
            }
            RequestCell cell = application.item.getValue();
            return new ApplicationGroupSnapshot(nodeType, nodeKey, cell.getPath(), null,
                    cell.getProcessInfo(), new LinkedHashSet<>(application.hosts.keySet()),
                    collect(application.hosts.values()));
        }
        if (nodeType == RequestCell.NodeType.HOST) {
            int separator = StringUtils.defaultString(nodeKey).indexOf(KEY_SEPARATOR);
            if (separator < 0) {
                return null;
            }
            ApplicationGroup application = applications.get(nodeKey.substring(0, separator));
            String hostName = nodeKey.substring(separator + 1);
            HostGroup host = application == null ? null : application.hosts.get(hostName);
            if (host == null) {
                return null;
            }
            RequestCell applicationCell = application.item.getValue();
            return new ApplicationGroupSnapshot(nodeType, nodeKey, applicationCell.getPath(), hostName,
                    applicationCell.getProcessInfo(), Set.of(hostName),
                    new LinkedHashSet<>(host.requests.keySet()));
        }
        return null;
    }

    public synchronized TreeItem<RequestCell> item(String requestId) {
        RequestEntry entry = requests.get(requestId);
        return entry == null ? null : entry.item;
    }

    public void select(String requestId) {
        TreeItem<RequestCell> item = item(requestId);
        if (item == null) {
            return;
        }
        expandParents(item);
        controller.getReqApplicationTreeView().getSelectionModel().select(item);
    }

    public synchronized void clear() {
        applications.clear();
        requests.clear();
        Platform.runLater(() -> root().getInternalChildren().clear());
    }

    private ApplicationGroup createApplication(ApplicationSource identity) {
        RequestCell cell = new RequestCell(identity.displayName(), "");
        cell.setLeaf(false);
        cell.setNodeType(RequestCell.NodeType.APPLICATION);
        cell.setNodeKey(identity.key());
        applyIdentity(cell, identity);
        return new ApplicationGroup(identity.key(), new FilterableTreeItem<>(cell));
    }

    private HostGroup createHost(ApplicationGroup application, String host) {
        RequestCell cell = new RequestCell(host, "");
        cell.setLeaf(false);
        cell.setNodeType(RequestCell.NodeType.HOST);
        cell.setNodeKey(application.key + KEY_SEPARATOR + host);
        cell.setSearchText(application.item.getValue().getSearchText() + " " + host);
        return new HostGroup(host, new FilterableTreeItem<>(cell));
    }

    private RequestEntry createRequest(ApplicationGroup application, HostGroup host, RequestMessage message) {
        RequestCell cell = new RequestCell(pathOf(message), message.getMethod());
        cell.setLeaf(true);
        cell.setNodeType(RequestCell.NodeType.REQUEST);
        cell.setNodeKey(message.getRequestId());
        cell.setRequestId(message.getRequestId());
        cell.setFullPath(message.getRequestUrl());
        cell.setSearchText(application.item.getValue().getSearchText() + " " + host.host + " "
                + StringUtils.defaultString(message.getMethod()) + " " + StringUtils.defaultString(message.getRequestUrl()));
        return new RequestEntry(message.getRequestId(), application, host, new FilterableTreeItem<>(cell));
    }

    private void removeInternal(RequestEntry entry) {
        requests.remove(entry.requestId);
        entry.host.requests.remove(entry.requestId);
        boolean removeHost = entry.host.requests.isEmpty();
        boolean removeApplication = removeHost && entry.application.hosts.size() == 1;
        if (removeHost) {
            entry.application.hosts.remove(entry.host.host);
        }
        if (entry.application.hosts.isEmpty()) {
            applications.remove(entry.application.key);
            removeApplication = true;
        }

        boolean finalRemoveApplication = removeApplication;
        Platform.runLater(() -> {
            decrementCounts(entry.application, entry.host);
            entry.host.item.getInternalChildren().remove(entry.item);
            if (entry.host.requests.isEmpty()) {
                entry.application.item.getInternalChildren().remove(entry.host.item);
            }
            if (finalRemoveApplication) {
                root().getInternalChildren().remove(entry.application.item);
            }
        });
    }

    private void incrementCounts(ApplicationGroup application, HostGroup host) {
        application.item.getValue().setCount(application.item.getValue().getCount() + 1);
        host.item.getValue().setCount(host.item.getValue().getCount() + 1);
    }

    private void decrementCounts(ApplicationGroup application, HostGroup host) {
        application.item.getValue().setCount(Math.max(0, application.item.getValue().getCount() - 1));
        host.item.getValue().setCount(Math.max(0, host.item.getValue().getCount() - 1));
    }

    private void attachApplication(ApplicationGroup application) {
        FilterableTreeItem<RequestCell> root = root();
        if (!root.getInternalChildren().contains(application.item)) {
            root.getInternalChildren().add(application.item);
        }
    }

    private FilterableTreeItem<RequestCell> root() {
        return controller.getApplicationTreeRoot();
    }

    private void attachHost(ApplicationGroup application, HostGroup host) {
        if (!application.item.getInternalChildren().contains(host.item)) {
            application.item.getInternalChildren().add(host.item);
        }
    }

    private void updateApplicationCell(ApplicationGroup application, ApplicationSource identity) {
        applyIdentity(application.item.getValue(), identity);
        String prefix = application.item.getValue().getSearchText();
        application.hosts.values().forEach(host -> {
            host.item.getValue().setSearchText(prefix + " " + host.host);
            host.requests.values().forEach(request -> request.item.getValue().setSearchText(
                    prefix + " " + host.host + " " + request.item.getValue().getMethod() + " "
                            + request.item.getValue().getFullPath()));
        });
    }

    private static void applyIdentity(RequestCell cell, ApplicationSource identity) {
        cell.setPath(identity.displayName());
        cell.setSecondaryText(identity.secondaryText());
        cell.setStatusText(identity.statusText());
        cell.setProcessInfo(identity.processInfo());
        cell.setSearchText(identity.displayName() + " " + identity.secondaryText());
    }

    private static String hostOf(RequestMessage message) {
        if (StringUtils.isNotBlank(message.getRemoteHost())) {
            return message.getRemoteHost();
        }
        try {
            String host = new URI(message.getRequestUrl()).getHost();
            return StringUtils.defaultIfBlank(host, "Unknown Host");
        } catch (URISyntaxException | NullPointerException e) {
            return "Unknown Host";
        }
    }

    private static String pathOf(RequestMessage message) {
        try {
            URI uri = new URI(message.getRequestUrl());
            String path = StringUtils.defaultIfBlank(uri.getRawPath(), "/");
            return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
        } catch (URISyntaxException | NullPointerException e) {
            return StringUtils.defaultString(message.getRequestUrl(), "<Unknown>");
        }
    }

    private static Set<String> collect(Collection<HostGroup> hosts) {
        Set<String> ids = new LinkedHashSet<>();
        hosts.forEach(host -> ids.addAll(host.requests.keySet()));
        return ids;
    }

    private static void expandParents(TreeItem<RequestCell> item) {
        TreeItem<RequestCell> parent = item.getParent();
        while (parent != null) {
            parent.setExpanded(true);
            parent = parent.getParent();
        }
    }

    private static final class ApplicationGroup {
        private final String key;
        private final FilterableTreeItem<RequestCell> item;
        private final Map<String, HostGroup> hosts = new LinkedHashMap<>();

        private ApplicationGroup(String key, FilterableTreeItem<RequestCell> item) {
            this.key = key;
            this.item = item;
        }
    }

    private static final class HostGroup {
        private final String host;
        private final FilterableTreeItem<RequestCell> item;
        private final Map<String, RequestEntry> requests = new LinkedHashMap<>();

        private HostGroup(String host, FilterableTreeItem<RequestCell> item) {
            this.host = host;
            this.item = item;
        }
    }

    private record RequestEntry(String requestId, ApplicationGroup application, HostGroup host,
                                FilterableTreeItem<RequestCell> item) {}
}
