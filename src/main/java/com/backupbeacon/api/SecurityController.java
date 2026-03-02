package com.backupbeacon.api;

import com.backupbeacon.service.CryptoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/security")
public class SecurityController {
    private final CryptoService cryptoService;

    public SecurityController(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Collections.<String, Object>singletonMap("initialized", cryptoService.isInitialized());
    }

    @PostMapping("/init")
    public Map<String, Object> init(@RequestBody Map<String, Object> body) {
        Object keyObj = body.get("key");
        String key = keyObj == null ? "" : String.valueOf(keyObj);
        cryptoService.initializeKey(key);
        return Collections.<String, Object>singletonMap("ok", true);
    }
}