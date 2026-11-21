package com.bew1769.niagaragit;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CoreTest {
    @Test
    public void classifyStationFilesAreShown() {
        String[] names = {
                "niagara_user_home/stations/demoPlant/config.bog",
                "niagara_user_home/stations/demoPlant/shared/px/AHU1.px",
                "niagara_user_home/stations/demoPlant/shared/nav/demoPlant.nav",
                "niagara_user_home/stations/demoPlant/shared/palette/demoPlant.palette"
        };
        for (int i = 0; i < names.length; i++) {
            Classifier.Result got = Classifier.classify(names[i]);
            assertEquals(Kind.SHOW, got.kind);
            assertEquals(names[i], got.display);
        }
    }

    @Test
    public void classifyNoiseSecretsAndUnsafe() {
        assertEquals("history", Classifier.noisyCategory(Classifier.classify(
                "niagara_user_home/stations/demoPlant/history/AHU1_SupplyTemp.bin").display));
        assertEquals("alarm", Classifier.noisyCategory(Classifier.classify(
                "niagara_user_home/stations/demoPlant/alarm/alarmData.bin").display));
        assertEquals("logs", Classifier.noisyCategory(Classifier.classify(
                "niagara_home/logs/station.out").display));
        assertEquals("config.backup", Classifier.noisyCategory(Classifier.classify(
                "niagara_user_home/stations/demoPlant/config.backup.20200101_0000.bog").display));
        assertEquals("config.backup", Classifier.noisyCategory(Classifier.classify(
                "stations/demoPlant/config_backup_200548_0042.bog").display));
        assertEquals("*.log", Classifier.noisyCategory(Classifier.classify("shared/notes.log").display));
        assertEquals(Kind.SECRET, Classifier.classify("niagara_home/logs/keystore.jceks").kind);
        String[] secrets = {
                "niagara_user_home/security/keystore.jceks",
                "niagara_user_home/security/cacerts.jceks",
                "niagara_user_home/security/exemptions.tes",
                "niagara_user_home/certManagement/demo-client.pem",
                "exported/client.p12",
                "exported/client.bcfks"
        };
        for (int i = 0; i < secrets.length; i++) {
            assertEquals(Kind.SECRET, Classifier.classify(secrets[i]).kind, secrets[i]);
        }
        assertEquals(Kind.SHOW, Classifier.classify("stations/demoPlant/config.bog").kind);
        assertEquals(Kind.UNSAFE, Classifier.classify("../outside.pem").kind);
        assertEquals(Kind.UNSAFE, Classifier.classify("/etc/passwd").kind);
        assertEquals(Kind.UNSAFE, Classifier.classify("C:/Users/secret.pem").kind);
        Classifier.Result normalized = Classifier.classify("stations//demo/./config.bog");
        assertEquals(Kind.SHOW, normalized.kind);
        assertEquals("stations/demo/config.bog", normalized.display);
    }

    @Test
    public void prettySortsAttributesAndRefusesDoctype() throws Exception {
        byte[] raw = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<bajaObjectGraph version=\"4.0\"><p t=\"b:Folder\" n=\"AHU1\" h=\"2\">"
                + "<!-- supply air --><p n=\"SupplyTemp\" t=\"control:NumericWritable\" h=\"3\">"
                + "<p v=\"72.0\" n=\"fallback\"/></p></p></bajaObjectGraph>").getBytes(StandardCharsets.UTF_8);
        String want = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<bajaObjectGraph version=\"4.0\">\n"
                + "  <p h=\"2\" n=\"AHU1\" t=\"b:Folder\">\n"
                + "    <!-- supply air -->\n"
                + "    <p h=\"3\" n=\"SupplyTemp\" t=\"control:NumericWritable\">\n"
                + "      <p n=\"fallback\" v=\"72.0\"/>\n"
                + "    </p>\n"
                + "  </p>\n"
                + "</bajaObjectGraph>\n";
        assertEquals(want, XmlFmt.pretty(raw));
        byte[] order = "<root><p n=\"second\"/><p n=\"first\"/></root>".getBytes(StandardCharsets.UTF_8);
        String text = XmlFmt.pretty(order);
        assertTrue(text.indexOf("n=\"second\"") < text.indexOf("n=\"first\""));
        byte[] escaped = "<root><p n=\"note\" v=\"a&amp;b&quot;c&lt;d\"/></root>".getBytes(StandardCharsets.UTF_8);
        assertTrue(XmlFmt.pretty(escaped).contains("v=\"a&amp;b&quot;c&lt;d\""));
        byte[] onceRaw = "<root><p n=\"A\" h=\"1\"><p v=\"x\" n=\"b\"/></p></root>".getBytes(StandardCharsets.UTF_8);
        String once = XmlFmt.pretty(onceRaw);
        assertEquals(once, XmlFmt.pretty(once.getBytes(StandardCharsets.UTF_8)));
        String crlf = XmlFmt.pretty("<root>\r\n  <p a=\"2\" b=\"1\"/>\r\n</root>".getBytes(StandardCharsets.UTF_8));
        assertEquals(XmlFmt.pretty("<root><p b=\"1\" a=\"2\"/></root>".getBytes(StandardCharsets.UTF_8)), crlf);
        assertFalse(crlf.contains("\r"));
        byte[] dtd = "<!DOCTYPE bajaObjectGraph [<!ENTITY x \"y\">]><root>&x;</root>".getBytes(StandardCharsets.UTF_8);
        assertThrows(javax.xml.stream.XMLStreamException.class, () -> XmlFmt.pretty(dtd));
        String shown = Convert.bog(dtd);
        assertTrue(shown.contains("DTD or entity declarations are not expanded"));
        assertTrue(shown.contains("<!DOCTYPE"));
        String newline = XmlFmt.pretty("<root><p v=\"line1&#10;line2\"/></root>".getBytes(StandardCharsets.UTF_8));
        assertTrue(newline.contains("&#10;"));
    }

    @Test
    public void cleanSmudgePreservesDemoBogAndOrder() throws Exception {
        byte[] original = fixture("examples/demoPlant/station/config.bog");
        byte[] stored = BogStore.clean(original);
        assertTrue(BogStore.isStore(stored));
        assertArrayEquals(stored, BogStore.clean(stored));
        byte[] rebuilt = BogStore.smudge(stored);
        assertEntriesEqual(original, rebuilt);
        assertArrayEquals(rebuilt, BogStore.smudge(stored));
        assertArrayEquals(stored, BogStore.clean(rebuilt));
        assertEquals(Convert.bog(original), Convert.bog(stored));
        assertArrayEquals(original, BogStore.smudge(original));

        byte[] xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><root><p n=\"A\" v=\"1\"/></root>"
                .getBytes(StandardCharsets.UTF_8);
        byte[] binary = new byte[] {0x00, (byte) 0x89, (byte) 0xff, 'P', 'N', 'G'};
        byte[] zip = zipWithComment("station comment", new String[] {
                "notes/readme.txt", "dir/", "file.xml", "images/mark.bin"
        }, new byte[][] {
                "keep me first".getBytes(StandardCharsets.UTF_8),
                new byte[0],
                xml,
                binary
        });
        byte[] cleaned = BogStore.clean(zip);
        assertFalse(indexOf(cleaned, binary) >= 0);
        byte[] again = BogStore.smudge(cleaned);
        assertEntriesEqual(zip, again);
        BogStore.Parsed parsed = BogStore.parse(cleaned);
        assertEquals("station comment", parsed.comment);
        assertEquals("notes/readme.txt", parsed.entries.get(0).name);
        assertEquals("file.xml", parsed.entries.get(2).name);
        assertEquals("images/mark.bin", parsed.entries.get(3).name);
    }

    @Test
    public void goldensMatchTheGoTool() throws Exception {
        assertEquals(textFixture("testdata/bog-v1.txt"), Convert.bog(fixture("examples/demoPlant/station/config.bog")));
        assertEquals(textFixture("testdata/bog-v2.txt"), Convert.bog(fixture("examples/demoPlant/station-v2/config.bog")));
        assertEquals(textFixture("testdata/dist-v1.txt"), Convert.dist(fixture("examples/demoPlant/backups/demoPlant-v1.dist")));
        assertEquals(textFixture("testdata/dist-v2.txt"), Convert.dist(fixture("examples/demoPlant/backups/demoPlant-v2.dist")));
        String v1 = Convert.bog(fixture("examples/demoPlant/station/config.bog"));
        assertTrue(v1.contains("SYNTHETIC-NOT-A-REAL-HASH"));
        assertTrue(v1.contains("v=\"72.0\""));
        assertFalse(v1.contains("v=\"70.0\""));
        String dist = Convert.dist(fixture("examples/demoPlant/backups/demoPlant-v1.dist"));
        assertFalse(dist.contains("synthetic-keystore-not-a-real-jceks"));
        assertFalse(dist.contains("-----BEGIN CERTIFICATE-----"));
        byte[] opaque = "not a bog \u0000 encrypted-looking".getBytes(StandardCharsets.ISO_8859_1);
        String hidden = Convert.bog(opaque);
        assertTrue(hidden.contains("passphrase-protected"));
        assertTrue(hidden.contains(Digests.sha256(opaque)));
        assertFalse(hidden.contains("\u0000"));
    }

    @Test
    public void mergeBlockIsIdempotent() {
        String block = "# niagara-git:begin\n*.bog diff=niagara-bog\n# niagara-git:end\n";
        String first = TextMerge.mergeBlock("# local\n", block);
        assertEquals(first, TextMerge.mergeBlock(first, block));
        assertEquals(1, count(first, "# niagara-git:begin"));
        String updated = block.replace("niagara-bog", "niagara-bog-v2");
        String third = TextMerge.mergeBlock(first, updated);
        assertTrue(third.contains("niagara-bog-v2"));
        assertTrue(third.startsWith("# local\n"));
        assertEquals("'a b'", TextMerge.shellQuote("a b"));
        assertEquals("'it'\\''s'", TextMerge.shellQuote("it's"));
    }

    @Test
    public void restartGuardStopsALoopAndApplySurvivesASave(@TempDir File dir) throws Exception {
        assertTrue(RestartGuard.arm(dir, "abc"));
        assertTrue(RestartGuard.arm(dir, "abc"));
        assertFalse(RestartGuard.arm(dir, "abc"));
        assertTrue(RestartGuard.arm(dir, "def"));

        File station = new File(dir, "station");
        assertTrue(station.mkdir());
        byte[] pulled = "NEW-BOG".getBytes(StandardCharsets.UTF_8);
        PullApplier.stage(station, "sha-new",
                java.util.Collections.singletonList(new StationFiles.Item("config.bog", pulled)),
                java.util.Collections.<String>emptyList());
        write(new File(station, "config.bog"), "OLD-MEMORY".getBytes(StandardCharsets.UTF_8));
        assertTrue(PullApplier.needsApply(station));
        PullApplier.apply(station);
        assertArrayEquals(pulled, Files.readAllBytes(new File(station, "config.bog").toPath()));
        write(new File(station, "config.bog"), "SHUTDOWN-SAVE".getBytes(StandardCharsets.UTF_8));
        PullApplier.apply(station);
        assertArrayEquals(pulled, Files.readAllBytes(new File(station, "config.bog").toPath()));
        File escaped = new File(dir, "outside.txt");
        assertThrows(java.io.IOException.class, () -> Paths.resolveInside(station, "../outside.txt"));
        assertFalse(escaped.exists());
    }

    @Test
    public void jgitSkipsSecretsFastForwardsAndRefusesDivergence(@TempDir File root) throws Exception {
        byte[] v1 = fixture("examples/demoPlant/station/config.bog");
        byte[] v2 = fixture("examples/demoPlant/station-v2/config.bog");
        File bare = new File(root, "bare.git");
        Git.init().setDirectory(bare).setBare(true).call().close();

        File station = new File(root, "station");
        assertTrue(station.mkdir());
        write(new File(station, "config.bog"), v1);
        File secret = new File(station, "shared/security/keystore.jceks");
        secret.getParentFile().mkdirs();
        write(secret, "synthetic-keystore-not-a-real-jceks".getBytes(StandardCharsets.UTF_8));
        write(new File(station, "shared/px/AHU1.px"), "<px/>".getBytes(StandardCharsets.UTF_8));
        new File(station, "history").mkdirs();
        write(new File(station, "history/trend.bin"), "synthetic-history".getBytes(StandardCharsets.UTF_8));

        JGitStation.Request req = request(station, bare);
        req.storeXml = true;
        assertTrue(JGitStation.init(req).contains("initialized"));
        JGitStation.Outcome first = JGitStation.commit(req, true);
        assertTrue(first.message.contains("committed"));
        assertFalse(treeHas(station, "keystore.jceks"));
        assertFalse(treeHas(station, "trend.bin"));
        assertTrue(treeHas(station, "shared/px/AHU1.px"));
        byte[] blob = blob(station, "config.bog");
        assertTrue(BogStore.isStore(blob));
        assertTrue(new String(Files.readAllBytes(new File(station, "config.bog").toPath()), StandardCharsets.ISO_8859_1)
                .startsWith("PK"));
        JGitStation.Outcome again = JGitStation.commit(req, false);
        assertTrue(again.message.contains("unchanged"));

        File other = new File(root, "other");
        assertTrue(other.mkdir());
        write(new File(other, "config.bog"), v1);
        JGitStation.Request otherReq = request(other, bare);
        JGitStation.init(otherReq);
        JGitStation.Outcome pulled = JGitStation.pull(otherReq);
        assertTrue(pulled.changed);
        PullApplier.apply(other);
        assertTrue(Convert.bog(Files.readAllBytes(new File(other, "config.bog").toPath())).contains("v=\"72.0\""));

        write(new File(station, "config.bog"), v2);
        JGitStation.Outcome pushed = JGitStation.commit(req, true);
        assertTrue(pushed.changed);
        JGitStation.Outcome ff = JGitStation.pull(otherReq);
        assertTrue(ff.changed);
        assertTrue(ff.message.contains("staged config.bog"));
        PullApplier.apply(other);
        assertTrue(Convert.bog(Files.readAllBytes(new File(other, "config.bog").toPath())).contains("v=\"70.0\""));

        write(new File(other, "config.bog"), v1);
        JGitStation.commit(otherReq, false);
        java.io.IOException diverged = assertThrows(java.io.IOException.class, () -> JGitStation.pull(otherReq));
        assertTrue(diverged.getMessage().contains("not a fast-forward"));
        assertTrue(diverged.getMessage().contains("diverged"));
    }

    @Test
    public void pullRestoresBogAfterSaveAndGuardsRestart(@TempDir File root) throws Exception {
        byte[] v1 = fixture("examples/demoPlant/station/config.bog");
        byte[] v2 = fixture("examples/demoPlant/station-v2/config.bog");
        File bare = new File(root, "bare.git");
        Git.init().setDirectory(bare).setBare(true).call().close();
        File station = new File(root, "station");
        assertTrue(station.mkdir());
        write(new File(station, "config.bog"), v1);
        JGitStation.Request req = request(station, bare);
        JGitStation.init(req);
        JGitStation.commit(req, true);
        write(new File(station, "config.bog"), v2);
        JGitStation.commit(req, true);

        File target = new File(root, "target");
        assertTrue(target.mkdir());
        write(new File(target, "config.bog"), v1);
        final boolean[] restarted = new boolean[] {false};
        final byte[] memory = v1;
        StationWorkflow.Host host = new StationWorkflow.Host() {
            public File stationHome() {
                return target;
            }

            public void save() {
                try {
                    write(new File(target, "config.bog"), memory);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            public void restart() {
                restarted[0] = true;
                try {
                    PullApplier.apply(target);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        StationWorkflow.Options options = new StationWorkflow.Options();
        options.remoteUrl = bare.getAbsolutePath();
        options.branch = "main";
        options.restartAfterPull = true;
        String message = StationWorkflow.pull(host, options);
        assertTrue(message.contains("restart requested"));
        assertTrue(restarted[0]);
        assertArrayEquals(v2, Files.readAllBytes(new File(target, "config.bog").toPath()));
        assertTrue(StationWorkflow.onStarted(host).isEmpty());
    }

    @Test
    @EnabledIf("gitExists")
    public void cliSnapshotSkipsSecrets(@TempDir File root) throws Exception {
        File station = new File(root, "station");
        assertTrue(new File(station, "shared/px").mkdirs());
        copyFixture("examples/demoPlant/station/config.bog", new File(station, "config.bog"));
        copyFixture("examples/demoPlant/station/shared/px/AHU1.px", new File(station, "shared/px/AHU1.px"));
        File secret = new File(station, "shared/security/keystore.jceks");
        secret.getParentFile().mkdirs();
        write(secret, "synthetic-keystore-not-a-real-jceks".getBytes(StandardCharsets.UTF_8));
        File repo = new File(root, "repo");
        Snapshot.Options opts = new Snapshot.Options();
        opts.source = station.getPath();
        opts.repo = repo.getPath();
        opts.shared = true;
        Snapshot.run(opts, "niagara-git");
        String files = GitCli.output(repo, "ls-files");
        assertTrue(files.contains("config.bog"));
        assertTrue(files.contains("shared/px/AHU1.px"));
        assertTrue(files.contains(".gitignore"));
        assertFalse(files.contains("keystore.jceks"));
        assertFalse(files.contains("history/"));
        Snapshot.run(opts, "niagara-git");
        assertEquals("1\n", GitCli.output(repo, "rev-list", "--count", "HEAD"));
    }

    static boolean gitExists() {
        try {
            Process process = new ProcessBuilder("git", "--version").start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static JGitStation.Request request(File station, File bare) {
        JGitStation.Request req = new JGitStation.Request();
        req.station = station;
        req.remoteUrl = bare.getAbsolutePath();
        req.branch = "main";
        req.authorName = "niagara-git tests";
        req.authorEmail = "niagara-git@example.com";
        return req;
    }

    private static boolean treeHas(File station, String needle) throws Exception {
        Git git = Git.open(station);
        try {
            Repository repo = git.getRepository();
            ObjectId head = repo.resolve("HEAD");
            RevWalk walk = new RevWalk(repo);
            try {
                RevCommit commit = walk.parseCommit(head);
                TreeWalk tw = new TreeWalk(repo);
                try {
                    tw.addTree(commit.getTree());
                    tw.setRecursive(true);
                    while (tw.next()) {
                        if (tw.getPathString().contains(needle)) {
                            return true;
                        }
                    }
                } finally {
                    tw.close();
                }
            } finally {
                walk.close();
            }
            return false;
        } finally {
            git.close();
        }
    }

    private static byte[] blob(File station, String path) throws Exception {
        Git git = Git.open(station);
        try {
            Repository repo = git.getRepository();
            ObjectId head = repo.resolve("HEAD");
            RevWalk walk = new RevWalk(repo);
            try {
                RevCommit commit = walk.parseCommit(head);
                TreeWalk tw = new TreeWalk(repo);
                try {
                    tw.addTree(commit.getTree());
                    tw.setRecursive(true);
                    while (tw.next()) {
                        if (path.equals(tw.getPathString())) {
                            ObjectLoader loader = repo.open(tw.getObjectId(0));
                            return loader.getBytes();
                        }
                    }
                } finally {
                    tw.close();
                }
            } finally {
                walk.close();
            }
            throw new java.io.FileNotFoundException(path);
        } finally {
            git.close();
        }
    }

    private static void assertEntriesEqual(byte[] left, byte[] right) throws Exception {
        ZipArchives.Archive a = ZipArchives.read(left);
        ZipArchives.Archive b = ZipArchives.read(right);
        assertEquals(a.comment, b.comment);
        assertEquals(a.entries.size(), b.entries.size());
        for (int i = 0; i < a.entries.size(); i++) {
            assertEquals(a.entries.get(i).name, b.entries.get(i).name);
            assertArrayEquals(a.entries.get(i).data, b.entries.get(i).data);
        }
    }

    private static byte[] zipWithComment(String comment, String[] names, byte[][] data) throws Exception {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        ZipOutputStream out = new ZipOutputStream(buf);
        out.setComment(comment);
        for (int i = 0; i < names.length; i++) {
            ZipEntry entry = new ZipEntry(names[i]);
            if (names[i].endsWith("/")) {
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(0);
                entry.setCrc(0);
            }
            entry.setTime(ZipArchives.fixedDosTime());
            out.putNextEntry(entry);
            if (data[i] != null && data[i].length > 0) {
                out.write(data[i]);
            }
            out.closeEntry();
        }
        out.close();
        return buf.toByteArray();
    }

    private static int indexOf(byte[] data, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= data.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static int count(String text, String needle) {
        int n = 0;
        int i = 0;
        while (true) {
            int at = text.indexOf(needle, i);
            if (at < 0) {
                return n;
            }
            n++;
            i = at + needle.length();
        }
    }

    static byte[] fixture(String path) throws Exception {
        InputStream in = CoreTest.class.getResourceAsStream("/fixtures/" + path);
        if (in == null) {
            throw new java.io.FileNotFoundException(path);
        }
        try {
            return ZipArchives.readLimited(in, 32L * 1024L * 1024L);
        } finally {
            in.close();
        }
    }

    private static String textFixture(String path) throws Exception {
        return new String(fixture(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static void copyFixture(String path, File dest) throws Exception {
        dest.getParentFile().mkdirs();
        write(dest, fixture(path));
    }

    private static void write(File dest, byte[] data) throws Exception {
        dest.getParentFile().mkdirs();
        FileOutputStream out = new FileOutputStream(dest);
        try {
            out.write(data);
        } finally {
            out.close();
        }
    }

    @SuppressWarnings("unused")
    private static byte[] readAll(InputStream in) throws Exception {
        return ZipArchives.readLimited(in, 1024);
    }

    @SuppressWarnings("unused")
    private static void silence(ByteArrayInputStream in) {
    }
}
