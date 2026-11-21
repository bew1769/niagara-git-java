package com.bew1769.niagaragit;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * Pure-Java git for the station directory. JGit is EDL-licensed.
 * The working tree keeps a real config.bog zip. With store-xml, the git blob is the text container.
 */
public final class JGitStation {
    public static final class Request {
        public File station;
        public String remoteUrl = "";
        public String branch = "main";
        public String authorName = "niagara-git";
        public String authorEmail = "niagara-git@localhost";
        public String credentialUser = "git";
        public String credentialSecret = "";
        public boolean storeXml;
    }

    public static final class Outcome {
        public final String message;
        public final boolean changed;
        public final String sha;

        public Outcome(String message, boolean changed, String sha) {
            this.message = message;
            this.changed = changed;
            this.sha = sha == null ? "" : sha;
        }
    }

    private JGitStation() {
    }

    public static String init(Request req) throws IOException {
        checkBranch(req.branch);
        rejectEmbeddedSecret(req.remoteUrl);
        File station = req.station;
        if (!station.isDirectory()) {
            throw new IOException("niagara-git: station directory is not a directory");
        }
        Git git = null;
        try {
            if (!new File(station, ".git").isDirectory()) {
                git = Git.init().setDirectory(station).setInitialBranch(req.branch).call();
            } else {
                git = Git.open(station);
            }
            Repository repo = git.getRepository();
            StoredConfig config = repo.getConfig();
            config.setBoolean("core", null, "autocrlf", false);
            if (req.remoteUrl != null && req.remoteUrl.trim().length() > 0) {
                config.setString("remote", "origin", "url", req.remoteUrl.trim());
                config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*");
            }
            config.save();
            Installer.writeWorkingTreeFiles(station, Boolean.valueOf(req.storeXml));
            if (repo.resolve(Constants.HEAD) == null) {
                RefUpdate head = repo.updateRef(Constants.HEAD);
                head.link(Constants.R_HEADS + req.branch);
            }
            return "niagara-git: initialized " + station.getPath() + " on " + req.branch;
        } catch (GitAPIException e) {
            throw new IOException(scrub(e.getMessage(), req.credentialSecret), e);
        } finally {
            if (git != null) {
                git.close();
            }
        }
    }

    public static Outcome commit(Request req, boolean push) throws IOException {
        Git git = openReady(req);
        try {
            Repository repo = git.getRepository();
            Installer.writeWorkingTreeFiles(req.station, Boolean.valueOf(req.storeXml));
            Map<String, byte[]> desired = desiredBlobs(req);
            if (!sameTree(repo, desired)) {
                String message = "snapshot " + DateTimeFormatter.ISO_INSTANT.format(
                        Instant.now().truncatedTo(ChronoUnit.SECONDS));
                ObjectId id = writeCommit(repo, req, desired, message);
                String text = "niagara-git: committed " + message + " " + shortSha(id);
                if (push) {
                    push(git, req);
                    text = text + "\nniagara-git: pushed";
                }
                return new Outcome(text, true, id.getName());
            }
            String text = "niagara-git: snapshot unchanged";
            if (push) {
                push(git, req);
                text = text + "\nniagara-git: pushed";
            }
            ObjectId head = repo.resolve(Constants.HEAD);
            return new Outcome(text, false, head == null ? "" : head.getName());
        } catch (GitAPIException e) {
            throw new IOException(scrub(e.getMessage(), req.credentialSecret), e);
        } finally {
            git.close();
        }
    }

    public static Outcome pull(Request req) throws IOException {
        if (req.remoteUrl == null || req.remoteUrl.trim().isEmpty()) {
            throw new IOException("niagara-git: remote URL is not set");
        }
        Git git = openReady(req);
        try {
            Repository repo = git.getRepository();
            fetch(git, req);
            ObjectId remote = repo.resolve("refs/remotes/origin/" + req.branch);
            if (remote == null) {
                throw new IOException("niagara-git: remote has no branch " + req.branch);
            }
            ObjectId local = repo.resolve(Constants.HEAD);
            if (local != null && local.equals(remote)) {
                return new Outcome("niagara-git: already up to date " + shortSha(remote), false, remote.getName());
            }
            if (local != null && !isAncestor(repo, local, remote)) {
                throw new IOException("niagara-git: pull refused; not a fast-forward. local "
                        + local.getName() + " remote " + remote.getName()
                        + ". The station and the remote have diverged; no files were changed.");
            }
            TreeCheckout checkout = readTree(repo, remote);
            PullApplier.stage(req.station, remote.getName(), checkout.files, checkout.removals(repo, local));
            moveBranch(repo, req.branch, local, remote);
            git.reset().setMode(ResetCommand.ResetType.MIXED).setRef(remote.getName()).call();
            StringBuilder message = new StringBuilder();
            message.append("niagara-git: fast-forward to ").append(shortSha(remote));
            if (checkout.configBog) {
                message.append("\nstaged config.bog");
            }
            for (int i = 0; i < checkout.skipped.size(); i++) {
                message.append("\nskipped ").append(checkout.skipped.get(i));
            }
            return new Outcome(message.toString(), true, remote.getName());
        } catch (GitAPIException e) {
            throw new IOException(scrub(e.getMessage(), req.credentialSecret), e);
        } finally {
            git.close();
        }
    }

    public static String status(Request req) throws IOException {
        Git git = openReady(req);
        try {
            Repository repo = git.getRepository();
            ObjectId head = repo.resolve(Constants.HEAD);
            String branch = repo.getBranch();
            StringBuilder b = new StringBuilder();
            b.append("branch ").append(branch == null ? req.branch : branch).append('\n');
            b.append("head ").append(head == null ? "(none)" : head.getName()).append('\n');
            ObjectId remote = repo.resolve("refs/remotes/origin/" + req.branch);
            if (remote == null) {
                b.append("upstream (none)\n");
            } else if (head == null) {
                b.append("upstream ").append(remote.getName()).append(" (no local commits)\n");
            } else if (head.equals(remote)) {
                b.append("upstream ").append(shortSha(remote)).append(" (in sync)\n");
            } else if (isAncestor(repo, head, remote)) {
                b.append("upstream ").append(shortSha(remote)).append(" (behind)\n");
            } else if (isAncestor(repo, remote, head)) {
                b.append("upstream ").append(shortSha(remote)).append(" (ahead)\n");
            } else {
                b.append("upstream ").append(shortSha(remote)).append(" (diverged)\n");
            }
            if (head == null) {
                b.append("dirty (no commits yet)\n");
                return b.toString();
            }
            Map<String, byte[]> desired = desiredBlobs(req);
            if (sameTree(repo, desired)) {
                b.append("clean\n");
            } else {
                b.append("dirty\n");
            }
            return b.toString();
        } finally {
            git.close();
        }
    }

    private static Git openReady(Request req) throws IOException {
        checkBranch(req.branch);
        rejectEmbeddedSecret(req.remoteUrl);
        if (!new File(req.station, ".git").isDirectory()) {
            init(req);
        }
        try {
            Git git = Git.open(req.station);
            if (req.remoteUrl != null && req.remoteUrl.trim().length() > 0) {
                StoredConfig config = git.getRepository().getConfig();
                config.setString("remote", "origin", "url", req.remoteUrl.trim());
                config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*");
                config.setBoolean("core", null, "autocrlf", false);
                config.save();
            }
            return git;
        } catch (IOException e) {
            throw new IOException(scrub(e.getMessage(), req.credentialSecret), e);
        }
    }

    private static Map<String, byte[]> desiredBlobs(Request req) throws IOException {
        Map<String, byte[]> blobs = new TreeMap<String, byte[]>();
        List<StationFiles.Item> items = StationFiles.itemsFromStation(req.station, true);
        for (int i = 0; i < items.size(); i++) {
            StationFiles.Item item = items.get(i);
            byte[] data = item.data;
            if (req.storeXml && item.rel.toLowerCase(java.util.Locale.ROOT).endsWith(".bog")) {
                data = BogStore.clean(data);
            }
            blobs.put(item.rel, data);
        }
        putFile(blobs, req.station, ".gitignore");
        putFile(blobs, req.station, ".gitattributes");
        return blobs;
    }

    private static void putFile(Map<String, byte[]> blobs, File station, String name) throws IOException {
        File file = new File(station, name);
        if (file.isFile()) {
            blobs.put(name, java.nio.file.Files.readAllBytes(file.toPath()));
        }
    }

    private static boolean sameTree(Repository repo, Map<String, byte[]> desired) throws IOException {
        Map<String, ObjectId> have = headBlobs(repo);
        if (have.size() != desired.size()) {
            return false;
        }
        ObjectInserter inserter = repo.newObjectInserter();
        try {
            for (Map.Entry<String, byte[]> e : desired.entrySet()) {
                ObjectId id = inserter.idFor(Constants.OBJ_BLOB, e.getValue());
                if (!id.equals(have.get(e.getKey()))) {
                    return false;
                }
            }
        } finally {
            inserter.close();
        }
        return true;
    }

    private static Map<String, ObjectId> headBlobs(Repository repo) throws IOException {
        Map<String, ObjectId> map = new TreeMap<String, ObjectId>();
        ObjectId head = repo.resolve(Constants.HEAD);
        if (head == null) {
            return map;
        }
        RevWalk walk = new RevWalk(repo);
        try {
            RevCommit commit = walk.parseCommit(head);
            TreeWalk tw = new TreeWalk(repo);
            try {
                tw.addTree(commit.getTree());
                tw.setRecursive(true);
                while (tw.next()) {
                    map.put(tw.getPathString(), tw.getObjectId(0).copy());
                }
            } finally {
                tw.close();
            }
        } finally {
            walk.close();
        }
        return map;
    }

    private static ObjectId writeCommit(Repository repo, Request req, Map<String, byte[]> desired, String message)
            throws IOException {
        ObjectInserter inserter = repo.newObjectInserter();
        try {
            DirCache index = DirCache.newInCore();
            DirCacheBuilder builder = index.builder();
            for (Map.Entry<String, byte[]> e : desired.entrySet()) {
                ObjectId blob = inserter.insert(Constants.OBJ_BLOB, e.getValue());
                DirCacheEntry entry = new DirCacheEntry(e.getKey());
                entry.setFileMode(FileMode.REGULAR_FILE);
                entry.setObjectId(blob);
                builder.add(entry);
            }
            builder.finish();
            ObjectId tree = index.writeTree(inserter);
            PersonIdent ident = new PersonIdent(req.authorName, req.authorEmail);
            ObjectId parent = repo.resolve(Constants.HEAD);
            CommitBuilder commit = new CommitBuilder();
            commit.setTreeId(tree);
            commit.setAuthor(ident);
            commit.setCommitter(ident);
            commit.setMessage(message);
            if (parent != null) {
                commit.setParentId(parent);
            }
            ObjectId id = inserter.insert(commit);
            inserter.flush();
            RefUpdate update = repo.updateRef(Constants.R_HEADS + req.branch);
            update.setNewObjectId(id);
            update.setExpectedOldObjectId(parent == null ? ObjectId.zeroId() : parent);
            RefUpdate.Result result = update.update();
            if (result != RefUpdate.Result.NEW && result != RefUpdate.Result.FAST_FORWARD
                    && result != RefUpdate.Result.FORCED) {
                throw new IOException("niagara-git: could not update " + req.branch + ": " + result);
            }
            RefUpdate head = repo.updateRef(Constants.HEAD);
            RefUpdate.Result linked = head.link(Constants.R_HEADS + req.branch);
            if (linked != RefUpdate.Result.NEW && linked != RefUpdate.Result.FORCED
                    && linked != RefUpdate.Result.NO_CHANGE && linked != RefUpdate.Result.FAST_FORWARD) {
                throw new IOException("niagara-git: could not point HEAD at " + req.branch + ": " + linked);
            }
            return id;
        } finally {
            inserter.close();
        }
    }

    private static void fetch(Git git, Request req) throws GitAPIException, IOException {
        org.eclipse.jgit.api.FetchCommand fetch = git.fetch()
                .setRemote("origin")
                .setRefSpecs(new RefSpec("+refs/heads/" + req.branch + ":refs/remotes/origin/" + req.branch));
        CredentialsProvider credentials = credentials(req);
        if (credentials != null) {
            fetch.setCredentialsProvider(credentials);
        }
        fetch.call();
    }

    private static void push(Git git, Request req) throws GitAPIException, IOException {
        if (req.remoteUrl == null || req.remoteUrl.trim().isEmpty()) {
            throw new IOException("niagara-git: remote URL is not set");
        }
        org.eclipse.jgit.api.PushCommand push = git.push()
                .setRemote("origin")
                .setRefSpecs(new RefSpec("refs/heads/" + req.branch + ":refs/heads/" + req.branch));
        CredentialsProvider credentials = credentials(req);
        if (credentials != null) {
            push.setCredentialsProvider(credentials);
        }
        Iterable<PushResult> results = push.call();
        for (PushResult result : results) {
            for (RemoteRefUpdate update : result.getRemoteUpdates()) {
                RemoteRefUpdate.Status status = update.getStatus();
                if (status != RemoteRefUpdate.Status.OK && status != RemoteRefUpdate.Status.UP_TO_DATE) {
                    throw new IOException("niagara-git: push rejected: " + status + " "
                            + scrub(update.getMessage(), req.credentialSecret));
                }
            }
        }
    }

    private static void moveBranch(Repository repo, String branch, ObjectId oldHead, ObjectId remote) throws IOException {
        RefUpdate update = repo.updateRef(Constants.R_HEADS + branch);
        update.setNewObjectId(remote);
        ObjectId current = repo.resolve(Constants.R_HEADS + branch);
        update.setExpectedOldObjectId(current == null ? ObjectId.zeroId() : current);
        RefUpdate.Result result = update.update();
        if (result != RefUpdate.Result.NEW && result != RefUpdate.Result.FAST_FORWARD
                && result != RefUpdate.Result.NO_CHANGE) {
            throw new IOException("niagara-git: could not fast-forward " + branch + ": " + result);
        }
        RefUpdate head = repo.updateRef(Constants.HEAD);
        head.link(Constants.R_HEADS + branch);
        if (oldHead != null && current != null && !current.equals(oldHead) && !current.equals(remote)) {
            // The branch ref was not HEAD. The fast-forward check used HEAD.
            // Leave the updated branch in place; HEAD now names it.
        }
    }

    private static boolean isAncestor(Repository repo, ObjectId ancestor, ObjectId descendant) throws IOException {
        RevWalk walk = new RevWalk(repo);
        try {
            RevCommit older = walk.parseCommit(ancestor);
            RevCommit newer = walk.parseCommit(descendant);
            return walk.isMergedInto(older, newer);
        } finally {
            walk.close();
        }
    }

    private static TreeCheckout readTree(Repository repo, ObjectId commitId) throws IOException {
        TreeCheckout checkout = new TreeCheckout();
        RevWalk walk = new RevWalk(repo);
        try {
            RevCommit commit = walk.parseCommit(commitId);
            TreeWalk tw = new TreeWalk(repo);
            try {
                tw.addTree(commit.getTree());
                tw.setRecursive(true);
                while (tw.next()) {
                    String path = tw.getPathString();
                    FileMode mode = tw.getFileMode(0);
                    if (mode == FileMode.SYMLINK || mode == FileMode.GITLINK) {
                        throw new IOException("niagara-git: pull refused; symlink or submodule " + path);
                    }
                    if (path.startsWith(".niagara-git/") || ".niagara-git".equals(path)) {
                        continue;
                    }
                    Classifier.Result classified = Classifier.classify(path);
                    if (classified.kind == Kind.UNSAFE) {
                        throw new IOException("niagara-git: pull refused; unsafe path " + path);
                    }
                    if (classified.kind != Kind.SHOW) {
                        checkout.skipped.add(classified.kind + " " + classified.display);
                        continue;
                    }
                    ObjectLoader loader = repo.open(tw.getObjectId(0));
                    if (loader.getSize() > NiagaraGit.MAX_ENTRY_BYTES) {
                        throw new IOException("niagara-git: pull refused; " + path + " is larger than "
                                + NiagaraGit.MAX_ENTRY_BYTES + " bytes");
                    }
                    byte[] data = loader.getBytes();
                    if (path.toLowerCase(java.util.Locale.ROOT).endsWith(".bog") && BogStore.isStore(data)) {
                        data = BogStore.smudge(data);
                    }
                    checkout.files.add(new StationFiles.Item(path, data));
                    if ("config.bog".equals(path)) {
                        checkout.configBog = true;
                    }
                }
            } finally {
                tw.close();
            }
        } finally {
            walk.close();
        }
        return checkout;
    }

    private static CredentialsProvider credentials(Request req) {
        if (req.credentialSecret == null || req.credentialSecret.isEmpty()) {
            return null;
        }
        String user = req.credentialUser == null || req.credentialUser.isEmpty() ? "git" : req.credentialUser;
        return new UsernamePasswordCredentialsProvider(user, req.credentialSecret);
    }

    private static void checkBranch(String branch) throws IOException {
        if (branch == null || branch.isEmpty() || branch.indexOf(' ') >= 0 || branch.indexOf('\\') >= 0
                || branch.contains("..") || branch.startsWith("-") || branch.startsWith("/")
                || branch.endsWith("/") || branch.endsWith(".lock")) {
            throw new IOException("niagara-git: bad branch name");
        }
    }

    static void rejectEmbeddedSecret(String url) throws IOException {
        if (url == null) {
            return;
        }
        int scheme = url.indexOf("://");
        if (scheme < 0) {
            return;
        }
        int at = url.indexOf('@', scheme + 3);
        if (at < 0) {
            return;
        }
        String userinfo = url.substring(scheme + 3, at);
        if (userinfo.indexOf(':') >= 0) {
            throw new IOException("niagara-git: put the token in the credentials property, not in the remote URL");
        }
    }

    static String scrub(String message, String secret) {
        if (message == null) {
            return "";
        }
        if (secret != null && secret.length() > 0) {
            return message.replace(secret, "****");
        }
        return message;
    }

    private static String shortSha(ObjectId id) {
        String name = id.getName();
        return name.length() > 7 ? name.substring(0, 7) : name;
    }

    private static final class TreeCheckout {
        final List<StationFiles.Item> files = new ArrayList<StationFiles.Item>();
        final List<String> skipped = new ArrayList<String>();
        boolean configBog;

        List<String> removals(Repository repo, ObjectId oldHead) throws IOException {
            List<String> gone = new ArrayList<String>();
            if (oldHead == null) {
                return gone;
            }
            Map<String, ObjectId> previous = headBlobs(repo);
            for (Map.Entry<String, ObjectId> e : previous.entrySet()) {
                String path = e.getKey();
                if (!PullApplier.managed(path)) {
                    continue;
                }
                if (Classifier.classify(path).kind != Kind.SHOW) {
                    continue;
                }
                boolean still = false;
                for (int i = 0; i < files.size(); i++) {
                    if (files.get(i).rel.equals(path)) {
                        still = true;
                        break;
                    }
                }
                if (!still) {
                    gone.add(path);
                }
            }
            return gone;
        }
    }
}
