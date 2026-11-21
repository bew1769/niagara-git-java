package com.bew1769.niagaragit;

import java.io.File;
import java.io.IOException;

/**
 * Station actions shared by the Niagara service. The host saves and restarts;
 * this class never touches a Niagara type.
 */
public final class StationWorkflow {
    public interface Host {
        File stationHome();

        /** Flush the running station. A save can overwrite config.bog with memory. */
        void save();

        /** Ask the platform to restart this station. The process may keep running until shutdown. */
        void restart();
    }

    public static final class Options {
        public String remoteUrl = "";
        public String branch = "main";
        public String authorName = "niagara-git";
        public String authorEmail = "niagara-git@localhost";
        public String credentialUser = "git";
        public String credentialSecret = "";
        public boolean storeXml;
        public boolean restartAfterPull;
    }

    private static final Object LOCK = new Object();

    private StationWorkflow() {
    }

    public static String init(Host host, Options options) throws IOException {
        synchronized (LOCK) {
            return JGitStation.init(request(host, options));
        }
    }

    public static String commitSnapshot(Host host, Options options) throws IOException {
        synchronized (LOCK) {
            host.save();
            return JGitStation.commit(request(host, options), false).message;
        }
    }

    public static String push(Host host, Options options) throws IOException {
        synchronized (LOCK) {
            host.save();
            return JGitStation.commit(request(host, options), true).message;
        }
    }

    public static String status(Host host, Options options) throws IOException {
        synchronized (LOCK) {
            return JGitStation.status(request(host, options));
        }
    }

    public static String pull(Host host, Options options) throws IOException {
        synchronized (LOCK) {
            JGitStation.Outcome outcome = JGitStation.pull(request(host, options));
            File home = host.stationHome();
            if (!outcome.changed) {
                return outcome.message;
            }
            if (!options.restartAfterPull) {
                PullApplier.apply(home);
                return outcome.message + "\nRestart is off. A later station save can overwrite config.bog.";
            }
            if (!RestartGuard.arm(home, outcome.sha)) {
                return "niagara-git: fast-forward " + outcome.sha
                        + " is staged but restart was suppressed; the loop guard is at its limit.";
            }
            host.save();
            PullApplier.apply(home);
            host.restart();
            return outcome.message + "\nStation restart requested.";
        }
    }

    /**
     * Called when the station is up. If a shutdown save put the old database back,
     * copy the staged pull once more and restart. The guard stops a third try.
     */
    public static String onStarted(Host host) throws IOException {
        synchronized (LOCK) {
            File home = host.stationHome();
            if (!PullApplier.needsApply(home)) {
                return "";
            }
            PullApplier.apply(home);
            String sha = PullApplier.pendingSha(home);
            if (!RestartGuard.arm(home, sha)) {
                return "niagara-git: pulled config.bog did not stick and the restart guard stopped a loop for " + sha;
            }
            host.restart();
            return "niagara-git: re-applied pulled config.bog and requested another restart";
        }
    }

    private static JGitStation.Request request(Host host, Options options) {
        JGitStation.Request req = new JGitStation.Request();
        req.station = host.stationHome();
        req.remoteUrl = options.remoteUrl == null ? "" : options.remoteUrl;
        req.branch = options.branch == null || options.branch.isEmpty() ? "main" : options.branch;
        req.authorName = options.authorName == null || options.authorName.isEmpty() ? "niagara-git" : options.authorName;
        req.authorEmail = options.authorEmail == null || options.authorEmail.isEmpty()
                ? "niagara-git@localhost" : options.authorEmail;
        req.credentialUser = options.credentialUser == null || options.credentialUser.isEmpty()
                ? "git" : options.credentialUser;
        req.credentialSecret = options.credentialSecret == null ? "" : options.credentialSecret;
        req.storeXml = options.storeXml;
        return req;
    }
}
