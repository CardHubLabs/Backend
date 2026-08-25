package com.kant.cardhub.storage.api;

import com.kant.cardhub.storage.infrastructure.LocalObjectStorageAdapter;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@Profile("!prod")
public class LocalStorageController {

    private final LocalObjectStorageAdapter storage;

    public LocalStorageController(LocalObjectStorageAdapter storage) {
        this.storage = storage;
    }

    @PutMapping("/api/v1/uploads/local")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void upload(
            @RequestParam String key,
            @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType,
            @RequestBody byte[] content
    ) {
        storage.put(key, contentType, content);
    }

    @GetMapping("/api/v1/listings/assets")
    public ResponseEntity<byte[]> download(@RequestParam String key) {
        LocalObjectStorageAdapter.LocalStoredObject object = storage.get(key);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(object.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(object.bytes());
    }
}
