package com.catas.wicked.common.factory;

import com.catas.wicked.common.provider.DesktopProvider;
import com.catas.wicked.common.util.SystemUtils;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parallel;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.ObjectUtils;


@Parallel
@Factory
public class DesktopProviderFactory {

    @Bean
    @Singleton
    @Requires(os = Requires.Family.MAC_OS)
    public DesktopProvider macDesktopProvider() {
        return uri -> {
            ObjectUtils.requireNonEmpty(uri);
            ProcessBuilder builder = new ProcessBuilder("open", uri);
            SystemUtils.runCommand(builder);
        };
    }

    @Bean
    @Singleton
    @Requires(os = Requires.Family.WINDOWS)
    public DesktopProvider winDesktopProvider() {
        return uri -> {
            ObjectUtils.requireNonEmpty(uri);
            ProcessBuilder builder = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", uri);
            SystemUtils.runCommand(builder);
        };
    }

    @Bean
    @Singleton
    @Requires(os = Requires.Family.LINUX)
    public DesktopProvider linuxDesktopProvider() {
        return uri -> {
            ObjectUtils.requireNonEmpty(uri);
            ProcessBuilder builder = new ProcessBuilder("xdg-open", uri);
            SystemUtils.runCommand(builder);
        };
    }
}
