package com.bew1769.niagaragit.core;

import java.io.IOException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.eclipse.jgit.api.errors.GitAPIException;

/**
 * The operations a station exposes, without any Niagara types, so they can be
 * tested on a plain JVM. The Niagara component fills in {@link Settings} from
 * its properties and calls these on a worker thread.
 */
public final class StationGit {

  /** Everything an operation needs. */
  public static final class Settings {
    public Path stationDir;
    public Path workTree;
    public String remoteUrl = "";
    public String branch = "main";
    public String username = "";
    public String password = "";
    public String authorName = "Niagara station";
    public String authorEmail = "station@niagara.invalid";
    public boolean includeShared = true;
    public boolean applySharedOnPull = false;
    /** Station database bytes. Null means read {@code stationDir/config.bog}. */
    public Snapshot.BogSource bogSource;
  }

  /** What an operation did, for the component's status slots. */
  public static final class Outcome {
    public final String message;
    public final String head;

    Outcome(String message, String head) {
      this.message = message;
      this.head = head == null ? "" : head;
    }

    @Override
    public String toString() {
      return message;
    }
  }

  private StationGit() {
  }

  /** Copies the station into the work tree and commits when it changed. */
  public static Outcome commit(Settings s, String reason) throws IOException, GitAPIException {
    try (StationRepo repo = StationRepo.open(s.workTree, s.branch, s.remoteUrl)) {
      Snapshot.BogSource src = s.bogSource != null ? s.bogSource : Snapshot.fileSource(s.stationDir);
      Snapshot.Result copied = Snapshot.toWorkTree(s.stationDir, src, s.workTree, s.includeShared);
      String id = repo.commitAll(message(reason), s.authorName, s.authorEmail);
      String msg = id == null
          ? "no changes"
          : "committed " + id.substring(0, 8) + " (" + copied + ")";
      return new Outcome(msg, repo.head());
    }
  }

  /** Pushes what is already committed. */
  public static Outcome push(Settings s) throws IOException, GitAPIException {
    try (StationRepo repo = StationRepo.open(s.workTree, s.branch, s.remoteUrl)) {
      String r = repo.push(s.username, s.password);
      return new Outcome(r, repo.head());
    }
  }

  /** Commit, then push when there is a remote. */
  public static Outcome commitAndPush(Settings s, String reason) throws IOException, GitAPIException {
    Outcome c = commit(s, reason);
    try (StationRepo repo = StationRepo.open(s.workTree, s.branch, s.remoteUrl)) {
      if (!repo.hasRemote()) {
        return new Outcome(c.message + "; no remote configured", c.head);
      }
      if (repo.head() == null) {
        return c;
      }
      String r = repo.push(s.username, s.password);
      return new Outcome(c.message + "; " + r, repo.head());
    }
  }

  /**
   * Fast-forwards the repo from the remote. With {@code applySharedOnPull},
   * copies the pulled {@code shared/} files into the station. config.bog in
   * the station is never written.
   */
  public static Outcome pull(Settings s) throws IOException, GitAPIException {
    try (StationRepo repo = StationRepo.open(s.workTree, s.branch, s.remoteUrl)) {
      String r = repo.pull(s.username, s.password);
      if (s.applySharedOnPull) {
        Snapshot.Result applied = Snapshot.sharedToStation(s.workTree, s.stationDir);
        r = r + "; " + applied.written.size() + " shared files written to the station";
      }
      return new Outcome(r, repo.head());
    }
  }

  static String message(String reason) {
    SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    f.setTimeZone(TimeZone.getTimeZone("UTC"));
    String stamp = "snapshot " + f.format(new Date());
    return reason == null || reason.trim().isEmpty() ? stamp : stamp + "\n\n" + reason.trim();
  }
}
