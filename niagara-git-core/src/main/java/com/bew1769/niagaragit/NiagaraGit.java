package com.bew1769.niagaragit;

/** Shared constants for the CLI and the station service. */
public final class NiagaraGit {
    public static final String VERSION = "0.1.0";
    /** Textconv format emitted by this copy. Git caches conversions by this number. */
    public static final int FORMAT_VERSION = 1;
    public static final int MAX_ENTRY_BYTES = 256 * 1024 * 1024;
    public static final String STORE_MAGIC = "NSTORE1\n";

    private NiagaraGit() {
    }

    public static String usage() {
        return "niagara-git turns Niagara 4 station files into text for git diff.\n"
                + "\n"
                + "Usage:\n"
                + "  niagara-git bog FILE\n"
                + "  niagara-git dist FILE\n"
                + "  niagara-git clean\n"
                + "  niagara-git smudge\n"
                + "  niagara-git install [--store-xml | --no-store-xml] [REPO]\n"
                + "  niagara-git snapshot --source PATH --repo PATH [--shared] [--push]\n"
                + "\n"
                + "bog and dist write a deterministic text view to stdout. install registers\n"
                + "those commands in a repository's local git config and merges the starter\n"
                + ".gitattributes and .gitignore templates.\n"
                + "\n"
                + "--store-xml is opt-in. It stores each .bog as text (every zip entry, in\n"
                + "order, with its original bytes) and rebuilds a zip on checkout. .dist files\n"
                + "stay zipped. snapshot copies config.bog, and with --shared the shared\n"
                + "folder, into a git repo and commits only when something changed.\n";
    }
}
