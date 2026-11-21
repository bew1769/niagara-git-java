package com.bew1769.niagaragit;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Subprocess git, matching the Go tool's install and snapshot commands. */
final class GitCli {
    private GitCli() {
    }

    static void run(File repo, String... args) throws IOException {
        Result result = exec(repo, args);
        if (result.code != 0) {
            String text = result.out.trim();
            if (text.isEmpty()) {
                throw new IOException("git " + join(args) + ": exit " + result.code);
            }
            throw new IOException("git " + join(args) + ": " + text);
        }
    }

    static String output(File repo, String... args) throws IOException {
        Result result = exec(repo, args);
        if (result.code != 0) {
            String text = result.out.trim();
            throw new IOException(text.isEmpty() ? "git " + join(args) + ": exit " + result.code : text);
        }
        return result.out;
    }

    /** @return true when {@code git diff --cached --quiet} reports a difference (exit 1). */
    static boolean cachedDirty(File repo) throws IOException {
        Result result = exec(repo, "diff", "--cached", "--quiet");
        if (result.code == 0) {
            return false;
        }
        if (result.code == 1) {
            return true;
        }
        throw new IOException("git diff --cached --quiet: " + result.out.trim());
    }

    static boolean succeeds(File repo, String... args) {
        try {
            return exec(repo, args).code == 0;
        } catch (IOException e) {
            return false;
        }
    }

    private static Result exec(File repo, String... args) throws IOException {
        List<String> cmd = new ArrayList<String>();
        cmd.add("git");
        if (repo != null) {
            cmd.add("-C");
            cmd.add(repo.getPath());
        }
        cmd.addAll(Arrays.asList(args));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Map<String, String> env = pb.environment();
        if (!env.containsKey("GIT_AUTHOR_NAME")) {
            env.put("GIT_AUTHOR_NAME", "niagara-git");
        }
        if (!env.containsKey("GIT_AUTHOR_EMAIL")) {
            env.put("GIT_AUTHOR_EMAIL", "niagara-git@localhost");
        }
        if (!env.containsKey("GIT_COMMITTER_NAME")) {
            env.put("GIT_COMMITTER_NAME", env.get("GIT_AUTHOR_NAME"));
        }
        if (!env.containsKey("GIT_COMMITTER_EMAIL")) {
            env.put("GIT_COMMITTER_EMAIL", env.get("GIT_AUTHOR_EMAIL"));
        }
        Process process = pb.start();
        byte[] bytes;
        try {
            bytes = ZipArchives.readLimited(process.getInputStream(), 32L * 1024L * 1024L);
            int code = process.waitFor();
            return new Result(code, new String(bytes, StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("git interrupted", e);
        }
    }

    private static String join(String[] args) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                b.append(' ');
            }
            b.append(args[i]);
        }
        return b.toString();
    }

    private static final class Result {
        final int code;
        final String out;

        Result(int code, String out) {
            this.code = code;
            this.out = out;
        }
    }
}
