package com.catas.wicked.common.util;

import com.catas.wicked.common.webpdecoderjn.WebPDecoder;
import com.luciad.imageio.webp.WebPReadParam;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.image.WritablePixelFormat;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

@Slf4j
public class ImageUtils {

    /**
     * avoid using awt
     */
    public static Image getJFXImage(WebPDecoder.SimpleImageInfo imageInfo) {
        if (imageInfo == null || imageInfo.getPixels() == null || imageInfo.getPixels().length == 0) {
            return null;
        }

        int height = imageInfo.getHeight();
        int width = imageInfo.getWidth();
        WritableImage image = new WritableImage(width, height);
        PixelWriter pixelWriter = image.getPixelWriter();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = imageInfo.getPixels()[y * width + x];
                int r = pixel & 0x000000FF;
                int g = pixel & 0x0000FF00;
                int b = pixel & 0x00FF0000;
                int alpha = pixel & 0xFF000000;

                int argb = (r << 16) | g | (b >>> 16) | alpha;

                // Color color = Color.rgb(r, g, b);
                pixelWriter.setArgb(x, y, argb);
            }
        }
        return image;
    }

    /**
     * convert bufferedImage to javafx image
     * @param bufferedImage bufferedImage
     * @return javafx.scene.image.Image
     */
    @Deprecated
    public static Image getJFXImage(BufferedImage bufferedImage) {
        if (bufferedImage == null) {
             throw new RuntimeException("Buffered image cannot be null.");
        }
        WritableImage image = new WritableImage(bufferedImage.getWidth(), bufferedImage.getHeight());
        PixelWriter pixelWriter = image.getPixelWriter();
        for (int x = 0; x < bufferedImage.getWidth(); x++) {
            for (int y = 0; y < bufferedImage.getHeight(); y++) {
                pixelWriter.setArgb(x, y, bufferedImage.getRGB(x, y));
            }
        }
        return image;
    }

    public static BufferedImage fromJFXImage(Image image) {
        if (image == null) {
            throw new RuntimeException("Image cannot be null.");
        }
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        int pixels[] = new int[width * height];

        image.getPixelReader().getPixels(
                0, 0, width, height,
                (WritablePixelFormat<IntBuffer>) image.getPixelReader().getPixelFormat(),
                pixels, 0, width
        );

        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var pixel = pixels[y * width + x];
                int r = (pixel & 0xFF0000) >> 16;
                int g = (pixel & 0xFF00) >> 8;
                int b = (pixel & 0xFF);

                bufferedImage.getRaster().setPixel(x, y, new int[]{r, g, b});
            }
        }
        return bufferedImage;
    }

    /**
     * parse webp format image
     * @param inputStream image inputStream
     * @return bufferedImage
     * @throws IOException
     */
    @Deprecated
    public static BufferedImage encodeWebpImage(InputStream inputStream) throws IOException {
        // WebP.register(WebP.getWebPImageReaderSpi());
        ImageReader reader = ImageIO.getImageReadersByMIMEType("image/webp").next();
        if (inputStream instanceof ImageInputStream) {
            reader.setInput(inputStream);
        } else {
            MemoryCacheImageInputStream imageInputStream = new MemoryCacheImageInputStream(inputStream);
            reader.setInput(imageInputStream);
        }
        WebPReadParam readParam = new WebPReadParam();
        readParam.setBypassFiltering(true);
        return reader.read(0, readParam);
    }

    public static void saveToFile(Image image, File file) {
        if (image == null || file == null) {
            throw new RuntimeException("Image cannot be null.");
        }
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        PixelReader reader = image.getPixelReader();
        byte[] buffer = new byte[width * height * 4];
        WritablePixelFormat<ByteBuffer> format = PixelFormat.getByteBgraInstance();
        reader.getPixels(0, 0, width, height, format, buffer, 0, width * 4);
        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
            for(int count = 0; count < buffer.length; count += 4) {
                out.write(buffer[count + 2]);
                out.write(buffer[count + 1]);
                out.write(buffer[count]);
                out.write(buffer[count + 3]);
            }
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
