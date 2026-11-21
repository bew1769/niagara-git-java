package com.bew1769.niagaragit.core;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Which station files belong in git. Same rules as the Go niagara-git
 * {@code classify.go}: secrets win over noise, so a key file left under
 * {@code logs/} is still a secret.
 *
 * <p>Niagara 4 layout (AX to N4 Migration Guide, Platform Guide): the protected
 * station home holds {@code config.bog}, timestamped {@code config.backup}
 * copies, and the alarm, history, dataRecovery, provisioningNiagara, and
 * niagaraDriver_nVirtual folders. {@code shared/} holds graphics. Certificate
 * stores live under the user home {@code security} folder.
 */
public final class PathRules {

  /** What to do with a path. */
  public enum Kind { SHOW, NOISY, SECRET, UNSAFE }

  private static final Set<String> NOISY_DIRS = set(
      "alarm", "history", "logs", "logging", "datarecovery", "provisioningniagara",
      "niagaradriver_nvirtual", "__macosx", ".git");

  private static final Set<String> SECRET_DIRS = set(
      "security", "certmanagement", "certificates", "keystores");

  private static final Set<String> SECRET_FILENAMES = set(
      "keystore.jceks", "cacerts.jceks", "exemptions.tes");

  private static final Set<String> SECRET_EXTS = set(
      ".jceks", ".bcfks", ".pem", ".key", ".p12", ".pfx", ".jks", ".keystore",
      ".cer", ".crt", ".der", ".priv", ".tes");

  private static final Set<String> NOISY_FILENAMES = set(
      "station.out", "security.out", "audit.out", ".ds_store", "thumbs.db");

  private static final Set<String> NOISY_EXTS = set(".log", ".hdb", ".adb");

  private static final Pattern CONFIG_BACKUP =
      Pattern.compile("^config(?:\\.backup\\.|_backup_).+\\.bog$", Pattern.CASE_INSENSITIVE);

  private static final Pattern DRIVE_LETTER = Pattern.compile("^[A-Za-z]:");

  private PathRules() {
  }

  /** Classifies a path relative to the station directory. */
  public static Kind classify(String relativePath) {
    String raw = relativePath.replace('\\', '/');
    if (raw.startsWith("/") || DRIVE_LETTER.matcher(raw).find()) {
      return Kind.UNSAFE;
    }
    String[] parts = Arrays.stream(raw.split("/"))
        .filter(p -> !p.isEmpty() && !p.equals("."))
        .toArray(String[]::new);
    if (parts.length == 0) {
      return Kind.UNSAFE;
    }
    for (String part : parts) {
      if (part.equals("..")) {
        return Kind.UNSAFE;
      }
    }
    String filename = fold(parts[parts.length - 1]);
    boolean secretDir = false;
    boolean noisyDir = false;
    for (int i = 0; i < parts.length - 1; i++) {
      String dir = fold(parts[i]);
      secretDir |= SECRET_DIRS.contains(dir);
      noisyDir |= NOISY_DIRS.contains(dir);
    }
    String ext = extOf(filename);
    if (secretDir || SECRET_FILENAMES.contains(filename) || SECRET_EXTS.contains(ext)) {
      return Kind.SECRET;
    }
    if (noisyDir || NOISY_FILENAMES.contains(filename) || NOISY_EXTS.contains(ext)
        || CONFIG_BACKUP.matcher(filename).matches()) {
      return Kind.NOISY;
    }
    return Kind.SHOW;
  }

  /** A directory is skipped as a whole when its own name is a noisy or secret folder. */
  public static boolean skipDirectory(String dirName) {
    String folded = fold(dirName);
    return NOISY_DIRS.contains(folded) || SECRET_DIRS.contains(folded);
  }

  static String extOf(String name) {
    int slash = name.lastIndexOf('/');
    String base = slash >= 0 ? name.substring(slash + 1) : name;
    if (base.startsWith(".") && base.indexOf('.', 1) < 0) {
      return "";
    }
    int dot = base.lastIndexOf('.');
    return dot < 0 ? "" : fold(base.substring(dot));
  }

  private static String fold(String s) {
    return s.toLowerCase(Locale.ROOT);
  }

  private static Set<String> set(String... values) {
    return new HashSet<>(Arrays.asList(values));
  }
}
