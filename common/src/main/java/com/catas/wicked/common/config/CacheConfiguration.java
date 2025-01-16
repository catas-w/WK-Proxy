package com.catas.wicked.common.config;

import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.util.IdUtil;
import com.catas.wicked.common.util.SystemUtils;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.PersistentCacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.EntryUnit;
import org.ehcache.config.units.MemoryUnit;

import java.io.IOException;
import java.nio.file.Path;

@Slf4j
@Factory
public class CacheConfiguration implements AutoCloseable {

    @Bean(preDestroy = "close")
    @Singleton
    public CacheManager cacheManager() throws IOException {
        Path storagePath = SystemUtils.getStoragePath("cache");

        // Create cache manager
        ResourcePoolsBuilder poolsBuilder = ResourcePoolsBuilder.newResourcePoolsBuilder()
                .heap(100, EntryUnit.ENTRIES)
                .offheap(50, MemoryUnit.MB)
                .disk(500, MemoryUnit.MB, true);
        CacheConfigurationBuilder<String, RequestMessage> builder = CacheConfigurationBuilder.newCacheConfigurationBuilder(
                String.class, RequestMessage.class, poolsBuilder);

        PersistentCacheManager requestCache = null;
        try {
            requestCache = CacheManagerBuilder.newCacheManagerBuilder()
                .with(CacheManagerBuilder.persistence(storagePath.toFile()))
                .withCache("requestCache", builder)
                .build(true);
        } catch (Exception e) {
            log.error("Failed to create cache manager with disk persistence", e);
            storagePath = SystemUtils.getStoragePath("cache-" + IdUtil.getId());
            requestCache = CacheManagerBuilder.newCacheManagerBuilder()
                .with(CacheManagerBuilder.persistence(storagePath.toFile()))
                .withCache("requestCache", builder)
                .build(true);
        }

        return requestCache;
    }

    @Bean(preDestroy = "clear")
    @Singleton
    public Cache<String, RequestMessage> requestCache(CacheManager cacheManager) {
        return cacheManager.getCache("requestCache", String.class, RequestMessage.class);
    }

    @PreDestroy
    @Override
    public void close() throws Exception {

    }

    // @Override
    // public void destroy() throws CachePersistenceException {
    //     PersistentCacheManager cacheManager = (PersistentCacheManager) AppContextUtil.getBean("cacheManager");
    //     cacheManager.close();
    //     cacheManager.destroy();
    // }
}
