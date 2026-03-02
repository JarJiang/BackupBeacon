package com.backupbeacon.service;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Service
public class CryptoService {
    private static final String PREFIX = "ENC:";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final String KEY_FILE_PATH = "./data/crypto.key";

    private final SecureRandom secureRandom = new SecureRandom();
    private SecretKeySpec keySpec;

    public CryptoService() {
        loadKeyAtStartup();
    }

    public synchronized boolean isInitialized() {
        return keySpec != null;
    }

    public synchronized void initializeKey(String rawKey) {
        if (isInitialized()) {
            throw new IllegalStateException("密钥已初始化，禁止重复初始化");
        }
        if (rawKey == null || rawKey.trim().length() < 16) {
            throw new IllegalArgumentException("密钥长度至少 16 位");
        }
        try {
            File keyFile = new File(KEY_FILE_PATH);
            File parent = keyFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Files.write(keyFile.toPath(), rawKey.getBytes(StandardCharsets.UTF_8));
            this.keySpec = buildKey(rawKey);
        } catch (Exception e) {
            throw new IllegalStateException("密钥初始化失败", e);
        }
    }

    public synchronized String encrypt(String plain) {
        ensureInitialized();
        if (plain == null || plain.trim().isEmpty()) {
            return plain;
        }
        if (isEncrypted(plain)) {
            return plain;
        }
        try {
            byte[] iv = new byte[16];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("密码加密失败", e);
        }
    }

    public synchronized String decryptIfNeeded(String value) {
        ensureInitialized();
        if (value == null || value.trim().isEmpty()) {
            return value;
        }
        if (!isEncrypted(value)) {
            return value;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            byte[] iv = Arrays.copyOfRange(payload, 0, 16);
            byte[] encrypted = Arrays.copyOfRange(payload, 16, payload.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv));
            byte[] plain = cipher.doFinal(encrypted);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("密码解密失败，请检查初始化密钥是否一致", e);
        }
    }

    private void ensureInitialized() {
        if (!isInitialized()) {
            throw new IllegalStateException("系统密钥未初始化，请先在页面完成一次初始化");
        }
    }

    private boolean isEncrypted(String value) {
        return value.startsWith(PREFIX);
    }

    private void loadKeyAtStartup() {
        try {
            String env = System.getenv("BACKUPBEACON_CRYPTO_KEY");
            if (env != null && env.trim().length() >= 16) {
                this.keySpec = buildKey(env.trim());
                return;
            }
            File keyFile = new File(KEY_FILE_PATH);
            if (keyFile.exists()) {
                String fileKey = new String(Files.readAllBytes(keyFile.toPath()), StandardCharsets.UTF_8).trim();
                if (fileKey.length() >= 16) {
                    this.keySpec = buildKey(fileKey);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private SecretKeySpec buildKey(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(Arrays.copyOf(keyBytes, 16), "AES");
        } catch (Exception e) {
            throw new IllegalStateException("密钥处理失败", e);
        }
    }
}
