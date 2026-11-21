package com.bew1769.niagaragit;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/** Files a snapshot is allowed to copy: config.bog and, optionally, shared/. */
public final class StationFiles {
    public static final class Item {
        public final String rel;
        public final byte[] data;

        public Item(String rel, byte[] data) {
            this.rel = rel;
            this.data = data;
        }
    }

    private StationFiles() {
    }

    public static List<Item> collect(File source, boolean shared) throws IOException {
        source = new File(Paths.expandUser(source.getPath()));
        if (!source.exists()) {
            throw new IOException("niagara-git: snapshot: " + source.getPath());
        }
        if (!source.isDirectory()) {
            return itemsFromFile(source, shared);
        }
        if (new File(source, "config.bog").isFile()) {
            return itemsFromStation(source, shared);
        }
        File newest = newestBackup(source);
        return itemsFromFile(newest, shared);
    }

    static List<Item> itemsFromStation(File dir, boolean shared) throws IOException {
        byte[] bog = java.nio.file.Files.readAllBytes(new File(dir, "config.bog").toPath());
        List<Item> items = new ArrayList<Item>();
        items.add(new Item("config.bog", bog));
        if (!shared) {
            return items;
        }
        File sharedRoot = new File(dir, "shared");
        if (!sharedRoot.exists()) {
            return items;
        }
        if (!sharedRoot.isDirectory()) {
            throw new IOException("niagara-git: snapshot: shared is not a directory");
        }
        final File root = dir;
        final List<Item> found = items;
        Files.walkFileTree(sharedRoot.toPath(), new FileVisitor<Path>() {
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (attrs.isSymbolicLink()) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                String rel = relOf(root, dir.toFile());
                if (rel != null && !"shared".equals(rel) && !rel.isEmpty()) {
                    Classifier.Result kind = Classifier.classify(rel);
                    if (kind.kind != Kind.SHOW) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (attrs.isSymbolicLink()) {
                    return FileVisitResult.CONTINUE;
                }
                String rel = relOf(root, file.toFile());
                if (rel == null) {
                    throw new IOException("niagara-git: snapshot refused path");
                }
                Classifier.Result kind = Classifier.classify(rel);
                if (kind.kind != Kind.SHOW) {
                    return FileVisitResult.CONTINUE;
                }
                found.add(new Item(rel, java.nio.file.Files.readAllBytes(file)));
                return FileVisitResult.CONTINUE;
            }

            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                throw exc;
            }

            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return items;
    }

    private static String relOf(File root, File path) throws IOException {
        String rel = root.toPath().relativize(path.toPath()).toString().replace('\\', '/');
        if (rel.startsWith("../") || "..".equals(rel)) {
            throw new IOException("niagara-git: snapshot refused path " + rel);
        }
        return rel;
    }

    static List<Item> itemsFromFile(File path, boolean shared) throws IOException {
        String lower = path.getName().toLowerCase(java.util.Locale.ROOT);
        byte[] data = java.nio.file.Files.readAllBytes(path.toPath());
        if (lower.endsWith(".bog")) {
            Classifier.Result kind = Classifier.classify(path.getName());
            if (kind.kind != Kind.SHOW) {
                throw new IOException("niagara-git: snapshot: refusing " + path.getName());
            }
            List<Item> items = new ArrayList<Item>();
            items.add(new Item(path.getName(), data));
            return items;
        }
        if (lower.endsWith(".dist")) {
            return itemsFromDist(data, shared);
        }
        throw new IOException("niagara-git: snapshot: " + path.getPath()
                + " is not a station directory, .bog, or .dist");
    }

    static List<Item> itemsFromDist(byte[] data, boolean shared) throws IOException {
        ZipArchives.Archive archive;
        try {
            archive = ZipArchives.read(data);
        } catch (IOException e) {
            throw new IOException("niagara-git: snapshot: backup is not a zip: " + e.getMessage());
        }
        ZipArchives.Member bog = null;
        for (int i = 0; i < archive.entries.size(); i++) {
            ZipArchives.Member file = archive.entries.get(i);
            if (file.directory) {
                continue;
            }
            String base = baseName(file.name);
            if (!"config.bog".equals(base)) {
                continue;
            }
            if (bog == null || file.name.length() < bog.name.length()) {
                bog = file;
            }
        }
        if (bog == null || bog.data == null) {
            throw new IOException("niagara-git: snapshot: backup has no config.bog");
        }
        List<Item> items = new ArrayList<Item>();
        items.add(new Item("config.bog", bog.data));
        if (!shared) {
            return items;
        }
        String prefix = bog.name.endsWith("config.bog")
                ? bog.name.substring(0, bog.name.length() - "config.bog".length()) : "";
        String sharedPrefix = prefix + "shared/";
        for (int i = 0; i < archive.entries.size(); i++) {
            ZipArchives.Member file = archive.entries.get(i);
            if (file.directory || !file.name.startsWith(sharedPrefix)) {
                continue;
            }
            String rel = "shared/" + file.name.substring(sharedPrefix.length());
            if (Classifier.classify(rel).kind != Kind.SHOW) {
                continue;
            }
            if (file.data == null) {
                throw new IOException("niagara-git: snapshot: unreadable " + file.name);
            }
            items.add(new Item(rel, file.data));
        }
        return items;
    }

    static File newestBackup(File dir) throws IOException {
        File[] entries = dir.listFiles();
        if (entries == null) {
            throw new IOException("niagara-git: snapshot: " + dir.getPath());
        }
        File best = null;
        long bestTime = 0;
        String bestName = "";
        for (int i = 0; i < entries.length; i++) {
            File entry = entries[i];
            if (entry.isDirectory()) {
                continue;
            }
            String name = entry.getName();
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            if (!lower.endsWith(".bog") && !lower.endsWith(".dist")) {
                continue;
            }
            long modified = entry.lastModified();
            if (best == null || modified > bestTime || (modified == bestTime && name.compareTo(bestName) > 0)) {
                best = entry;
                bestTime = modified;
                bestName = name;
            }
        }
        if (best == null) {
            throw new IOException("niagara-git: snapshot: " + dir.getPath() + " has no .bog or .dist backup");
        }
        return best;
    }

    private static String baseName(String name) {
        String slash = name.replace('\\', '/');
        int i = slash.lastIndexOf('/');
        return i < 0 ? slash : slash.substring(i + 1);
    }
}
