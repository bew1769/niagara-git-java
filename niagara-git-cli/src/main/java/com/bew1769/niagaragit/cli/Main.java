package com.bew1769.niagaragit.cli;

import com.bew1769.niagaragit.BogStore;
import com.bew1769.niagaragit.Convert;
import com.bew1769.niagaragit.Installer;
import com.bew1769.niagaragit.NiagaraGit;
import com.bew1769.niagaragit.Snapshot;
import com.bew1769.niagaragit.UsageException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/** {@code java -jar niagara-git.jar bog|dist|install|clean|smudge|snapshot} */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (UsageException e) {
            System.exit(2);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.toString() : e.getMessage();
            if (!msg.endsWith("\n")) {
                msg = msg + "\n";
            }
            System.err.print(msg);
            System.exit(1);
        }
    }

    public static void run(String[] args) throws Exception {
        int format = 0;
        int i = 0;
        while (i < args.length) {
            String arg = args[i];
            if ("--version".equals(arg) || "-version".equals(arg)) {
                System.out.print("niagara-git " + NiagaraGit.VERSION + "\n");
                return;
            }
            if ("--help".equals(arg) || "-h".equals(arg) || "help".equals(arg)) {
                System.out.print(NiagaraGit.usage());
                return;
            }
            if ("--format".equals(arg)) {
                if (i + 1 >= args.length) {
                    System.err.print(NiagaraGit.usage());
                    throw new UsageException();
                }
                i++;
                format = parseFormat(args[i]);
            } else if (arg.startsWith("--format=")) {
                format = parseFormat(arg.substring("--format=".length()));
            } else {
                break;
            }
            i++;
        }
        if (format != 0 && format != NiagaraGit.FORMAT_VERSION) {
            System.err.print("niagara-git: textconv format " + format + " requested; this copy emits format "
                    + NiagaraGit.FORMAT_VERSION + ". Re-run the installer.\n");
        }
        if (i >= args.length) {
            System.err.print(NiagaraGit.usage());
            throw new UsageException();
        }
        String cmd = args[i];
        String[] rest = new String[args.length - i - 1];
        System.arraycopy(args, i + 1, rest, 0, rest.length);
        if ("bog".equals(cmd) || "dist".equals(cmd)) {
            if (rest.length != 1) {
                System.err.print(NiagaraGit.usage());
                throw new UsageException();
            }
            convert(cmd, rest[0]);
            return;
        }
        if ("clean".equals(cmd) || "smudge".equals(cmd)) {
            filter(cmd, rest);
            return;
        }
        if ("install".equals(cmd)) {
            ParsedInstall install = parseInstall(rest);
            Installer.install(install.repo, install.storeXml, Installer.currentCommandPrefix());
            return;
        }
        if ("snapshot".equals(cmd)) {
            Snapshot.Options opts = parseSnapshot(rest);
            Snapshot.run(opts, Installer.currentCommandPrefix());
            return;
        }
        System.err.print(NiagaraGit.usage());
        throw new UsageException();
    }

    private static int parseFormat(String text) throws UsageException {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            System.err.print(NiagaraGit.usage());
            throw new UsageException();
        }
    }

    private static void convert(String mode, String path) throws Exception {
        File file = new File(path);
        if (!file.isFile()) {
            throw new java.io.IOException("niagara-git: file not found: " + path + "\n");
        }
        byte[] data = Files.readAllBytes(file.toPath());
        String text = "bog".equals(mode) ? Convert.bog(data) : Convert.dist(data);
        System.out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        System.out.flush();
    }

    private static void filter(String mode, String[] args) throws Exception {
        if (args.length > 1) {
            System.err.print(NiagaraGit.usage());
            throw new UsageException();
        }
        if (args.length == 1 && args[0].toLowerCase(java.util.Locale.ROOT).endsWith(".dist")) {
            throw new java.io.IOException("niagara-git: " + mode
                    + " does not store .dist archives; they stay zipped and are diffed with textconv\n");
        }
        byte[] data = readFully(System.in, (long) NiagaraGit.MAX_ENTRY_BYTES + 1);
        byte[] out = "clean".equals(mode) ? BogStore.clean(data) : BogStore.smudge(data);
        System.out.write(out);
        System.out.flush();
    }

    private static ParsedInstall parseInstall(String[] args) throws UsageException {
        String repo = "";
        Boolean storeXml = null;
        for (int i = 0; i < args.length; i++) {
            if ("--store-xml".equals(args[i])) {
                if (storeXml != null && !storeXml.booleanValue()) {
                    System.err.print(NiagaraGit.usage());
                    throw new UsageException();
                }
                storeXml = Boolean.TRUE;
            } else if ("--no-store-xml".equals(args[i])) {
                if (storeXml != null && storeXml.booleanValue()) {
                    System.err.print(NiagaraGit.usage());
                    throw new UsageException();
                }
                storeXml = Boolean.FALSE;
            } else if (args[i].startsWith("-") || repo.length() > 0) {
                System.err.print(NiagaraGit.usage());
                throw new UsageException();
            } else {
                repo = args[i];
            }
        }
        ParsedInstall parsed = new ParsedInstall();
        parsed.repo = repo;
        parsed.storeXml = storeXml;
        return parsed;
    }

    private static Snapshot.Options parseSnapshot(String[] args) throws UsageException {
        Snapshot.Options opts = new Snapshot.Options();
        for (int i = 0; i < args.length; i++) {
            if ("--shared".equals(args[i])) {
                opts.shared = true;
            } else if ("--push".equals(args[i])) {
                opts.push = true;
            } else if ("--source".equals(args[i]) || "--repo".equals(args[i])) {
                if (i + 1 >= args.length) {
                    System.err.print(NiagaraGit.usage());
                    throw new UsageException();
                }
                i++;
                if ("--source".equals(args[i - 1])) {
                    opts.source = args[i];
                } else {
                    opts.repo = args[i];
                }
            } else {
                System.err.print(NiagaraGit.usage());
                throw new UsageException();
            }
        }
        if (opts.source == null || opts.repo == null) {
            System.err.print(NiagaraGit.usage());
            throw new UsageException();
        }
        return opts;
    }

    private static byte[] readFully(InputStream in, long max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) >= 0) {
            total += n;
            if (total > max) {
                throw new IOException("niagara-git: input larger than " + NiagaraGit.MAX_ENTRY_BYTES + " bytes");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static final class ParsedInstall {
        String repo;
        Boolean storeXml;
    }
}
