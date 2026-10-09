package com.example.demo.controller;

import com.example.demo.service.ImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequiredArgsConstructor
public class UploadedImageController {
    private final ImageStorage storage;

    @GetMapping("/uploads/{fileName:[a-z0-9-]+\\.(?:png|jpeg|jpg)}")
    public ResponseEntity<byte[]> image(@PathVariable String fileName) {
        return storage.load(fileName)
            .map(image -> ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(image.content()))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
