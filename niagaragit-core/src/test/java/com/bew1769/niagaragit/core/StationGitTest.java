package com.bew1769.niagaragit.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StationGitTest {

  @TempDir
  Path tmp;

  Path station;
  Path bare;
  StationGit.Settings s;

  @BeforeEach
  void setUp() throws Exception {
    station = SnapshotTest.fakeStation(tmp.resolve("station"));
    bare = tmp.resolve("remote.git");
    Git.init().setBare(true).setDirectory(bare.toFile()).setInitialBranch("main").call().close();
    s = new StationGit.Settings();
    s.stationDir = station;
    s.workTree = station.resolve("git");
    s.remoteUrl = bare.toUri().toString();
    s.branch = "main";
  }

  static List<String> treePaths(Path gitDir, String ref) throws IOException {
    List<String> out = new ArrayList<>();
    try (Repository repo = new org.eclipse.jgit.storage.file.FileRepositoryBuilder()
        .setGitDir(gitDir.toFile()).setMustExist(true).build();
         RevWalk walk = new RevWalk(repo)) {
      ObjectId id = repo.resolve(ref);
      if (id == null) {
        return out;
      }
      RevCommit c = walk.parseCommit(id);
      try (TreeWalk tw = new TreeWalk(repo)) {
        tw.addTree(c.getTree());
        tw.setRecursive(true);
        while (tw.next()) {
          out.add(tw.getPathString());
        }
      }
    }
    return out;
  }

  @Test
  void commitOnlyWhenChanged() throws Exception {
    StationGit.Outcome first = StationGit.commit(s, "first");
    assertTrue(first.message.startsWith("committed "), first.message);
    StationGit.Outcome second = StationGit.commit(s, null);
    assertEquals("no changes", second.message);
    assertEquals(first.head, second.head);
  }

  @Test
  void pushSendsBogAndSharedButNoSecretsOrNoise() throws Exception {
    StationGit.Outcome o = StationGit.commitAndPush(s, "import");
    assertTrue(o.message.endsWith("pushed"), o.message);
    List<String> paths = treePaths(bare, "refs/heads/main");
    assertTrue(paths.contains("config.bog"), paths.toString());
    assertTrue(paths.contains("shared/px/AHU1.px"), paths.toString());
    assertTrue(paths.contains(".gitignore"), paths.toString());
    assertTrue(paths.contains(".gitattributes"), paths.toString());
    for (String p : paths) {
      assertEquals(PathRules.Kind.SHOW, PathRules.classify(p), p);
    }
    assertFalse(paths.stream().anyMatch(p -> p.startsWith("git/")), "repo not nested in itself");

    StationGit.Outcome again = StationGit.commitAndPush(s, null);
    assertEquals("no changes; up to date", again.message);
  }

  @Test
  void pullFastForwardsAndAppliesSharedButNotBog() throws Exception {
    StationGit.commitAndPush(s, "import");
    Path clone = tmp.resolve("workbench-clone");
    try (Git wb = Git.cloneRepository().setURI(bare.toUri().toString()).setDirectory(clone.toFile()).call()) {
      SnapshotTest.write(clone, "shared/px/Lobby.px", "<lobby/>");
      SnapshotTest.write(clone, "config.bog", "edited-on-workbench");
      wb.add().addFilepattern(".").call();
      wb.commit().setMessage("graphics").setAuthor("eng", "eng@example.com").setSign(false).call();
      wb.push().call();
    }
    s.applySharedOnPull = true;
    StationGit.Outcome o = StationGit.pull(s);
    assertTrue(o.message.startsWith("fast-forwarded to "), o.message);
    assertTrue(o.message.endsWith("1 shared files written to the station"), o.message);
    assertTrue(Files.exists(station.resolve("shared/px/Lobby.px")));
    assertArrayEquals("bog-v1".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(station.resolve("config.bog")),
        "the live config.bog is never written by pull");

    assertEquals("up to date", StationGit.pull(s).message.split(";")[0]);
  }

  @Test
  void divergedHistoryIsRefused() throws Exception {
    StationGit.commitAndPush(s, "import");
    Path clone = tmp.resolve("workbench-clone");
    try (Git wb = Git.cloneRepository().setURI(bare.toUri().toString()).setDirectory(clone.toFile()).call()) {
      SnapshotTest.write(clone, "shared/px/Other.px", "<other/>");
      wb.add().addFilepattern(".").call();
      wb.commit().setMessage("remote side").setAuthor("eng", "eng@example.com").setSign(false).call();
      wb.push().call();
    }
    SnapshotTest.write(station, "config.bog", "bog-v2");
    assertNotNull(StationGit.commit(s, "station side").head);

    IllegalStateException e = assertThrows(IllegalStateException.class, () -> StationGit.pull(s));
    assertTrue(e.getMessage().contains("diverged"), e.getMessage());
    IllegalStateException p = assertThrows(IllegalStateException.class, () -> StationGit.push(s));
    assertTrue(p.getMessage().contains("REJECTED"), p.getMessage());
  }

  @Test
  void firstPullIntoEmptyRepoChecksOutRemote() throws Exception {
    StationGit.commitAndPush(s, "import");
    StationGit.Settings other = new StationGit.Settings();
    other.stationDir = SnapshotTest.fakeStation(tmp.resolve("station2"));
    other.workTree = tmp.resolve("station2-git");
    other.remoteUrl = s.remoteUrl;
    StationGit.Outcome o = StationGit.pull(other);
    assertTrue(o.message.startsWith("checked out "), o.message);
    assertTrue(Files.exists(other.workTree.resolve("shared/px/AHU1.px")));
  }

  @Test
  void noRemoteMeansCommitOnly() throws Exception {
    s.remoteUrl = "";
    StationGit.Outcome o = StationGit.commitAndPush(s, null);
    assertTrue(o.message.endsWith("; no remote configured"), o.message);
    assertThrows(IllegalStateException.class, () -> StationGit.push(s));
  }

  @Test
  void repoConfigTurnsOffLineEndingConversion() throws Exception {
    StationGit.commit(s, null);
    String cfg = new String(Files.readAllBytes(s.workTree.resolve(".git/config")), StandardCharsets.UTF_8);
    assertTrue(cfg.contains("autocrlf = false"), cfg);
  }

  @Test
  void refusesCredentialsInRemoteUrl() {
    for (String bad : new String[] {
        "https://user:s3cr3t@example.com/r.git", "https://s3cr3t@example.com/r.git", "http://u:p@127.0.0.1:8443/r"}) {
      s.remoteUrl = bad;
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> StationGit.commit(s, null), bad);
      assertFalse(e.getMessage().contains("s3cr3t"), "message must not echo the token");
    }
    for (String ok : new String[] {
        "", "https://example.com/org/r.git", "https://127.0.0.1:8443/r.git?x=a@b", "file:///C:/x/r.git"}) {
      StationGit.checkRemoteUrl(ok);
    }
  }
}
