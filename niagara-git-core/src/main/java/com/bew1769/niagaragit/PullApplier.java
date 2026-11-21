package com.bew1769.niagaragit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Holds the files a fast-forward pull wants on disk, then copies them into the
 * station directory. A station save writes the running database over config.bog,
 * so the copy runs after that save and again from a shutdown hook.
 */
public final class PullApplier {
    static final String STAGED = ".niagara-git/staged";
    static final String PENDING = ".niagara-git/pending-sha";
    static final String REMOVALS = ".niagara-git/staged-removals";

    private PullApplier() {
    }

    public static void stage(File station, String sha, List<StationFiles.Item> files, List<String> removals)
            throws IOException {
        File stagedRoot = Paths.resolveInside(station, STAGED);
        deleteTree(stagedRoot);
        if (!stagedRoot.mkdirs() && !stagedRoot.isDirectory()) {
            throw new IOException("cannot create " + stagedRoot);
        }
        for (int i = 0; i < files.size(); i++) {
            StationFiles.Item item = files.get(i);
            if (Classifier.classify(item.rel).kind != Kind.SHOW) {
                continue;
            }
            File dest = Paths.resolveInside(stagedRoot, item.rel);
            File parent = dest.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("cannot create " + parent);
            }
            FileOutputStream out = new FileOutputStream(dest);
            try {
                out.write(item.data);
            } finally {
                out.close();
            }
        }
        writeText(Paths.resolveInside(station, PENDING), sha + "\n");
        StringBuilder removed = new StringBuilder();
        for (int i = 0; i < removals.size(); i++) {
            removed.append(removals.get(i)).append('\n');
        }
        writeText(Paths.resolveInside(station, REMOVALS), removed.toString());
    }

    public static boolean hasPending(File station) {
        return new File(station, PENDING.replace('/', File.separatorChar)).isFile();
    }

    public static String pendingSha(File station) throws IOException {
        File file = new File(station, PENDING.replace('/', File.separatorChar));
        if (!file.isFile()) {
            return "";
        }
        return new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
    }

    /** True when staged config.bog is missing from disk or its bytes differ. */
    public static boolean needsApply(File station) throws IOException {
        if (!hasPending(station)) {
            return false;
        }
        File stagedBog = new File(station, (STAGED + "/config.bog").replace('/', File.separatorChar));
        if (!stagedBog.isFile()) {
            return false;
        }
        File live = new File(station, "config.bog");
        if (!live.isFile()) {
            return true;
        }
        byte[] a = java.nio.file.Files.readAllBytes(stagedBog.toPath());
        byte[] b = java.nio.file.Files.readAllBytes(live.toPath());
        return !Arrays.equals(a, b);
    }

    private static volatile boolean hookRegistered;

    /** Re-apply a staged pull after Niagara's shutdown save. Registered at most once. */
    public static void registerHook(final File station) {
        if (hookRegistered) {
            return;
        }
        hookRegistered = true;
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                try {
                    apply(station);
                } catch (Exception ignored) {
                    // The station JVM is exiting. serviceStarted retries the copy.
                }
            }
        }, "niagara-git-pull"));
    }

    public static void apply(File station) throws IOException {
        if (!hasPending(station)) {
            return;
        }
        File stagedRoot = new File(station, STAGED.replace('/', File.separatorChar));
        if (stagedRoot.isDirectory()) {
            copyTree(stagedRoot, stagedRoot, station);
        }
        File removals = new File(station, REMOVALS.replace('/', File.separatorChar));
        if (removals.isFile()) {
            String text = new String(java.nio.file.Files.readAllBytes(removals.toPath()), StandardCharsets.UTF_8);
            String[] lines = text.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                String rel = lines[i].trim();
                if (rel.isEmpty()) {
                    continue;
                }
                if (Classifier.classify(rel).kind != Kind.SHOW) {
                    continue;
                }
                File dest = Paths.resolveInside(station, rel);
                if (dest.isFile() && !dest.delete()) {
                    throw new IOException("cannot remove " + rel);
                }
            }
        }
    }

    private static void copyTree(File stagedRoot, File dir, File station) throws IOException {
        File[] kids = dir.listFiles();
        if (kids == null) {
            return;
        }
        for (int i = 0; i < kids.length; i++) {
            File kid = kids[i];
            if (kid.isDirectory()) {
                copyTree(stagedRoot, kid, station);
                continue;
            }
            String rel = stagedRoot.toPath().relativize(kid.toPath()).toString().replace('\\', '/');
            if (Classifier.classify(rel).kind != Kind.SHOW) {
                continue;
            }
            byte[] data = java.nio.file.Files.readAllBytes(kid.toPath());
            File dest = Paths.resolveInside(station, rel);
            File parent = dest.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("cannot create " + parent);
            }
            FileOutputStream out = new FileOutputStream(dest);
            try {
                out.write(data);
            } finally {
                out.close();
            }
        }
    }

    private static void writeText(File dest, String text) throws IOException {
        File parent = dest.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("cannot create " + parent);
        }
        FileOutputStream out = new FileOutputStream(dest);
        try {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } finally {
            out.close();
        }
    }

    private static void deleteTree(File dir) throws IOException {
        if (!dir.exists()) {
            return;
        }
        File[] kids = dir.listFiles();
        if (kids != null) {
            for (int i = 0; i < kids.length; i++) {
                if (kids[i].isDirectory()) {
                    deleteTree(kids[i]);
                } else if (!kids[i].delete()) {
                    throw new IOException("cannot delete " + kids[i]);
                }
            }
        }
        if (dir.exists() && !dir.delete()) {
            throw new IOException("cannot delete " + dir);
        }
    }

    /** Paths the station repo itself tracks, not the tool's private directory. */
    public static boolean managed(String rel) {
        return "config.bog".equals(rel) || rel.startsWith("shared/")
                || ".gitignore".equals(rel) || ".gitattributes".equals(rel);
    }

    public static List<String> emptyRemovals() {
        return new ArrayList<String>();
    }
}
