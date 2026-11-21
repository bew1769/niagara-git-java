package com.bew1769.niagaragit;

import java.io.File;
import java.io.IOException;

final class Paths {
    private Paths() {
    }

    static String expandUser(String path) {
        if (path == null) {
            return null;
        }
        if ("~".equals(path) || path.startsWith("~/") || path.startsWith("~\\")) {
            String home = System.getProperty("user.home");
            if (home == null || home.isEmpty()) {
                return path;
            }
            if ("~".equals(path)) {
                return home;
            }
            return new File(home, path.substring(2)).getPath();
        }
        return path;
    }

    static File canonical(File path) {
        try {
            return path.getCanonicalFile();
        } catch (IOException e) {
            return path.getAbsoluteFile();
        }
    }

    static boolean sameDir(File a, File b) {
        File ca = canonical(a);
        File cb = canonical(b);
        if (ca.equals(cb)) {
            return true;
        }
        if (isWindows() && ca.getPath().equalsIgnoreCase(cb.getPath())) {
            return true;
        }
        try {
            return ca.exists() && cb.exists() && java.nio.file.Files.isSameFile(ca.toPath(), cb.toPath());
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Resolved file for a relative station path. Refuses anything that leaves {@code root}.
     */
    static File resolveInside(File root, String rel) throws IOException {
        if (rel.indexOf('\0') >= 0) {
            throw new IOException("niagara-git: refusing path " + rel);
        }
        String normalized = rel.replace('\\', '/');
        Classifier.Result classified = Classifier.classify(normalized);
        if (classified.kind == Kind.UNSAFE) {
            throw new IOException("niagara-git: refusing path " + rel);
        }
        File rootCanon = canonical(root);
        File dest = canonical(new File(root, normalized.replace('/', File.separatorChar)));
        String rootPath = rootCanon.getPath();
        String destPath = dest.getPath();
        if (!destPath.equals(rootPath) && !destPath.startsWith(rootPath + File.separator)) {
            throw new IOException("niagara-git: refusing path outside station: " + rel);
        }
        return dest;
    }

    static boolean isWindows() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    static String slash(String path) {
        return path.replace('\\', '/');
    }
}
