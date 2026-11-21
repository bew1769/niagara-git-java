package com.bew1769.niagaragit.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

/**
 * A git repository that holds one station's snapshot. Wraps JGit so the
 * station needs no git executable. Every method is synchronous; the station
 * component runs them off the engine thread.
 *
 * <p>Pull is fast-forward only. A station repo is a mirror of one station, so
 * a history that diverged needs a person with a Workbench clone, not an
 * automatic merge of {@code config.bog}.
 */
public final class StationRepo implements AutoCloseable {

  public static final String REMOTE = "origin";

  private final Path workTree;
  private final String branch;
  private final Git git;

  private StationRepo(Path workTree, String branch, Git git) {
    this.workTree = workTree;
    this.branch = branch;
    this.git = git;
  }

  /**
   * Opens the repository at {@code workTree}, creating it on {@code branch}
   * if it does not exist. When {@code remoteUrl} is not blank it becomes
   * {@code origin}. The niagara-git ignore and attribute blocks are written
   * into the work tree.
   */
  public static StationRepo open(Path workTree, String branch, String remoteUrl)
      throws IOException, GitAPIException {
    IsolatedSystemReader.install();
    String b = (branch == null || branch.trim().isEmpty()) ? "main" : branch.trim();
    Files.createDirectories(workTree);
    File dotGit = workTree.resolve(Constants.DOT_GIT).toFile();
    Git git;
    if (dotGit.isDirectory()) {
      Repository repo = new FileRepositoryBuilder()
          .setGitDir(dotGit)
          .setWorkTree(workTree.toFile())
          .setMustExist(true)
          .build();
      git = new Git(repo);
    } else {
      git = Git.init().setDirectory(workTree.toFile()).setInitialBranch(b).call();
    }
    StationRepo sr = new StationRepo(workTree, b, git);
    sr.configure(remoteUrl);
    Templates.mergeBlock(workTree.resolve(Constants.DOT_GIT_IGNORE), Templates.gitignore());
    Templates.mergeBlock(workTree.resolve(Constants.DOT_GIT_ATTRIBUTES), Templates.gitattributes());
    return sr;
  }

  private void configure(String remoteUrl) throws IOException {
    StoredConfig config = git.getRepository().getConfig();
    boolean dirty = false;
    // Station files are binary zips; never rewrite line endings or file modes.
    if (!"false".equals(config.getString("core", null, "autocrlf"))) {
      config.setString("core", null, "autocrlf", "false");
      dirty = true;
    }
    if (!"false".equals(config.getString("commit", null, "gpgsign"))) {
      config.setString("commit", null, "gpgsign", "false");
      dirty = true;
    }
    if (remoteUrl != null && !remoteUrl.trim().isEmpty()) {
      String url = remoteUrl.trim();
      if (!url.equals(config.getString("remote", REMOTE, "url"))) {
        config.setString("remote", REMOTE, "url", url);
        config.setString("remote", REMOTE, "fetch", "+refs/heads/*:refs/remotes/" + REMOTE + "/*");
        dirty = true;
      }
    }
    if (dirty) {
      config.save();
    }
  }

  public Path workTree() {
    return workTree;
  }

  public String branch() {
    return branch;
  }

  public boolean hasRemote() {
    String url = git.getRepository().getConfig().getString("remote", REMOTE, "url");
    return url != null && !url.isEmpty();
  }

  /** The commit id at HEAD, or null when nothing has been committed. */
  public String head() throws IOException {
    ObjectId id = git.getRepository().resolve(Constants.HEAD);
    return id == null ? null : id.name();
  }

  /**
   * Stages everything in the work tree (honouring .gitignore, including
   * deletions) and commits when something changed. Returns the new commit id,
   * or null when the tree already matched HEAD.
   */
  public String commitAll(String message, String authorName, String authorEmail)
      throws GitAPIException {
    git.add().addFilepattern(".").call();
    git.add().addFilepattern(".").setUpdate(true).call();
    if (!git.status().call().hasUncommittedChanges()) {
      return null;
    }
    PersonIdent who = new PersonIdent(
        blank(authorName) ? "Niagara station" : authorName,
        blank(authorEmail) ? "station@niagara.invalid" : authorEmail);
    RevCommit c = git.commit()
        .setMessage(message)
        .setAuthor(who)
        .setCommitter(who)
        .setSign(false)
        .call();
    return c.name();
  }

  /**
   * Pushes the branch to {@code origin} and records it as the upstream.
   * Never forces. Throws when the remote rejects the update.
   */
  public String push(String username, String password) throws GitAPIException, IOException {
    requireRemote();
    if (head() == null) {
      throw new IllegalStateException("nothing to push: no commits on " + branch);
    }
    String ref = Constants.R_HEADS + branch;
    Iterable<PushResult> results = git.push()
        .setRemote(REMOTE)
        .setRefSpecs(new RefSpec(ref + ":" + ref))
        .setForce(false)
        .setCredentialsProvider(credentials(username, password))
        .call();
    List<String> problems = new ArrayList<>();
    String outcome = "unknown";
    for (PushResult r : results) {
      for (RemoteRefUpdate u : r.getRemoteUpdates()) {
        RemoteRefUpdate.Status s = u.getStatus();
        if (s == RemoteRefUpdate.Status.OK || s == RemoteRefUpdate.Status.UP_TO_DATE) {
          outcome = s == RemoteRefUpdate.Status.OK ? "pushed" : "up to date";
        } else {
          problems.add(u.getRemoteName() + ": " + s + (u.getMessage() == null ? "" : " (" + u.getMessage() + ")"));
        }
      }
    }
    if (!problems.isEmpty()) {
      throw new IllegalStateException("push rejected: " + String.join("; ", problems));
    }
    StoredConfig config = git.getRepository().getConfig();
    config.setString("branch", branch, "remote", REMOTE);
    config.setString("branch", branch, "merge", ref);
    config.save();
    return outcome;
  }

  /**
   * Fetches {@code origin} and fast-forwards the branch. Refuses when the work
   * tree has uncommitted changes or the histories diverged. Returns a short
   * description of what happened.
   */
  public String pull(String username, String password) throws GitAPIException, IOException {
    requireRemote();
    git.fetch()
        .setRemote(REMOTE)
        .setCredentialsProvider(credentials(username, password))
        .call();
    Repository repo = git.getRepository();
    Ref remote = repo.exactRef(Constants.R_REMOTES + REMOTE + "/" + branch);
    if (remote == null) {
      return "remote has no branch " + branch;
    }
    ObjectId remoteId = remote.getObjectId();
    ObjectId local = repo.resolve(Constants.HEAD);
    if (local == null) {
      RefUpdate ru = repo.updateRef(Constants.R_HEADS + branch);
      ru.setNewObjectId(remoteId);
      ru.setExpectedOldObjectId(ObjectId.zeroId());
      RefUpdate.Result res = ru.update();
      if (res != RefUpdate.Result.NEW && res != RefUpdate.Result.FAST_FORWARD) {
        throw new IllegalStateException("could not create " + branch + ": " + res);
      }
      git.reset().setMode(ResetCommand.ResetType.HARD).call();
      return "checked out " + abbrev(remoteId);
    }
    if (local.equals(remoteId)) {
      return "up to date";
    }
    git.add().addFilepattern(".").call();
    git.add().addFilepattern(".").setUpdate(true).call();
    if (git.status().call().hasUncommittedChanges()) {
      git.reset().call();
      throw new IllegalStateException("work tree has changes that are not committed; commit before pulling");
    }
    MergeResult m = git.merge()
        .include(remoteId)
        .setFastForward(MergeCommand.FastForwardMode.FF_ONLY)
        .setCommit(true)
        .call();
    MergeResult.MergeStatus st = m.getMergeStatus();
    if (st == MergeResult.MergeStatus.ALREADY_UP_TO_DATE) {
      return "up to date";
    }
    if (st == MergeResult.MergeStatus.FAST_FORWARD) {
      return "fast-forwarded to " + abbrev(remoteId);
    }
    throw new IllegalStateException("cannot fast-forward " + branch + " to " + REMOTE + "/" + branch
        + " (" + st + "); the histories diverged. Resolve in a Workbench clone and push.");
  }

  private void requireRemote() {
    if (!hasRemote()) {
      throw new IllegalStateException("no remote configured");
    }
  }

  static CredentialsProvider credentials(String username, String password) {
    if (blank(password)) {
      return null;
    }
    // Hosted git services accept a token as the password; the user name only has to be non-empty.
    return new UsernamePasswordCredentialsProvider(blank(username) ? "git" : username, password);
  }

  private static String abbrev(ObjectId id) {
    return id.name().substring(0, 8);
  }

  private static boolean blank(String s) {
    return s == null || s.trim().isEmpty();
  }

  @Override
  public void close() {
    git.getRepository().close();
    git.close();
  }
}
