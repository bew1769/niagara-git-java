package com.bew1769.niagaragit;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Which members of a station backup are worth diffing.
 *
 * <p>Niagara 4 layout, from Tridium's AX-to-N4 migration guide and platform guide:
 * the protected station home holds config.bog, timestamped config.backup copies,
 * and the alarm, history, dataRecovery, provisioningNiagara, and
 * niagaraDriver_nVirtual folders. shared/ holds graphics. Certificate stores
 * live under the user home security folder (Station Security Guide).
 */
public final class Classifier {
    private static final Set<String> NOISY_DIRS = set(
            "alarm", "history", "logs", "logging",
            "datarecovery", "provisioningniagara", "niagaradriver_nvirtual",
            "__macosx");
    private static final Set<String> SECRET_DIRS = set(
            "security", "certmanagement", "certificates", "keystores");
    private static final Set<String> SECRET_FILES = set(
            "keystore.jceks", "cacerts.jceks", "exemptions.tes");
    private static final Set<String> SECRET_EXTS = set(
            ".jceks", ".bcfks", ".pem", ".key", ".p12", ".pfx",
            ".jks", ".keystore", ".cer", ".crt", ".der", ".priv",
            ".tes");
    private static final Set<String> NOISY_FILES = set(
            "station.out", "security.out", "audit.out",
            ".ds_store", "thumbs.db");
    private static final Set<String> NOISY_EXTS = set(".log", ".hdb", ".adb");
    private static final Pattern CONFIG_BACKUP = Pattern.compile(
            "(?i)^config(?:\\.backup\\.|_backup_).+\\.bog$");
    private static final Pattern DRIVE_LETTER = Pattern.compile("^[A-Za-z]:");

    private Classifier() {
    }

    public static final class Result {
        public final Kind kind;
        public final String display;

        public Result(Kind kind, String display) {
            this.kind = kind;
            this.display = display;
        }
    }

    /** Secret wins over noisy so a key left under logs/ is still secret. */
    public static Result classify(String name) {
        Normalized n = normalizeZipPath(name);
        if (n.unsafe) {
            return new Result(Kind.UNSAFE, n.display);
        }
        String[] parts = n.display.split("/", -1);
        String filename = fold(parts[parts.length - 1]);
        if (dirHit(parts, SECRET_DIRS) || SECRET_FILES.contains(filename) || SECRET_EXTS.contains(extOf(filename))) {
            return new Result(Kind.SECRET, n.display);
        }
        if (dirHit(parts, NOISY_DIRS) || NOISY_FILES.contains(filename) || NOISY_EXTS.contains(extOf(filename))
                || CONFIG_BACKUP.matcher(filename).matches()) {
            return new Result(Kind.NOISY, n.display);
        }
        return new Result(Kind.SHOW, n.display);
    }

    /** Stable bucket for a path already classified as noisy. */
    public static String noisyCategory(String displayPath) {
        String[] parts = displayPath.split("/", -1);
        for (int i = 0; i < parts.length - 1; i++) {
            String folded = fold(parts[i]);
            if (NOISY_DIRS.contains(folded)) {
                return folded;
            }
        }
        String name = fold(parts[parts.length - 1]);
        if (CONFIG_BACKUP.matcher(name).matches()) {
            return "config.backup";
        }
        if (NOISY_FILES.contains(name)) {
            if ("station.out".equals(name) || "security.out".equals(name) || "audit.out".equals(name)) {
                return "*.out";
            }
            return name;
        }
        String ext = extOf(name);
        if (NOISY_EXTS.contains(ext)) {
            return "*" + ext;
        }
        return "noisy";
    }

    static String fold(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    /**
     * pathlib suffix: a leading-dot name with no further dot has an empty suffix.
     * {@code file.tar.gz} yields {@code .gz}.
     */
    static String extOf(String name) {
        int slash = name.lastIndexOf('/');
        String base = slash >= 0 ? name.substring(slash + 1) : name;
        if (base.startsWith(".") && base.indexOf('.', 1) < 0) {
            return "";
        }
        int dot = base.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        return fold(base.substring(dot));
    }

    private static boolean dirHit(String[] parts, Set<String> dirs) {
        for (int i = 0; i < parts.length - 1; i++) {
            if (dirs.contains(fold(parts[i]))) {
                return true;
            }
        }
        return false;
    }

    private static final class Normalized {
        final boolean unsafe;
        final String display;

        Normalized(boolean unsafe, String display) {
            this.unsafe = unsafe;
            this.display = display;
        }
    }

    private static Normalized normalizeZipPath(String name) {
        String raw = name.replace('\\', '/');
        boolean absolute = raw.startsWith("/") || DRIVE_LETTER.matcher(raw).find();
        StringBuilder display = new StringBuilder();
        boolean dotDot = false;
        int parts = 0;
        int start = 0;
        while (start <= raw.length()) {
            int slash = raw.indexOf('/', start);
            if (slash < 0) {
                slash = raw.length();
            }
            String part = raw.substring(start, slash);
            if (!part.isEmpty() && !".".equals(part)) {
                if ("..".equals(part)) {
                    dotDot = true;
                }
                if (parts > 0) {
                    display.append('/');
                }
                display.append(part);
                parts++;
            }
            if (slash == raw.length()) {
                break;
            }
            start = slash + 1;
        }
        String shown = display.length() == 0 ? raw : display.toString();
        boolean unsafe = absolute || parts == 0 || dotDot;
        return new Normalized(unsafe, shown);
    }

    private static Set<String> set(String... values) {
        Set<String> out = new HashSet<String>();
        for (int i = 0; i < values.length; i++) {
            out.add(values[i]);
        }
        return out;
    }
}
