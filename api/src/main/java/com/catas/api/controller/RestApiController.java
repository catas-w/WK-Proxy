package com.catas.api.controller;

import com.catas.api.param.Param;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

@RestController
@RequestMapping("")
public class RestApiController {

    @RequestMapping("")
    public String host() {
        return "Time" + System.currentTimeMillis();
    }

    @RequestMapping("/api/test")
    public String testApi() throws InterruptedException {
        Thread.sleep(5000);
        // MultipartStream
        return "From wicked-proxy";
    }

    @RequestMapping("/api/page")
    public String page() {
        return "Page";
    }

    @RequestMapping("/api/page/{num}")
    public String pageNum(@PathVariable("num") int num) {
        return "Page-" + num;
    }

    @RequestMapping("/api/page/{num}/detail")
    public String pageDetail(@PathVariable("num") int num) {
        return "Page-" + num + "-Detail";
    }

    @RequestMapping("/api/test/params")
    public String param(@RequestParam Param param) {
        return "Param: " + param.toString();
    }

    @PostMapping("/api/file-upload")
    public String testFile(MultipartFile file) {
        String res = file == null ? "empty file" : String.valueOf(file.getSize());
        return "File size: " + res;
    }

    @PostMapping(value = "/api/file-upload-v2", consumes = "image/jpeg")
    public ResponseEntity testFileV2(@RequestBody byte[] file) {
        if (file == null) {
            return ResponseEntity.ok("Empty file");
        }
        try {
            // Ensure the directory exists
            File directory = new File("test-image");
            if (!directory.exists()) {
                directory.mkdirs();
            }

            // Generate a unique file name
            String fileName = "image_" + System.currentTimeMillis() + ".jpg";

            // Save the file to the directory
            File imageFile = new File(directory, fileName);
            try (FileOutputStream fos = new FileOutputStream(imageFile)) {
                fos.write(file);
            }

            return ResponseEntity.ok("File size: " + file.length);
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Error saving the image: " + e.getMessage());
        }
    }
}
