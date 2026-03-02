package com.backupbeacon.api;

import com.backupbeacon.service.FileSystemService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/fs")
public class FileSystemController {
    private final FileSystemService fileSystemService;

    public FileSystemController(FileSystemService fileSystemService) {
        this.fileSystemService = fileSystemService;
    }

    @GetMapping("/list")
    public Map<String, Object> list(@RequestParam(value = "path", required = false) String path) {
        try {
            return fileSystemService.listDirectories(path);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/check-writable")
    public Map<String, Object> checkWritable(@RequestBody Map<String, Object> body) {
        String path = body.get("path") == null ? "" : String.valueOf(body.get("path"));
        try {
            fileSystemService.ensureWritable(path);
            return Collections.<String, Object>singletonMap("ok", true);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}