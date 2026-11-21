package com.bew1769.niagaragit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Merge the embedded templates and register local diff/filter config. */
public final class Installer {
    private Installer() {
    }

    /**
     * @param storeXml {@code null} leaves the filter choice alone, {@code TRUE} opts in,
     *                 {@code FALSE} removes it
     * @param commandPrefix command that re-invokes this program, without a trailing mode
     */
    public static void install(String repoArg, Boolean storeXml, String commandPrefix) throws IOException {
        File root = resolveRepo(repoArg);
        mergeTemplate(new File(root, ".gitattributes"), Templates.ATTRIBUTES);
        mergeTemplate(new File(root, ".gitignore"), Templates.IGNORE);
        String bog = textconvCommand(commandPrefix, "bog");
        String dist = textconvCommand(commandPrefix, "dist");
        gitConfig(root, "diff.niagara-bog.textconv", bog);
        gitConfig(root, "diff.niagara-bog.cachetextconv", "true");
        gitConfig(root, "diff.niagara-dist.textconv", dist);
        gitConfig(root, "diff.niagara-dist.cachetextconv", "true");
        if (storeXml != null && storeXml.booleanValue()) {
            mergeMarkedFile(new File(root, ".gitattributes"), TextMerge.STORE_BEGIN, TextMerge.STORE_END,
                    Templates.ATTRIBUTES_STORE);
            gitConfig(root, "filter.niagara-store.clean", filterCommand(commandPrefix, "clean"));
            gitConfig(root, "filter.niagara-store.smudge", filterCommand(commandPrefix, "smudge"));
            gitConfig(root, "filter.niagara-store.required", "true");
        } else if (storeXml != null) {
            removeMarkedFile(new File(root, ".gitattributes"), TextMerge.STORE_BEGIN, TextMerge.STORE_END);
            gitUnset(root, "filter.niagara-store.clean");
            gitUnset(root, "filter.niagara-store.smudge");
            gitUnset(root, "filter.niagara-store.required");
        }
        System.err.print("niagara-git: configured " + root.getPath() + "\n");
        System.err.print("niagara-git: commit .gitattributes and .gitignore. Each clone must run the installer; the diff command is local git config.\n");
        if (storeXml != null && storeXml.booleanValue()) {
            System.err.print("niagara-git: .bog blobs will be stored as text and rebuilt as zip files on checkout.\n");
        }
    }

    /**
     * Writes the embedded ignore and attributes files without touching git config.
     * The station service uses this because the JACE repo is read by JGit, not by a git binary.
     */
    public static void writeWorkingTreeFiles(File root, Boolean storeXml) throws IOException {
        mergeTemplate(new File(root, ".gitattributes"), Templates.ATTRIBUTES);
        mergeTemplate(new File(root, ".gitignore"), Templates.IGNORE);
        if (storeXml != null && storeXml.booleanValue()) {
            mergeMarkedFile(new File(root, ".gitattributes"), TextMerge.STORE_BEGIN, TextMerge.STORE_END,
                    Templates.ATTRIBUTES_STORE);
        } else if (storeXml != null) {
            removeMarkedFile(new File(root, ".gitattributes"), TextMerge.STORE_BEGIN, TextMerge.STORE_END);
        }
        ensureIgnoreLine(new File(root, ".gitignore"), ".niagara-git/");
    }

    private static void ensureIgnoreLine(File gitignore, String line) throws IOException {
        String text = read(gitignore);
        String normalized = text.replace("\r\n", "\n");
        String[] lines = normalized.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().equals(line)) {
                return;
            }
        }
        if (normalized.length() > 0 && !normalized.endsWith("\n")) {
            normalized = normalized + "\n";
        }
        if (normalized.length() > 0 && !normalized.endsWith("\n\n")) {
            normalized = normalized + "\n";
        }
        write(gitignore, normalized + "# Station service bookkeeping. Not part of the station database.\n" + line + "\n");
    }

    public static String currentCommandPrefix() {
        String javaBin = javaBinary();
        String jar = runningJar();
        if (jar != null) {
            return TextMerge.shellQuote(Paths.slash(javaBin)) + " -jar " + TextMerge.shellQuote(Paths.slash(jar));
        }
        String cp = System.getProperty("java.class.path", ".");
        return TextMerge.shellQuote(Paths.slash(javaBin)) + " -cp " + TextMerge.shellQuote(cp)
                + " com.bew1769.niagaragit.cli.Main";
    }

    static String textconvCommand(String prefix, String mode) {
        return prefix + " --format " + NiagaraGit.FORMAT_VERSION + " " + mode;
    }

    static String filterCommand(String prefix, String mode) {
        return prefix + " " + mode;
    }

    private static void mergeTemplate(File dest, String block) throws IOException {
        write(dest, TextMerge.mergeBlock(read(dest), block));
    }

    private static void mergeMarkedFile(File dest, String begin, String end, String block) throws IOException {
        write(dest, TextMerge.mergeMarked(read(dest), begin, end, block));
    }

    private static void removeMarkedFile(File dest, String begin, String end) throws IOException {
        if (!dest.isFile()) {
            return;
        }
        write(dest, TextMerge.removeMarked(read(dest), begin, end));
    }

    static File resolveRepo(String repo) throws IOException {
        if (repo == null || repo.isEmpty()) {
            try {
                String out = GitCli.output(null, "rev-parse", "--show-toplevel");
                return new File(out.trim());
            } catch (IOException e) {
                throw new IOException("niagara-git: not a git repository. Pass the station repo path, or run git init first.\n");
            }
        }
        File abs = new File(Paths.expandUser(repo)).getAbsoluteFile();
        if (!abs.isDirectory()) {
            throw new IOException("niagara-git: not a directory: " + abs.getPath() + "\n");
        }
        try {
            String out = GitCli.output(abs, "rev-parse", "--show-toplevel");
            return new File(out.trim());
        } catch (IOException e) {
            throw new IOException("niagara-git: " + abs.getPath() + " is not a git repository. Run git init there first.\n");
        }
    }

    private static void gitConfig(File repo, String key, String value) throws IOException {
        GitCli.run(repo, "config", "--local", key, value);
    }

    private static void gitUnset(File repo, String key) {
        GitCli.succeeds(repo, "config", "--unset", key);
    }

    private static String read(File dest) throws IOException {
        if (!dest.isFile()) {
            return "";
        }
        return new String(java.nio.file.Files.readAllBytes(dest.toPath()), StandardCharsets.UTF_8);
    }

    private static void write(File dest, String text) throws IOException {
        File parent = dest.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("cannot create " + parent);
        }
        FileOutputStream out = new FileOutputStream(dest);
        try {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } finally {
            out.close();
        }
    }

    private static String javaBinary() {
        String home = System.getProperty("java.home");
        String name = Paths.isWindows() ? "java.exe" : "java";
        return new File(new File(home, "bin"), name).getPath();
    }

    private static String runningJar() {
        try {
            java.net.URL url = Installer.class.getProtectionDomain().getCodeSource().getLocation();
            if (url == null) {
                return null;
            }
            File file = new File(url.toURI());
            if (file.isFile() && file.getName().endsWith(".jar")) {
                return file.getAbsolutePath();
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }
}
