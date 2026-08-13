package com.catas.wicked.proxy.service.icon;

import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

import java.io.ByteArrayInputStream;

interface ApplicationIconData {

    int PNG_RASTER_SIZE = 64;

    Image toImage();

    record Png(byte[] bytes) implements ApplicationIconData {
        @Override
        public Image toImage() {
            return new Image(new ByteArrayInputStream(bytes), PNG_RASTER_SIZE, PNG_RASTER_SIZE,
                    true, true);
        }
    }

    record Bgra(int width, int height, byte[] pixels) implements ApplicationIconData {
        @Override
        public Image toImage() {
            WritableImage image = new WritableImage(width, height);
            image.getPixelWriter().setPixels(0, 0, width, height,
                    PixelFormat.getByteBgraInstance(), pixels, 0, width * 4);
            return image;
        }
    }
}
