package com.bew1769.niagaragit.core;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Copies the parts of a station worth versioning into a git work tree, and
 * back. A station directory is the folder that holds {@code config.bog}
 * (the protected station home). Only {@code config.bog} and, when asked,
 * the non-secret, non-noisy files under {@code shared/} are copied.
 * Histories, alarms, logs, keystores, and certificates never leave the station.
 */
public final class Snapshot {

  public static final String CONFIG_BOG = "config.bog";
  public static final String SHARED = "shared";

  /** Counts from one copy. */
  public static final class Result {
    public final List<String> written;
    public final List<String> deleted;
    /** Password values {@link BogSecrets} removed from config.bog. */
    public final int passwordsRemoved;

    Result(List<String> written, List<String> deleted) {
      this(written, deleted, 0);
    }

    Result(List<String> written, List<String> deleted, int passwordsRemoved) {
      this.written = Collections.unmodifiableList(written);
      this.deleted = Collections.unmodifiableList(deleted);
      this.passwordsRemoved = passwordsRemoved;
    }

    public boolean changed() {
      return !written.isEmpty() || !deleted.isEmpty();
    }

    @Override
    public String toString() {
      return written.size() + " written, " + deleted.size() + " deleted"
          + (passwordsRemoved > 0 ? ", " + passwordsRemoved + " password values removed" : "");
    }
  }

  /** Where the station database bytes come from. */
  public interface BogSource {
    byte[] read() throws IOException;
  }

  private Snapshot() {
  }

  /** Reads {@code config.bog} from the station directory. */
  public static BogSource fileSource(Path stationDir) {
    return () -> {
      Path bog = stationDir.resolve(CONFIG_BOG);
      if (!Files.isRegularFile(bog, LinkOption.NOFOLLOW_LINKS)) {
        throw new NoSuchFileException(bog.toString(), null, "station has no config.bog");
      }
      return Files.readAllBytes(bog);
    };
  }

  public static Result toWorkTree(Path stationDir, Path workTree, boolean includeShared)
      throws IOException {
    return toWorkTree(stationDir, fileSource(stationDir), workTree, includeShared);
  }

  /**
   * Makes the work tree match the station: {@code config.bog}, and the shared
   * files when {@code includeShared}. Files under the work tree's
   * {@code shared/} that the station no longer has are removed. Nothing else in
   * the work tree (README, .gitignore, .git) is touched.
   */
  public static Result toWorkTree(Path stationDir, BogSource bogSource, Path workTree,
      boolean includeShared) throws IOException {
    BogSecrets.Result clean = BogSecrets.redact(bogSource.read());
    byte[] bog = clean.bog;
    if (isInside(stationDir.resolve(SHARED), workTree)) {
      throw new IOException("the git work tree must not be inside the station shared folder: " + workTree);
    }
    Files.createDirectories(workTree);

    Map<String, Path> source = new TreeMap<>();
    if (includeShared) {
      source.putAll(listShared(stationDir));
    }

    List<String> written = new ArrayList<>();
    if (writeIfDifferent(bog, workTree.resolve(CONFIG_BOG))) {
      written.add(CONFIG_BOG);
    }
    for (Map.Entry<String, Path> e : source.entrySet()) {
      if (copyIfDifferent(e.getValue(), workTree.resolve(e.getKey()))) {
        written.add(e.getKey());
      }
    }

    List<String> deleted = new ArrayList<>();
    if (includeShared) {
      // Shared files are only removed when shared/ is part of the snapshot.
      for (Map.Entry<String, Path> e : listShared(workTree).entrySet()) {
        if (!source.containsKey(e.getKey())) {
          Files.delete(e.getValue());
          deleted.add(e.getKey());
        }
      }
    }
    pruneEmptyDirs(workTree.resolve(SHARED));
    return new Result(written, deleted, clean.removed);
  }

  /**
   * Copies shared files from the work tree into the station. Never writes
   * {@code config.bog} and never deletes station files: a running station owns
   * its database, and restoring one is a Platform or Station Copier job.
   */
  public static Result sharedToStation(Path workTree, Path stationDir) throws IOException {
    List<String> written = new ArrayList<>();
    for (Map.Entry<String, Path> e : listShared(workTree).entrySet()) {
      if (copyIfDifferent(e.getValue(), stationDir.resolve(e.getKey()))) {
        written.add(e.getKey());
      }
    }
    return new Result(written, new ArrayList<>());
  }

  /** Relative path (forward slashes, starting with {@code shared/}) to file. */
  static Map<String, Path> listShared(Path root) throws IOException {
    Map<String, Path> out = new TreeMap<>();
    Path shared = root.resolve(SHARED);
    if (!Files.isDirectory(shared, LinkOption.NOFOLLOW_LINKS)) {
      return out;
    }
    Files.walkFileTree(shared, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        if (!dir.equals(shared) && (attrs.isSymbolicLink() || PathRules.skipDirectory(dir.getFileName().toString()))) {
          return FileVisitResult.SKIP_SUBTREE;
        }
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        if (!attrs.isRegularFile() || attrs.isSymbolicLink()) {
          return FileVisitResult.CONTINUE;
        }
        String rel = relative(root, file);
        if (PathRules.classify(rel) == PathRules.Kind.SHOW) {
          out.put(rel, file);
        }
        return FileVisitResult.CONTINUE;
      }
    });
    return out;
  }

  static String relative(Path root, Path file) {
    return root.relativize(file).toString().replace('\\', '/');
  }

  private static boolean copyIfDifferent(Path from, Path to) throws IOException {
    return writeIfDifferent(Files.readAllBytes(from), to);
  }

  private static boolean writeIfDifferent(byte[] data, Path to) throws IOException {
    if (Files.isRegularFile(to, LinkOption.NOFOLLOW_LINKS) && Arrays.equals(data, Files.readAllBytes(to))) {
      return false;
    }
    Path parent = to.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Path tmp = to.resolveSibling(to.getFileName() + ".niagaragit-tmp");
    Files.write(tmp, data);
    Files.move(tmp, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    return true;
  }

  private static void pruneEmptyDirs(Path dir) throws IOException {
    if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
        if (!d.equals(dir)) {
          try (java.util.stream.Stream<Path> s = Files.list(d)) {
            if (!s.findAny().isPresent()) {
              Files.delete(d);
            }
          }
        }
        return FileVisitResult.CONTINUE;
      }
    });
  }

  private static boolean isInside(Path parent, Path child) {
    Path p = parent.toAbsolutePath().normalize();
    Path c = child.toAbsolutePath().normalize();
    return c.startsWith(p);
  }
}
