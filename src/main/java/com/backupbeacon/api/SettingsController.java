package com.backupbeacon.api;

import com.backupbeacon.service.FileSystemService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {
    private final FileSystemService fileSystemService;

    public SettingsController(FileSystemService fileSystemService) {
        this.fileSystemService = fileSystemService;
    }

    @GetMapping("/path-config")
    public Map<String, Object> pathConfig() {
        return fileSystemService.getPathConfig();
    }
}