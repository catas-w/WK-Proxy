package com.catas.wicked.proxy.service.icon;

import com.catas.wicked.common.bean.ProcessInfo;
import java.util.Optional;

@FunctionalInterface
interface ApplicationIconProvider {

    Optional<ApplicationIconData> load(ProcessInfo processInfo);
}
