package com.catas.wicked.proxy.service.icon;

import com.catas.wicked.common.bean.ProcessInfo;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import javafx.scene.image.Image;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ArrayBlockingQueue;

@Slf4j
@Singleton
public class ApplicationIconService {

    static final int CACHE_CAPACITY = 128;
    static final int QUEUE_CAPACITY = 64;

    private final ApplicationIconProvider nativeProvider;
    private final ApplicationIconProvider bundledProvider;
    private final ThreadPoolExecutor executor;
    private final Map<String, ApplicationIconData> cache;
    private final Map<String, Image> imageCache;
    private final Map<String, Boolean> misses;
    private final Map<String, CompletableFuture<Optional<ApplicationIconData>>> inFlight = new LinkedHashMap<>();

    public ApplicationIconService() {
        this(NativeApplicationIconProvider.create(), new BundledApplicationIconProvider(), createExecutor());
    }

    ApplicationIconService(ApplicationIconProvider nativeProvider,
                           ApplicationIconProvider bundledProvider,
                           ThreadPoolExecutor executor) {
        this.nativeProvider = nativeProvider;
        this.bundledProvider = bundledProvider;
        this.executor = executor;
        this.cache = lruMap();
        this.imageCache = lruMap();
        this.misses = lruMap();
    }

    private static <T> Map<String, T> lruMap() {
        return new LinkedHashMap<>(CACHE_CAPACITY, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, T> eldest) {
                return size() > CACHE_CAPACITY;
            }
        };
    }

    public CompletableFuture<Optional<Image>> load(ProcessInfo processInfo) {
        if (!isFound(processInfo)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        String key = cacheKey(processInfo);
        synchronized (this) {
            Image cachedImage = imageCache.get(key);
            if (cachedImage != null) {
                return CompletableFuture.completedFuture(Optional.of(cachedImage));
            }
        }
        return loadData(processInfo).thenApply(data -> data.map(iconData -> {
            synchronized (this) {
                Image cachedImage = imageCache.get(key);
                if (cachedImage != null) {
                    return cachedImage;
                }
                Image image = iconData.toImage();
                imageCache.put(key, image);
                return image;
            }
        }));
    }

    CompletableFuture<Optional<ApplicationIconData>> loadData(ProcessInfo processInfo) {
        if (!isFound(processInfo)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        String key = cacheKey(processInfo);
        synchronized (this) {
            ApplicationIconData cached = cache.get(key);
            if (cached != null) {
                return CompletableFuture.completedFuture(Optional.of(cached));
            }
            if (misses.containsKey(key)) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            CompletableFuture<Optional<ApplicationIconData>> pending = inFlight.get(key);
            if (pending != null) {
                return pending;
            }
            CompletableFuture<Optional<ApplicationIconData>> future = new CompletableFuture<>();
            inFlight.put(key, future);
            try {
                executor.execute(() -> resolve(key, processInfo, future));
            } catch (RejectedExecutionException e) {
                inFlight.remove(key);
                future.complete(Optional.empty());
            }
            return future;
        }
    }

    private void resolve(String key, ProcessInfo processInfo,
                         CompletableFuture<Optional<ApplicationIconData>> future) {
        Optional<ApplicationIconData> result = Optional.empty();
        try {
            result = safeLoad(nativeProvider, processInfo).or(() -> safeLoad(bundledProvider, processInfo));
            result.ifPresent(image -> {
                synchronized (this) {
                    cache.put(key, image);
                    misses.remove(key);
                }
            });
            if (result.isEmpty()) {
                synchronized (this) {
                    misses.put(key, Boolean.TRUE);
                }
            }
        } catch (LinkageError | RuntimeException e) {
            log.debug("Unable to resolve application icon for {}", key, e);
        } finally {
            synchronized (this) {
                inFlight.remove(key);
            }
            future.complete(result);
        }
    }

    private Optional<ApplicationIconData> safeLoad(ApplicationIconProvider provider, ProcessInfo processInfo) {
        try {
            return provider.load(processInfo);
        } catch (LinkageError | RuntimeException e) {
            log.debug("Application icon provider failed", e);
            return Optional.empty();
        }
    }

    static String cacheKey(ProcessInfo info) {
        return cacheKey(info, System.getProperty("os.name", ""));
    }

    static String cacheKey(ProcessInfo info, String osName) {
        String path = StringUtils.firstNonBlank(info.getApplicationExecutablePath(), info.getOwnerExecutablePath());
        if (StringUtils.isNotBlank(path)) {
            String normalized = path.replace('\\', '/');
            if (osName.toLowerCase(Locale.ROOT).contains("win")) {
                normalized = normalized.toLowerCase(Locale.ROOT);
            }
            return "path:" + normalized;
        }
        return "name:" + StringUtils.defaultString(
                StringUtils.firstNonBlank(info.getApplicationName(), info.getOwnerProcessName()), "unknown")
                .toLowerCase(Locale.ROOT);
    }

    private static boolean isFound(ProcessInfo info) {
        return info != null && info.getLookupStatus() == ProcessInfo.LookupStatus.FOUND;
    }

    private static ThreadPoolExecutor createExecutor() {
        ThreadPoolExecutor result = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY), runnable -> {
                    Thread thread = new Thread(runnable, "application-icon-lookup");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        result.allowCoreThreadTimeOut(false);
        return result;
    }

    @PreDestroy
    void close() {
        executor.shutdownNow();
    }
}
