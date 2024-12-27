package com.catas.wicked.common.provider;

import java.io.IOException;

public interface DesktopProvider {

    void browseOnLocal(String uri) throws IOException, InterruptedException;
}
