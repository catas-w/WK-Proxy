package com.catas.wicked.common.worker.worker;

import com.catas.wicked.common.util.SystemUtils;
import com.catas.wicked.common.worker.AbstractScheduledWorker;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

@Slf4j
@Singleton
public class FilePurgeWorker extends AbstractScheduledWorker {

    public static final String FILE_PATTERN = "temp_.+";

    @Override
    protected boolean doWork(boolean manually) {
        // purge temp files matching the pattern
        log.info("Starting FilePurgeWorker to purge temporary files matching pattern: {}", FILE_PATTERN);
        try {
            purgeTempFiles(FILE_PATTERN);
            log.info("FilePurgeWorker completed successfully.");
        } catch (Exception e) {
            log.error("Error in FilePurgeWorker", e);
        }
        return true;
    }

    @Override
    public long getDelay() {
        return 20 * 60 * 1000;
    }

    @Override
    public long getInitDelay() {
        return 5 * 1000;
    }

    private void purgeTempFiles(String pattern) {
        Path path = SystemUtils.getStoragePath("");
        try {
            // Logic to find and delete files matching the pattern in the specified path
            Files.list(path)
                .filter(file -> file.getFileName().toString().matches(pattern))
                .peek(file -> log.info("Found file: {}", file))
                .filter(file -> {
                    // delete file if created 60 min ago
                    try {
                        FileTime lastModifiedTime = Files.getLastModifiedTime(file);
                        return lastModifiedTime.toMillis() < System.currentTimeMillis() - 60 * 60 * 1000;
                    } catch (IOException e) {
                        log.error("Failed to get last modified time for file.", e);
                        return false;
                    }
                })
                .forEach(file -> {
                    try {
                        Files.delete(file);
                        log.info("Deleted temporary file: {}", file);
                    } catch (IOException e) {
                        log.error("Failed to delete file: {}", file, e);
                    }
                });
        } catch (Exception e) {
            log.error("Error while purging temporary files", e);
        }
    }
}
