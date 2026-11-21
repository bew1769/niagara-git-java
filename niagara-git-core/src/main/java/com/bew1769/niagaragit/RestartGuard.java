package com.bew1769.niagaragit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Stops a pulled commit from restarting the station forever.
 * Two restart attempts are allowed per commit id. The third is refused.
 */
public final class RestartGuard {
    static final String FILE_NAME = ".niagara-git/restart-guard";

    private RestartGuard() {
    }

    public static boolean arm(File station, String sha) throws IOException {
        State state = read(station);
        int count = sha.equals(state.sha) ? state.count : 0;
        if (count >= 2) {
            return false;
        }
        write(station, sha, count + 1);
        return true;
    }

    public static int count(File station, String sha) throws IOException {
        State state = read(station);
        return sha.equals(state.sha) ? state.count : 0;
    }

    private static State read(File station) throws IOException {
        File file = new File(station, FILE_NAME.replace('/', File.separatorChar));
        if (!file.isFile()) {
            return new State("", 0);
        }
        String text = new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
        int space = text.indexOf(' ');
        if (space < 0) {
            return new State(text, 0);
        }
        int count;
        try {
            count = Integer.parseInt(text.substring(space + 1).trim());
        } catch (NumberFormatException e) {
            count = 0;
        }
        return new State(text.substring(0, space), count);
    }

    private static void write(File station, String sha, int count) throws IOException {
        File file = Paths.resolveInside(station, FILE_NAME);
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("cannot create " + parent);
        }
        FileOutputStream out = new FileOutputStream(file);
        try {
            out.write((sha + " " + count + "\n").getBytes(StandardCharsets.US_ASCII));
        } finally {
            out.close();
        }
    }

    private static final class State {
        final String sha;
        final int count;

        State(String sha, int count) {
            this.sha = sha;
            this.count = count;
        }
    }
}
