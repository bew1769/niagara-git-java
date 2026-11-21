package com.bew1769.niagaragit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** CLI snapshot. Copies a station into a git repo and commits when bytes changed. */
public final class Snapshot {
    public static final class Options {
        public String source;
        public String repo;
        public boolean shared;
        public boolean push;
    }

    private Snapshot() {
    }

    public static void run(Options opts, String commandPrefix) throws IOException {
        if (opts.source == null || opts.source.isEmpty() || opts.repo == null || opts.repo.isEmpty()) {
            throw new IOException("niagara-git: snapshot requires --source and --repo\n");
        }
        File root = ensureRepo(opts.repo);
        Installer.install(root.getPath(), null, commandPrefix);
        List<StationFiles.Item> items = StationFiles.collect(new File(opts.source), opts.shared);
        List<String> rels = new ArrayList<String>();
        for (int i = 0; i < items.size(); i++) {
            writeFile(root, items.get(i));
            rels.add(items.get(i).rel);
        }
        List<String> add = new ArrayList<String>();
        add.add("add");
        add.add("--");
        add.add(".gitignore");
        add.add(".gitattributes");
        add.addAll(rels);
        GitCli.run(root, add.toArray(new String[add.size()]));
        if (!GitCli.cachedDirty(root)) {
            System.err.print("niagara-git: snapshot unchanged\n");
            return;
        }
        String msg = "snapshot " + DateTimeFormatter.ISO_INSTANT.format(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        GitCli.run(root, "commit", "-m", msg);
        System.err.print("niagara-git: committed " + msg + "\n");
        if (opts.push) {
            push(root);
            System.err.print("niagara-git: pushed\n");
        }
    }

    static void writeFile(File repo, StationFiles.Item item) throws IOException {
        if (Classifier.classify(item.rel).kind != Kind.SHOW) {
            throw new IOException("niagara-git: snapshot refused " + item.rel);
        }
        File dest = Paths.resolveInside(repo, item.rel);
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

    static File ensureRepo(String path) throws IOException {
        File abs = new File(Paths.expandUser(path)).getAbsoluteFile();
        boolean created = false;
        if (!abs.exists()) {
            if (!abs.mkdirs()) {
                throw new IOException("cannot create " + abs);
            }
            created = true;
        } else if (!abs.isDirectory()) {
            throw new IOException("niagara-git: snapshot: not a directory: " + abs.getPath() + "\n");
        }
        File root = Paths.canonical(abs);
        if (!GitCli.succeeds(root, "rev-parse", "--is-inside-work-tree")) {
            GitCli.run(root, "init");
            created = true;
        }
        if (created) {
            try {
                GitCli.run(root, "config", "core.autocrlf", "false");
            } catch (IOException ignored) {
                // Windows Git rewrites text. Best effort, same as the Go tool.
            }
        }
        String topOut = GitCli.output(root, "rev-parse", "--show-toplevel");
        File top = Paths.canonical(new File(topOut.trim()));
        if (!Paths.sameDir(top, root)) {
            throw new IOException("niagara-git: snapshot: " + root.getPath() + " is inside " + top.getPath()
                    + "; pass the repository root\n");
        }
        return root;
    }

    private static void push(File repo) throws IOException {
        if (!GitCli.succeeds(repo, "rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}")) {
            GitCli.run(repo, "push", "-u", "origin", "HEAD");
        } else {
            GitCli.run(repo, "push");
        }
    }
}
