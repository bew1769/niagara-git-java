# niagara-git-java

Niagara Git for a JACE. This is a Java port of [niagara-git](https://github.com/bew1769/niagara-git) (the Go tool), packaged as a Niagara 4 module that runs on the station, plus a command-line jar that does the same diff and filter work on a PC.

Owner: Bryan Williams, controls tech.

The engine (zip textconv, the `--store-xml` container, secret skipping, snapshot file selection) is plain Java 8. The station service and the Workbench view are Niagara types. GitHub Actions compiles those types against small stand-in classes because the Niagara SDK is not in this repo. A real module jar is built in Workbench.

## What it does

A station database (`config.bog`) and a station backup (`.dist`) are zip files, so Git otherwise shows them as binary. The tool prints a stable text view:

| File | What `git diff` shows |
| --- | --- |
| `*.bog` | Pretty-printed Baja Object Graph XML. One `file.xml` member is printed by itself. |
| `*.dist` | Sorted file list (size, SHA-256, path), then the XML of each `.bog` that is not noise or a secret. |
| `*.px`, `*.nav`, `*.palette` | Left as text. |

History, alarms, logs, timestamped `config.backup.*.bog` copies, and certificate material are left out of a `.dist` listing. Noisy directories are a count and a total size. Secret files are a path and a size, with no checksum and no contents. Keystores, certs, histories, alarms, and logs are not committed by snapshot.

`--store-xml` is off unless you ask. It stores each `.bog` as a text container (every zip entry, in order, with its original bytes) and rebuilds a zip on checkout. `.dist` files stay zipped. A distribution archive contains histories, alarms, and keystores; putting one through a clean filter would commit those bytes.

On the JACE, `GitService` (under Services) versions **this station's directory**: `config.bog` and `shared/`, with the same ignore rules. The repo lives in the station home. Git operations use JGit (Eclipse Distribution License, Java 8). There is no `git` binary on the controller.

| Action | What it does |
| --- | --- |
| Init | `git init` in the station home, writes `.gitattributes` and `.gitignore`, sets `core.autocrlf=false`, records the remote. |
| Commit Snapshot | Saves the station, then commits `config.bog` and `shared/` when the bytes changed. |
| Pull | Fetches. Fast-forward only. If the histories diverged, nothing is written and the status says so. Otherwise the new tree is staged and `config.bog` is the zip Niagara will load. |
| Push | Saves, commits, pushes. |
| Status | Branch, head, ahead/behind/diverged, clean or dirty. The slot is `refreshStatus` because the `status` slot is the text property. |

Properties: remote URL, branch, author name and email, credential user, credentials (`baja:Password`), auto-commit interval (`baja:RelTime`, `0` is off), restart-after-pull, store-xml.

Auto-commit saves and commits. It does not push and it does not restart.

## Safety

A real `config.bog` contains station users. Niagara stores those passwords as encrypted hashes inside the station database. This tool does not strip them. A `.dist` backup often also contains `keystore.jceks`, `cacerts.jceks`, `exemptions.tes`, FIPS `*.bcfks` files, and exported keys.

**Keep a repository that holds a real station private.**

The service will not write a path that escapes the station directory, and it will not check out a symlink. Secret and noisy paths in a pulled commit are skipped and named in the status. An unsafe path (`..`, absolute, a drive letter) fails the pull before `HEAD` moves.

Credentials are a `baja:Password` slot. The service reads the value with `AccessController.doPrivileged` and never logs it. Do not put `user:token` in the remote URL; the service rejects that. Use the credential user (GitHub accepts the account name, or `git`) and put the token in the password slot. The slot needs Niagara's reversible password encoding so the station can present it to the remote. A one-way hash cannot be used as a token.

Actions check `BPermissions.ADMIN_INVOKE` (the same constant nhaystack uses) and require a user on the context. The hidden auto-commit tick allows a null context because the station clock invokes it.

### Pull, save, and restart

A running station will overwrite `config.bog` from memory when it saves. The pull order is:

1. Fetch and refuse anything that is not a fast-forward. No station file is changed yet.
2. Stage the new files under `.niagara-git/staged/` (smudged, so `config.bog` is a zip).
3. If restart is off, copy the staged files into the station and warn that a later save can overwrite them.
4. If restart is on, arm the loop guard, **save** (histories and the current database), copy the staged files back over that save, then invoke the platform restart.
5. A shutdown hook copies the staged files again, because Application Director's restart saves on the way down and that save would put the pre-pull database back on disk.
6. On the next start, if `config.bog` still does not match the staged pull, the service copies it once more and restarts. A third restart for the same commit is refused and logged.

The guard file is `.niagara-git/restart-guard`. That directory is not committed.

Restart calls `BSystemPlatformService.restartStation` (see [API uncertainties](#api-uncertainties)). It does not reboot the JACE.

## Java version

Niagara 4.13 on JACE-8000 / JACE-9000 class hardware runs **Java 8**. Later Niagara releases moved the station JVM; 4.13 did not. This project compiles with `--release 8`. JGit is pinned to 5.13.3, the last line that runs on Java 8. Do not upgrade it to JGit 6 for a 4.13 controller.

## Build on a PC (no Niagara SDK)

Java 21 (or 17) is enough. The compiler emits Java 8 bytecode.

```bash
./gradlew test shadowJar
java -jar niagara-git-cli/build/libs/niagara-git.jar --version
java -jar niagara-git-cli/build/libs/niagara-git.jar bog path/to/config.bog
```

Commands match the Go tool: `bog`, `dist`, `clean`, `smudge`, `install`, `snapshot`.

```bash
java -jar niagara-git.jar install --store-xml /path/to/repo
java -jar niagara-git.jar snapshot --source /opt/niagara/stations/demo --repo /srv/git/demo --shared
```

`install` registers local `diff.niagara-bog.textconv` and `diff.niagara-dist.textconv`. With `--store-xml` it also registers `filter.niagara-store`. Commit `.gitattributes` and `.gitignore`. Each clone has to run the installer once; the diff command is local git config.

The CLI jar relocates JGit under `com.bew1769.niagaragit.shaded` so it does not collide with another JGit on the classpath. The station module does not relocate: Tridium's `uberjar` configuration embeds JGit inside `niagara-git-rt.jar`, and a Niagara module classloader does not see another module's copy.

## Build the module in Workbench

The Niagara Gradle plugins and the `baja` jars ship with the Niagara install. They are not on Maven Central.

1. Set `niagara_home` (and `niagara_user_home`) in `gradle.properties`, or export `NIAGARA_HOME`.
2. Open a `settings.gradle.kts` that Workbench generated for this Niagara build and copy its `gradlePluginVersion` and `settingsPluginVersion` into `gradle.properties`. The defaults in this repo (`7.3.40` / `7.3.0`) match Tridium's public 4.13 code-samples and may not match your build number.
3. Replace the CI Gradle entry points with the Workbench ones:

   ```bash
   cp settings.niagara.gradle.kts settings.gradle.kts
   cp build.niagara.gradle.kts build.gradle.kts
   ```

   Or, in a Workbench module project you already have, add the folders `niagara-git-rt` and `niagara-git-wb`. Their `niagara-git-rt.gradle.kts` and `niagara-git-wb.gradle.kts` are the module-part scripts `findProjects()` looks for.
4. Build:

   ```bash
   ./gradlew :niagara-git-rt:jar :niagara-git-wb:jar :niagara-git-rt:slotomatic
   ```

   Slot-o-matic rewrites the `BEGIN BAJA AUTO GENERATED CODE` blocks and `module-include.xml` from the annotations. The copies in git were written by hand so the shape is reviewable; regenerate them before you ship.

`niagara-git-rt` compiles the shared engine sources into the module jar and uberjars JGit 5.13. `niagara-git-wb` depends on the runtime part and on `bajaui-wb` and `workbench-wb`.

The stubs under `niagara-git-stubs/` are not part of that build. Do not copy them to a JACE.

## Sign

Niagara 4.13 will not install an unsigned module.

The Gradle file leaves `com.tridium.niagara-signing` commented out until a Niagara signing profile exists (the dev alias is often `Niagara4Modules` in the Niagara user-home keystore). Uncomment that plugin in the module-part scripts when the profile is there.

Until then, sign the built jar locally. The script creates a self-signed RSA keystore under `signing/`, which is gitignored, and does not print the password into the repo.

Linux:

```bash
chmod +x scripts/sign-module.sh
scripts/sign-module.sh niagara-git-rt/build/libs/niagara-git-rt.jar
scripts/sign-module.sh niagara-git-wb/build/libs/niagara-git-wb.jar
```

Windows:

```powershell
.\scripts\sign-module.ps1 -Jar niagara-git-rt\build\libs\niagara-git-rt.jar
.\scripts\sign-module.ps1 -Jar niagara-git-wb\build\libs\niagara-git-wb.jar
```

The public certificate is `signing/niagara-git.cer`. The private key stays in `signing/niagara-git-dev.jks`. Never commit that keystore or `signing/password.txt`.

### Trust the cert on a JACE

1. Open Workbench and connect to the **platform** of the JACE (not only the station).
2. Open **Platform > Certificate Management**.
3. Open the **User Trust Store**.
4. Import `signing/niagara-git.cer`.
5. Module verification on 4.13 rejects a signer that is not in that trust store. If the platform is in a strict verification mode, the import is what allows Software Manager to accept the jar. Allowed Hosts is the FOX allow-list; it does not replace the trust store for code signing.
6. Connect to the platform, open **Software Manager**, and install `niagara-git-rt.jar` and `niagara-git-wb.jar`. Install the runtime part on the JACE. The Workbench part is for the PC you use to open the view; a JACE does not need the wb jar to run the service.
7. Restart the station when Software Manager asks.
8. Open the station, open the `niagaraGit` palette, and drop **GitService** onto **Services**.

Set the remote, branch, author, and token. Run **Init**, then **Commit Snapshot**, then **Push**. **Pull** with restart enabled only after a fast-forward is what you want on that controller.

## Usage on the station

1. Init. This creates `.git` in the station home and writes the ignore rules. `.git` and `.niagara-git/` stay inside the station directory. The service does not write anywhere else.
2. Commit Snapshot after the station is in the state you want recorded. The action saves first, so `config.bog` matches memory.
3. Push.
4. On another machine, `java -jar niagara-git.jar install` in the clone so `git diff` can read `.bog` files. Add `--store-xml` on that install only if the station's store-xml property is on. Both sides have to agree or the blob and the worktree will not match.
5. Pull on the JACE. If the remote moved forward, the service stages `config.bog` and, when the toggle is on, restarts. If you committed on the JACE and also on the remote, the pull stops and the status tells you the two commit ids.

The property sheet shows the actions. The Workbench view (`GitServiceView`) has the same buttons and calls `invoke`, so the admin check still runs on the station.

Logs go to `java.util.logging` logger `niagaraGit`. Set that logger in the station's log service when you need more detail. Failures are also copied into the read-only status slot.

## Limits

* Passphrase-encrypted bog and dist files are not decrypted. The text view is the size and the SHA-256.
* Baja reversible encoding is left as stored. Values are not decoded.
* Pretty-printing sorts attributes and drops insignificant whitespace. Element order is kept. A DTD or entity declaration is not parsed.
* A zip member larger than 256 MiB is not inflated.
* Inside a `.dist`, Px, nav, and palette files are checksummed, not inlined. Only `.bog` members are expanded.
* `.dist` is never a clean/smudge filter.
* Textconv does not run on GitHub. With the default install, a pull request still shows `.bog` as binary. With `--store-xml`, the pull request shows the text container.
* The JACE service does not shell out to `git`. Filters and textconv matter for the CLI on a PC. On the station, JGit writes the zip or the container directly.
* Pull is fast-forward only. There is no merge and no conflict resolution on the controller.
* Restart depends on the platform service accepting `restartStation`. If that action is named differently in your build, the service logs the failure and does not reboot the host.
* The shutdown hook assumes the station JVM exits on a platform restart. If a future build restarts the station in-process, the hook will not run; the startup check is the backstop, and it will not loop.
* Zip comments use the classic end-of-central-directory record. A ZIP64 comment is not copied.
* Smudge's zip bytes are deterministic on one machine. They are not claimed to be byte-identical to the Go tool's zip writer. Entry names, order, and uncompressed bytes are.

## API uncertainties

These are the calls that are documented by public Niagara 4 code or Tridium samples, and the ones to re-check in your baja jar before trusting a restart on a live JACE.

| Call | Why it is here | Confidence |
| --- | --- | --- |
| `BAbstractService`, `serviceStarted`, `serviceStopped`, `getServiceTypes` | Service under Services. Matches Tridium's module examples. | High |
| `Sys.getStationHome()` returns `File` | Used by station file utilities and the security guide (`Sys.getStationHome()`). | High |
| `Sys.getStation().save()` | axCommunity's Niagara 4 `BSysInfo.doSaveStation`. niagaramcp also documents `BStation.doSave(Context)`. This service calls `save()`. | High that one of these exists. Confirm `save()` is not asynchronous in 4.13; the pull path re-copies `config.bog` after it returns either way. |
| `Sys.getService(Type)` | Tridium's service lookup, used by axCommunity for the platform service. | High |
| `Sys.loadType(Class)`, `newProperty`, `newAction`, `getString` / `setString` | Standard slot boilerplate. | High |
| `BPassword.DEFAULT`, `BPassword.getValue()` inside `AccessController.doPrivileged` | Niagara 4 security guide (reversible password encoding). | High |
| `BRelTime.DEFAULT`, `BRelTime.make(long)`, `getMillis()` | Used throughout Niagara modules, including nhaystack. | High |
| `Clock.schedulePeriodically(BComponent, BRelTime, Action, BValue)` | Standard periodic action. The ticket type is `Clock.Ticket`. | Medium. Confirm the overload if slotomatic or the compiler disagrees. |
| `BPermissions.ADMIN_INVOKE` and `getPermissions(Context).has(int)` | nhaystack `TypeUtil` calls these exact names. | High |
| `BSystemPlatformService.TYPE`, `.restartStation`, `.lease(1)`, `.invoke(...)` | axCommunity `BSysInfo.doRestartStation`, which says it is the Application Director restart. The class is `com.tridium.platform.BSystemPlatformService` in the platform module. | Medium. The Gradle dependency is `api(":platform-rt")`. If the part name differs, change that line. If the action field is not `restartStation`, change `BGitService` and do not guess a reboot action. |
| `Lexicon.make("niagaraGit")`, `Command(BWidget, Lexicon, String)`, `BEdgePane`, `BGridPane`, `BAbstractButton.make`, `BWbComponentView.setContent`, `doLoadValue` | Tridium's public N4 `BGatorTrackerView` sample. | High for the view base and the command constructor. `BLabel.setText` is the usual widget mutator; confirm if the wb jar uses a different setter. |
| `@NiagaraType`, `@NiagaraProperty`, `@NiagaraAction`, `@AgentOn` in `javax.baja.nre.annotations` | Tridium N4 sample and every modern module. | High |
| `module-include.xml` `<agent><on type="niagaraGit:GitService"/></agent>` | Hand-written. Slot-o-matic's output is the authority. | Low until you run slotomatic. |
| Palette `t="ngit:GitService"` with `m="ngit=niagaraGit"` | Same shape as other palettes, using preferred symbol `ngit`. | Medium |
| `module-permissions.xml` `NETWORK_COMMUNICATION` | Same element shape as public modules. Ports include 443, 22, and 9418. | Medium. Niagara may want a narrower host list before a job site will approve it. |

The stubs in `niagara-git-stubs/` compile these calls. They are not evidence that the real method exists. The comments in that tree say STAND-IN.

## Tests

GitHub Actions runs `./gradlew test shadowJar` on Ubuntu and Windows and checks that `java -jar niagara-git.jar --version` works and that the jar does not contain `javax.baja`.

Covered without Niagara: classification, XML pretty-print, bog/dist goldens from the Go tool's fixtures, clean/smudge round trip, the marked gitignore block, JGit commit/push/fast-forward/divergence, secret paths left out of the tree, store-xml blobs versus a zip worktree, the restart guard, and the pull copy that runs again after a simulated station save.

Not covered here, because it needs Workbench and a JACE: slotomatic, module signing with Tridium's plugin, Software Manager install, a real `restartStation`, and a FOX session. Do the first pull against a bench JACE with a copy of the station, not against a live plant.
