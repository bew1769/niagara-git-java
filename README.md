# niagara-git-java

niagara-git as a Niagara 4 module: a station component that commits its own `config.bog` (and its `shared/` graphics) to git, and pushes to or pulls from a remote. It is meant for a JACE or a Supervisor that has no git executable. JGit does the git work inside the station JVM.

It writes the same `.gitignore` and `.gitattributes` blocks as the Go [niagara-git](https://github.com/bew1769/niagara-git) tool, so a repository the station pushes can be cloned on a Workbench PC and diffed there with `niagara-git install`.

Niagara SDK jars are not in this repository. `baja.jar`, `nre.jar`, and the Tridium Gradle plugins come from a Niagara install you are licensed to use.

## Layout

| Path | What it is |
| --- | --- |
| `niagaragit-core` | Snapshot rules, JGit wrapper, starter templates. No Niagara types. JUnit tests. |
| `niagaraGit-rt` | `BStationGit`, the station component. Compiles the core and JGit into the module jar. |
| `niagaraGit-rt/module-permissions.xml` | Permissions the module asks for (network, worker thread). |

The module targets Niagara 4.13 and later, Java 8 bytecode. JGit is 5.13.3, the last JGit line that runs on Java 8.

## What the component does

Drop **StationGit** from the `niagaraGit` palette anywhere in the station (Services is a natural place).

| Slot | Meaning |
| --- | --- |
| `repoPath` | Work tree. Blank (the default) means `<niagara user home>/shared/git/<station>`. A relative path is resolved against `<niagara user home>/shared`. See "Station security" below. |
| `remoteUrl` | `https://…` or a `file:` URL. Blank means commit only. A `file:` remote must be under `<niagara user home>/shared`. A URL with a user name or token in it (`https://user:token@host/…`) is refused: the URL is saved in config.bog and would be committed. |
| `branch` | Default `main`. |
| `username`, `password` | HTTPS credentials. `password` is a Niagara password slot (encrypted with the station key in the saved bog, and never written to a snapshot). For GitHub, a fine-grained token with Contents read/write on that one repo as the password; the user name can be anything non-empty. |
| `authorName`, `authorEmail` | Commit identity. |
| `includeShared` | Also version `shared/` (Px, nav, palettes, images). Default true. |
| `applySharedOnPull` | After a pull, copy pulled `shared/` files into the station. Default false. |
| `commitMessage` | Optional text added to every commit message. |
| `status`, `lastResult`, `lastCommit`, `lastRun` | Read-only results. |

Actions (none take an argument, so a `control:TimeTrigger` or any other topic can drive them through a link):

- **commit**: copy `config.bog` and the shared files into the work tree, then commit when the bytes changed. A second commit with nothing new reports `no changes`.
- **push**: push the branch to `origin`. Never forces. A rejected push (the remote moved on) is reported, not overwritten.
- **commitAndPush**: both. Suitable for a trigger or a scheduled link.
- **pull**: fetch and fast-forward only. A diverged history is refused with a message; resolve it in a Workbench clone. Pull never writes the live `config.bog`. A running station owns its database, and restoring one is a Platform / Station Copier job.

Everything runs on one daemon thread, never on the engine thread. A second request while one is running is refused with a status message.

What leaves the station: `config.bog` and the files under `shared/` that are not secret or noise. Not committed: histories, alarms, logs, `config.backup.*.bog`, `dataRecovery`, key stores, certificates, `*.pem` / `*.p12` / `*.pfx` / `*.jceks` / `*.bcfks`, and anything under `security/` or `certManagement/`. The rules are the same as the Go tool's `classify.go`.

**No passwords leave the station.** The snapshot's `config.bog` has every password slot emptied: user passwords, their password history, driver and service passwords, and this component's own token (see "Passwords in snapshots" below). After restoring a station from a snapshot, set user passwords and tokens again. The rest of the station (names, addresses, programs, links) is still in the snapshot, so **push only to a private repository.**

JGit is isolated from the host: it does not read `~/.gitconfig`, the system gitconfig, or `GIT_*` environment variables, and it does not write `~/.config/jgit`. Only the work tree's own `.git/config` applies. That config sets `core.autocrlf=false` so `.bog` zips are stored byte for byte.

## Station security

A station runs third-party modules under the Java security manager. Found on a scratch station (Niagara 4.15.6.20):

- The module may read and write only the station's `shared/` folder and `<niagara user home>/shared`. Writing `stations/<name>/git` was denied, so the work tree lives in `<niagara user home>/shared/git/<station>`, outside the station folder and not served as `file:^`. A `file:` remote elsewhere on disk is denied too.
- Reading the saved `stations/<name>/config.bog` is denied (and it holds key-wrapped passwords). `commit` encodes the running station with Niagara's own bog encoder (`javax.baja.io.ValueDocEncoder`), without an encryption key, and stores it zipped as `file.xml`, the layout of a saved bog. The encoded copy is the live component tree, so it is not byte-identical to the last save, and every snapshot differs from the one before while values change (one commit per trigger on a busy station).
- JGit needs `SHUTDOWN_HOOKS` (it registers a hook when it first loads) and `MANAGE_EXECUTION`. `module-permissions.xml` requests both, plus `NETWORK_COMMUNICATION`.
- Reading the password slot needs the module's own key-ring permission, which an action fired from another module's link does not carry, so the settings are read inside `doPrivileged`.
- The worker thread is created inside `doPrivileged`. Otherwise it inherits the permissions of whatever core module invoked the action (for example a `control:TimeTrigger` link), and JGit's own thread creation fails with `setContextClassLoader` denied.

## Passwords in snapshots

Checked on a scratch station with a throwaway user, a throwaway password slot and a token on StationGit:

| Slot | Saved `config.bog` (stays on the station) | Bog encoded without a key (before the fix) | Committed snapshot |
| --- | --- | --- | --- |
| Reversible password (driver password, StationGit token) | `[aliased-aes-256.2]…`, encrypted with the station key | empty | empty |
| User password | `[pbkdf2-aes-256.1]…`, PBKDF2 hash wrapped with the station key | `[pbkdf2-sha256.1]=salt:iterations:hash`, a bare PBKDF2 hash that can be attacked offline | empty |
| User password history | wrapped | base64 of bare PBKDF2 hashes | slot dropped (default) |

Without a key and with `defaultOnEncodeFailures`, Niagara's encoder already writes reversible passwords empty. The bare user hashes are removed by `BogSecrets` before anything reaches the work tree. It runs on every snapshot, whatever the source, and `lastResult` reports `N password values removed`. `BogSecretsTest` covers it.

## HTTPS remotes

Checked against a local HTTPS git server (git-http-backend behind basic auth, self-signed certificate):

- **Right credentials, trusted certificate:** push and pull work.
- **Wrong token:** fails cleanly. `lastResult` is `…: not authorized`, nothing is written, and the next trigger tries again (JGit makes 4 requests per attempt).
- **Self-signed or private-CA certificate, not trusted:** fails with `cannot open git-receive-pack` (pull: `git-upload-pack`); the station log shows `TlsFatalAlert: certificate_unknown` / `failed certificate validation`. The station uses Niagara's TLS stack and trust stores, not the JRE `cacerts`, and there is no "skip verification" setting.
- **To trust a server:** in Workbench open the station's **Platform → Certificate Management → User Trust Store** (or the station's Certificate Manager) and import the server's certificate or its CA, then restart the station. Public CAs such as GitHub's are already in the System Trust Store.
- Permissions: `NETWORK_COMMUNICATION` with hosts `*`, ports `*`, type `client` and `proxySelector=get` (JGit reads the default proxy selector; without it a fetch fails with `NetPermission getProxySelector` denied). To lock a station down, narrow `hosts` and `ports` to your git server (for example `github.com` and `443`) and rebuild.

## 1. Run the core tests

Any JDK 8 or later. The Niagara 4.15 `jre` (Zulu 8, includes `javac`) works:

```powershell
$env:JAVA_HOME = "C:\Niagara\Niagara-4.15.6.20\jre"
.\gradlew.bat test
```

```bash
./gradlew test
```

The tests build fake stations, commit, push to a local bare repository, pull a Workbench-side change, and check that a diverged history and a non-fast-forward push are refused.

## 2. Point the build at Niagara

Copy `local.properties.example` to `local.properties` and set `niagara_home`. The Niagara module plugin also resolves `baja.jar` from `niagara/niagara_home` under the repo. Link it there (both `niagara/` and `local.properties` are gitignored):

```powershell
mkdir niagara
cmd /c mklink /J niagara\niagara_home C:\Niagara\Niagara-4.15.6.20
```

```bash
mkdir -p niagara && ln -sfn /opt/Niagara-4.15.6.20 niagara/niagara_home
```

Compile the station part without signing (this does not build or install a jar):

```powershell
.\gradlew.bat -c settings-niagara.gradle.kts '-Psigning.skip=true' :niagaraGit-rt:compileJava
```

In PowerShell, quote `-P` arguments that contain a dot. Unquoted, PowerShell splits `-Psigning.skip=true` at the dot and Gradle never sees the property.

## 3. Signing

A station loads the jar only when it is signed by a certificate that host trusts. The build refuses to fall back to an unsigned jar. Create a profile and a self-signed development certificate with the Tridium signing plugin (same steps as [control-basic-niagara](https://github.com/bew1769/control-basic-niagara/blob/main/docs/SIGNING.md)):

```powershell
.\gradlew.bat -c settings-niagara.gradle.kts createProfile --profile-path=security/niagara.signing.xml --store-type=PKCS12 '-Psigning.skip=true'
.\gradlew.bat -c settings-niagara.gradle.kts generateCertificate --profile-path=security/niagara.signing.xml --alias=niagaragit --pem-file=security/dev-cert.pem '-Psigning.skip=true'
```

`security/` output is gitignored. Import the PEM into the User Trust Store of every Workbench and station host that will load the module.

## 4. Build and install

```powershell
$env:NIAGARA_SIGNING_PROFILE = "$PWD\security\niagara.signing.xml"
$env:NIAGARA_SIGNING_ALIAS = "niagaragit"
.\gradlew.bat -c settings-niagara.gradle.kts jar
```

The jar task signs `niagaraGit-rt.jar` and **copies it into `niagara_home/modules`** of the install `niagara/niagara_home` points at. On a JACE, install through Platform → Software Manager.

## Status

Verified on Windows 11 with Niagara 4.15.6.20 (Vykon brand), Zulu 8.0.502, Gradle 8.10.2 wrapper, plugin 7.6.22:

- `gradlew test`: 28 tests, 0 failures.
- `gradlew -c settings-niagara.gradle.kts :niagaraGit-rt:jar` built and signed the module with a self-signed development certificate trusted in a scratch user home.
- A scratch station loaded the module (with the "self-signed certificate" warning) and the three permission groups. `control:TimeTrigger` links drove `commitAndPush` every 30 s and `pull` every 45 s. Commits reached a bare `file:` remote under `<niagara user home>/shared/remotes`. A commit pushed from another clone (a new `shared/px` file plus a junk `config.bog`) was pulled: the Px file was written to the station's `shared/px`, the station's `config.bog` was untouched. `lastResult`, `lastCommit` and `lastRun` updated.

- HTTPS: against a local git-http-backend with basic auth and a self-signed certificate, push and pull worked once the certificate was in the station's User Trust Store; an untrusted certificate and a wrong token both failed cleanly (see "HTTPS remotes"). 27 pushed snapshots were scanned: no password, hash, history or token in any of them (see "Passwords in snapshots").

Not yet verified: a hosted service (GitHub, GitLab) from a station, a proxy, and restoring a station from a snapshot.

SSH remotes are not supported (JGit's SSH transport is not bundled). Use HTTPS with a token, or a `file:` URL.
