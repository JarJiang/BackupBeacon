package com.backupbeacon.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class FileSystemService {
    private final List<Path> allowedRoots;
    private final List<String> allowedRootStrings;
    private final Map<String, String> pathDisplayMap;

    public FileSystemService(
            @Value("${backupbeacon.fs.allowed-roots:./backup-target,/mnt,/data/backups,D:/,Z:/}") String rawRoots,
            @Value("${backupbeacon.fs.path-display-map:}") String rawPathDisplayMap) {
        this.allowedRoots = parseRoots(rawRoots);
        this.allowedRootStrings = Collections.unmodifiableList(toRootStrings(this.allowedRoots));
        this.pathDisplayMap = Collections.unmodifiableMap(parsePathDisplayMap(rawPathDisplayMap));
    }

    public Map<String, Object> getPathConfig() {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("allowedRoots", new ArrayList<String>(allowedRootStrings));
        result.put("displayMap", new LinkedHashMap<String, String>(pathDisplayMap));
        return result;
    }

    public Map<String, Object> listDirectories(String path) {
        Path target = resolveTarget(path);
        ensureAllowed(target);

        try {
            if (!Files.exists(target)) {
                // 目录选择时遇到不存在目录，自动创建，避免前端直接 400。
                Files.createDirectories(target);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("目录不存在且无法创建: " + target.toString() + "，原因: " + e.getMessage());
        }

        if (!Files.isDirectory(target)) {
            throw new IllegalArgumentException("不是目录: " + target.toString());
        }

        List<Map<String, String>> entries;
        try (Stream<Path> stream = Files.list(target)) {
            entries = stream
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                    .map(this::toDirItem)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new IllegalArgumentException("读取目录失败: " + e.getMessage());
        }

        Path parent = target.getParent();
        String parentPath = null;
        if (parent != null && isAllowed(parent)) {
            parentPath = parent.toString();
        }

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("currentPath", target.toString());
        result.put("parentPath", parentPath);
        result.put("entries", entries);
        result.put("allowedRoots", new ArrayList<String>(allowedRootStrings));
        return result;
    }

    public void ensureWritable(String path) {
        Path target = resolveTarget(path);
        ensureAllowed(target);

        try {
            if (!Files.exists(target)) {
                Files.createDirectories(target);
            }
            if (!Files.isDirectory(target)) {
                throw new IllegalArgumentException("备份目录不是文件夹: " + target.toString());
            }

            Path probe = target.resolve(".bb_write_probe_" + System.currentTimeMillis() + ".tmp");
            Files.write(probe, new byte[]{1}, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            Files.deleteIfExists(probe);
        } catch (IOException e) {
            throw new IllegalArgumentException("备份目录不可写: " + target.toString() + "，原因: " + e.getMessage());
        }
    }

    private Map<String, String> toDirItem(Path p) {
        Map<String, String> item = new HashMap<String, String>();
        item.put("name", p.getFileName() == null ? p.toString() : p.getFileName().toString());
        item.put("path", p.toString());
        return item;
    }

    private Path resolveTarget(String rawPath) {
        String fixed = normalizeInputPath(rawPath == null ? "" : rawPath.trim());
        if (fixed.isEmpty()) {
            Path first = firstExistingRoot();
            if (first != null) {
                return first;
            }
            throw new IllegalArgumentException("未配置可用目录白名单");
        }
        return Paths.get(fixed).toAbsolutePath().normalize();
    }

    private String normalizeInputPath(String path) {
        if (path.isEmpty()) {
            return path;
        }

        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (windows && path.startsWith("/") && !path.startsWith("//")) {
            return "." + path.replace("/", File.separator);
        }
        return path;
    }

    private void ensureAllowed(Path path) {
        if (!isAllowed(path)) {
            String roots = String.join(", ", allowedRootStrings);
            throw new IllegalArgumentException("目录不在允许范围内。允许根目录: " + roots);
        }
    }

    private boolean isAllowed(Path path) {
        for (Path root : allowedRoots) {
            if (path.startsWith(root)) {
                return true;
            }
        }
        return false;
    }

    private Path firstExistingRoot() {
        for (Path root : allowedRoots) {
            if (Files.exists(root)) {
                return root;
            }
        }
        return allowedRoots.isEmpty() ? null : allowedRoots.get(0);
    }

    private List<Path> parseRoots(String rawRoots) {
        Set<Path> roots = new LinkedHashSet<Path>();

        if (rawRoots != null && !rawRoots.trim().isEmpty()) {
            String[] arr = rawRoots.split(",");
            for (String item : arr) {
                String p = normalizeInputPath(item.trim());
                if (!p.isEmpty()) {
                    roots.add(Paths.get(p).toAbsolutePath().normalize());
                }
            }
        }

        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (windows) {
            for (File drive : File.listRoots()) {
                roots.add(drive.toPath().toAbsolutePath().normalize());
            }
        }

        if (roots.isEmpty()) {
            roots.add(Paths.get("./backup-target").toAbsolutePath().normalize());
        }
        return new ArrayList<Path>(roots);
    }

    private List<String> toRootStrings(List<Path> roots) {
        List<String> values = new ArrayList<String>();
        for (Path root : roots) {
            values.add(root.toString());
        }
        return values;
    }

    private Map<String, String> parsePathDisplayMap(String rawPathDisplayMap) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        if (rawPathDisplayMap == null || rawPathDisplayMap.trim().isEmpty()) {
            return result;
        }

        String normalized = rawPathDisplayMap.replace("\n", ",").replace(";", ",");
        String[] entries = normalized.split(",");
        for (String entry : entries) {
            String item = entry == null ? "" : entry.trim();
            if (item.isEmpty()) {
                continue;
            }

            int splitIndex = item.indexOf('=');
            if (splitIndex <= 0 || splitIndex >= item.length() - 1) {
                continue;
            }

            String containerPath = item.substring(0, splitIndex).trim();
            String hostPath = item.substring(splitIndex + 1).trim();
            if (!containerPath.isEmpty() && !hostPath.isEmpty()) {
                result.put(containerPath, hostPath);
            }
        }
        return result;
    }
}