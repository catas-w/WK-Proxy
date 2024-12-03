package com.catas.wicked.common.worker.worker;

import com.catas.wicked.common.bean.message.QuitMessage;
import com.catas.wicked.common.bean.message.RetryMessage;
import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.config.SystemProxyConfig;
import com.catas.wicked.common.constant.ServerStatus;
import com.catas.wicked.common.constant.SystemProxyStatus;
import com.catas.wicked.common.executor.ThreadPoolService;
import com.catas.wicked.common.pipeline.MessageQueue;
import com.catas.wicked.common.pipeline.Topic;
import com.catas.wicked.common.provider.SysProxyProvider;
import com.catas.wicked.common.worker.AbstractScheduledWorker;
import io.micronaut.core.util.CollectionUtils;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.List;


@Slf4j
@Singleton
public class SystemProxyWorker extends AbstractScheduledWorker {

    @Inject
    private ApplicationConfig appConfig;

    @Inject
    private SysProxyProvider proxyProvider;

    @Inject
    private MessageQueue messageQueue;

    @PostConstruct
    public void init() {
        messageQueue.subscribe(Topic.SET_SYS_PROXY, msg -> {
            log.info("Force updating sysProxy");
            // quit
            if (msg instanceof QuitMessage) {
                // System.out.println("clear proxy!!!");
                if (appConfig.getSettings().isSystemProxy()) {
                    proxyProvider.clearSysProxy();
                }
                return;
            }

            // refresh
            boolean res = invoke();
            if (!res && msg instanceof RetryMessage retryMessage) {
                retryMessage.reduce();
                ThreadPoolService.getInstance().run(() -> {
                    log.info("retry force update sysProxy: " + msg);
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ignored) {}
                    messageQueue.pushMsg(Topic.SET_SYS_PROXY, msg);
                });
            }
        });
    }

    @Override
    protected boolean doWork(boolean manually) {
        // server is not running
        if (appConfig.getObservableConfig().getServerStatus() != ServerStatus.RUNNING) {
            log.warn("Server is not running");
            appConfig.getObservableConfig().setSystemProxyStatus(SystemProxyStatus.DISABLED);
            return false;
        }

        if (manually) {
            log.info("Manually invoke systemProxyWorker");
            forceUpdateSysProxy();
        } else {
            autoUpdateSysProxy();
        }
        return true;
    }

    private void autoUpdateSysProxy() {
        // sysProxy off
        if (!appConfig.getSettings().isSystemProxy()) {
            appConfig.getObservableConfig().setSystemProxyStatus(SystemProxyStatus.OFF);
            return;
        }

        // sysProxy on
        boolean isConsistent = true;
        List<SystemProxyConfig> configList = proxyProvider.getSysProxyConfig();
        for (SystemProxyConfig config : configList) {
            if (!config.isEnabled() || !appConfig.getHost().equals(config.getServer())
                    || config.getPort() != appConfig.getSettings().getPort()) {
                isConsistent = false;
                break;
            }
        }
        if (!isConsistent) {
            log.warn("Os system proxy is not consistent with settings.");
            appConfig.getObservableConfig().setSystemProxyStatus(SystemProxyStatus.SUSPENDED);
        }
    }

    private void forceUpdateSysProxy() {
        proxyProvider.setSysProxyConfig();
        SystemProxyStatus status = appConfig.getSettings().isSystemProxy() ? SystemProxyStatus.ON : SystemProxyStatus.OFF;
        appConfig.getObservableConfig().setSystemProxyStatus(status);

        // update bypass domains
        List<String> targetList = appConfig.getSettings().getSysProxyBypassList();
        if (!CollectionUtils.isEmpty(targetList)) {
            // List<String> originList = proxyProvider.getBypassDomains();
            // List<String> finalList = new ArrayList<>(originList);
            // finalList.addAll(targetList);
            proxyProvider.setBypassDomains(targetList);
        }
    }

    @Override
    public long getDelay() {
        return 5 * 1000;
    }

    @Override
    public long getInitDelay() {
        return 5 * 1000;
    }
}
