package com.catas.wicked.proxy.gui.componet;

import com.catas.wicked.common.bean.message.OutputMessage;
import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.factory.MessageSourceFactory;
import com.catas.wicked.common.pipeline.MessageQueue;
import com.catas.wicked.common.util.ImageUtils;
import com.catas.wicked.common.webpdecoderjn.WebPDecoder;
import com.catas.wicked.proxy.event.OutputFileEventHandler;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.ActionEvent;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;

/**
 * image view support zoom
 */
@Slf4j
public class ZoomImageView extends ScrollPane {

    private BorderPane borderPane;
    private ImageView imageView;
    private Image image;
    private String url;
    protected String mimeType;
    protected InputStream imageData;
    private static final String STYLE = "zoom-image-view";
    private DoubleProperty zoomProperty = new SimpleDoubleProperty(100);


    public ZoomImageView() {
        this.getStyleClass().add(STYLE);
        AnchorPane.setLeftAnchor(this, 0.0);
        AnchorPane.setRightAnchor(this, 0.0);
        AnchorPane.setTopAnchor(this, 0.0);
        AnchorPane.setBottomAnchor(this, 0.0);
        // setContextMenu(contextMenu);

        // image = new Image("/image/start.jpg");
        imageView = new ImageView();
        imageView.setPreserveRatio(true);

        borderPane = new BorderPane();
        borderPane.setCenter(imageView);
        this.setContent(borderPane);

        borderPane.prefWidthProperty().bind(this.widthProperty().subtract(4.0));
        borderPane.prefHeightProperty().bind(this.heightProperty().subtract(4.0));
        // init();

        zoomWithScroll();
    }

    public void initContextMenu(MessageQueue messageQueue, ApplicationConfig appConfig, OutputMessage.Source source) {
        this.setContextMenu(new ImageViewContextMenu(this, messageQueue, appConfig, source));
    }

    public Image getImage() {
        return image;
    }

    public void setImage(Image image, String mimeType) {
        this.image = image;
        this.mimeType = mimeType;
        init();
    }

    public void setImage(InputStream inputStream, String mimeType) throws IOException {
        // webp format
        if (StringUtils.equals(mimeType, "image/webp")) {
            WebPDecoder.SimpleImageInfo imageInfo = WebPDecoder.decode2(inputStream.readAllBytes());
            this.image = ImageUtils.getJFXImage(imageInfo);
        } else {
            this.image = new Image(inputStream);
        }
        if (this.image == null || this.image.isError()) {
            throw new RuntimeException("Image load error.");
        }
        // this.image = new Image(inputStream);
        this.mimeType = mimeType;
        this.imageData = inputStream;
        this.imageData.reset();
        
        init();
    }

    public void setUrl(String url) {
        this.url = url;
        try {
            this.image = new Image(url);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        init();
    }

    private void init() {
        imageView.setImage(image);
        imageView.setRotate(0);
        double width = image.getWidth();
        double height = image.getHeight();
        double imageRatio = width / height;
        double parentRatio = this.getWidth() / this.getHeight();

        if (imageRatio > parentRatio && width > this.getWidth()) {
            imageView.setFitWidth(this.getWidth());
        } else if (imageRatio < parentRatio && height > this.getHeight()) {
            imageView.setFitHeight(this.getHeight());
        }

        // zoomWithScroll();
        this.zoomProperty.set(100);
    }

    private void zoomWithScroll() {
        zoomProperty.addListener(observable -> imageView.setFitHeight(getImage().getHeight() * (zoomProperty.get() / 100)));

        this.addEventFilter(ScrollEvent.ANY, event -> {
            if (!event.isControlDown()) {
                return;
            }
            if (event.getDeltaY() > 0 && zoomProperty.get() < 400) {
                zoomProperty.set(zoomProperty.get() * 1.1);
            } else if (event.getDeltaY() < 0 && zoomProperty.get() > 25) {
                zoomProperty.set(zoomProperty.get() * 0.9);
            }
        });

        // Listen for ZoomEvents (pinch gestures)
        this.setOnZoom(event -> {
            zoomProperty.set(zoomProperty.get() * event.getZoomFactor());

            // Consume the event to prevent further propagation
            event.consume();
        });
    }

    /**
     * rotate
     */
    private void rotate(int angle) {
        imageView.setRotate(imageView.getRotate() + angle);
    }

    private static class ImageViewContextMenu extends ContextMenu {

        final ZoomImageView zoomImageView;

        final FileChooser fileChooser;

        private final OutputMessage.Source source;

        private final MessageQueue messageQueue;

        private final ApplicationConfig appConfig;

        public ImageViewContextMenu(ZoomImageView zoomImageView,
                                    MessageQueue messageQueue,
                                    ApplicationConfig appConfig,
                                    OutputMessage.Source source) {
            this.zoomImageView = zoomImageView;
            this.source = source;
            this.messageQueue = messageQueue;
            this.appConfig = appConfig;

            MenuItem download = new MenuItem(MessageSourceFactory.getMessage("context.menu.save"));
            MenuItem rotateClockwise = new MenuItem(MessageSourceFactory.getMessage("context.menu.rotate") + "-90°");
            MenuItem rotateAntiClock = new MenuItem(MessageSourceFactory.getMessage("context.menu.rotate") + "+90°");
            rotateClockwise.setOnAction(e -> {
                zoomImageView.rotate(-90);
            });
            rotateAntiClock.setOnAction(e -> {
                zoomImageView.rotate(90);
            });

            // save file event
            fileChooser = new FileChooser();
            fileChooser.setTitle("Save as...");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Image Files", "*.jpeg", "*.jpg", "*.png", "*.gif", "*.webp"),
                    new FileChooser.ExtensionFilter("All Files", "*.*")
            );

            OutputFileEventHandler<ActionEvent> eventHandler = new OutputFileEventHandler<>(source, messageQueue, appConfig,
                    () -> zoomImageView.getScene().getWindow());
            eventHandler.setFileChooser(fileChooser);
            eventHandler.setInitialFileNameSupplier(() -> {
                String extension = ".jpg";
                if (!StringUtils.isBlank(zoomImageView.mimeType)) {
                    String[] split = zoomImageView.mimeType.split("/");
                    extension = split.length > 1 ? "." + split[1] : "";
                }
                return "image" + extension;
            });
            download.setOnAction(eventHandler);
            getItems().addAll(rotateClockwise, rotateAntiClock, download);
        }
    }
}
