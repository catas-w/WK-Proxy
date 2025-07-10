package com.catas.wicked.proxy.event;

import com.catas.wicked.common.bean.message.OutputMessage;
import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.pipeline.MessageQueue;
import com.catas.wicked.common.pipeline.Topic;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

@Slf4j
public class OutputFileEventHandler<T extends Event> implements EventHandler<T> {

    private final OutputMessage.Source source;

    private final MessageQueue messageQueue;

    private final ApplicationConfig appConfig;

    private final Supplier<Window> windowSupplier;

    @Setter
    private FileChooser fileChooser;

    @Setter
    private Supplier<String> initialFileNameSupplier;

    public OutputFileEventHandler(OutputMessage.Source source,
                                  MessageQueue messageQueue,
                                  ApplicationConfig appConfig,
                                  Supplier<Window> windowSupplier) {
        this.source = source;
        this.messageQueue = messageQueue;
        this.appConfig = appConfig;
        this.windowSupplier = windowSupplier;
    }

    @Override
    public void handle(T event) {
        log.info("Handling output file event: {}", event);

        if (fileChooser == null) {
            fileChooser = new FileChooser();
            fileChooser.setTitle("Save as...");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Text Files", "*.txt"),
                    new FileChooser.ExtensionFilter("Json Files", "*.json"),
                    new FileChooser.ExtensionFilter("All Files", "*.*")
            );
        }

        Window window = windowSupplier.get();
        if (window == null) {
            log.warn("No window available for file chooser dialog");
            return;
        }

        if (initialFileNameSupplier != null) {
            String initialFileName = initialFileNameSupplier.get();
            log.info("initial name: {}", initialFileName);
            if (initialFileName != null && !initialFileName.isBlank()) {
                fileChooser.setInitialFileName(initialFileName);
            }
        }

        var file = fileChooser.showSaveDialog(windowSupplier.get());
        if (file != null) {
            // Create and send the output message
            log.info("Saving content to file: {}", file.getAbsolutePath());
            OutputMessage outputMessage = OutputMessage.builder()
                    .requestId(appConfig.getObservableConfig().getCurrentRequestId())
                    .source(source)
                    .targetFile(file)
                    .build();
            messageQueue.pushMsg(Topic.DATA_OUTPUT, outputMessage);
        }
    }
}
