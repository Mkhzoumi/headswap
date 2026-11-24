# CLAUDE.md — Mkhzoumi's fork (English)

This repo is **Mkhzoumi/headswap**, a fork of the developer's **YegorkaPomidorka/headswap**. The root `CLAUDE.md` is the
developer's own (in Russian), written for their repo and their user-support process. **Where it conflicts with this
file, this file wins.** In particular, its sections «Ticket workflow», «Как изменения попадают в main» (how changes get
into main) and «Release» do not apply here.

## Read first

- [docs/en/ARCHITECTURE.md](../docs/en/ARCHITECTURE.md) — English map of the app: packages, data flow, HU flags,
  invariants, "symptom → where to look". Read it before exploring code; it often replaces exploring.
  Russian original: `docs/ARCHITECTURE.md`.
- [docs/en/README.md](../docs/en/README.md) — how this fork builds, releases and updates phones.
- `docs/adr/` — why things are done the way they are (Russian); English titles in
  [docs/en/adr-index.md](../docs/en/adr-index.md). Before changing an approach (passthrough/re-encoding, heartbeat,
  chunking, the 5277 server), read the ADR on the topic.
- Test bench without a car (HU window on the PC + smoke build on the phone): `pc-headunit\stand.ps1`, skill
  `.claude/skills/stand`, English notes in [docs/en/pc-headunit.md](../docs/en/pc-headunit.md). "Open the stand" means a
  live window on the desktop, not an emulator without a window.

## Fork workflow (overrides the root CLAUDE.md)

- **Never push to, open pull requests on, comment on, or create releases in `YegorkaPomidorka/headswap`.** It is
  read-only for us (remote `upstream`). Our repo is `origin` = `Mkhzoumi/headswap`. A change meant for the developer is
  only prepared here; the user decides whether and how to send it.
- Work happens on **`my-changes`**, the fork's default branch, or on a short-lived branch merged into it. The fork's
  `main` only mirrors the developer and is never built.
- `git add` explicit paths only, never `-a`. This repo commits with the user's GitHub noreply address (repo-local
  `git config user.email`); don't change it.
- Push with the gh account, not Git Credential Manager:
  `git -c credential.helper= -c "credential.helper=!gh auth git-credential" push origin my-changes`
- **Releases are made only by `.github/workflows/fork-release.yml`**, never with a local `gh release create`:
  - The developer's releases: checked daily; the run waits for the user's approval (environment `upstream-release`),
    then merges the tag into `my-changes`, runs the unit tests, builds, signs and publishes `v<version>-fork`.
  - The user's own release: `gh workflow run "Fork release" -R Mkhzoumi/headswap -f version=<current>.N -f notes="..."`
    (for example `4.3.0.1`). Add `-f dry_run=true` to only test and build.
  - **A published release reaches the user's phone through the in-app updater.** Ask before starting any run that
    publishes; dry runs are fine.
  - Don't edit `versionName`/`versionCode` in `android/app/build.gradle.kts`: the workflow sets them at build time
    (versionCode = developer's versionCode × 100 + N).
- **Keep the fork's diff small.** Every developer release is merged into `my-changes`; edits to the developer's files
  (above all `AaBridge.kt`, `CarLifeSession.kt`, `BridgeService.kt`) cause merge conflicts, and a conflict stops the
  automatic release until it is resolved on the PC. Prefer new files and small hooks. Big features are better offered to
  the developer.
- Fork-only changes so far: `UpdateLogic.REPO` (+ `UpdateLogicTest`), the release workflow, the English docs
  (`docs/en/`, this file).
- Never commit certificates, keys, passwords or users' logs. AA certificates (`android/app/src/main/assets/aa/`) are
  git-ignored; the signing key lives in `~/.android-keys/` and in the repo's Actions secrets.
- A decision specific to the fork (a non-obvious choice, an approach rejected after testing) gets a short record in
  `docs/en/fork-adr/F<NNNN>-<slug>.md`, not in `docs/adr/` (those numbers belong to the developer and would collide).

## Building and testing on Windows

- Set the JDK and SDK for the shell (Android Studio's bundled JDK works):
  `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"`
- `cd android; .\gradlew testDebugUnitTest` — about 1,280 tests. Two concurrency tests in `AaTranscodeCoreTest` can time
  out when the machine is busy; rerun them alone before calling it a bug.
- AA head unit certificates must be in `android/app/src/main/assets/aa/{jvc,hur}/{cert,key}.pem` (see
  [docs/en/README.md](../docs/en/README.md)).
- A local `assembleRelease` keeps the developer's versionCode (for example 77), which is lower than the fork's
  releases (7700+), so it can't be installed over them. To try a change on the phone, use the smoke build
  (`stand.ps1 -Build`, installs next to the real app) or a dry-run/own release.

## Technical rules (translated from the developer's CLAUDE.md files)

- Structure changed (new package, HU flag, data path): update `docs/en/ARCHITECTURE.md` in the same change and mark
  the addition **[fork]**.
- Car-brand fixes (for example the Honda cluster) apply to all models and channels of that brand unless told otherwise.
- For a bug report: collect all logs, photos and videos first, run `tools/logdiag.py`, report a diagnosis, and only
  build or change code when the user says so.
- For UI features: discuss the design first and show a mockup before writing code.
- Take the user's hypotheses seriously and test them before arguing against them.
- "Install on the phone" means `adb install`, not sending the APK through a messenger.

**`aa/` — Android Auto side and the bridge core**
- Read `AaBridge.kt` by function, not whole: `onFrame`/`aaFrame` (video), `onAudio*`, `onCarlifeTouch`,
  `onHuInfo`/`onHuStatistic` (HU detection), `attach()` (state reset).
- A new HU flag `hu*`: constant in the companion, `private set`, **reset in `attach()`**, set in
  `onHuInfo`/`onHuStatistic`, test in `AaBridgeTest`. Enable it only on its own HU (ADR 0006).
- `huVw20082120` really means "heartbeat on PAUSE", not only VW 20082120.
- The 5277 server is one-shot: close only through `AapSession.shutdown` (ByeBye → EOF); never kill a live AA session
  (ADR 0003).
- `VideoMode` and `CarPreset` are stored in prefs by enum name — never rename the values.
- Passthrough vs re-encoding is decided in four places (listed in ARCHITECTURE, section "Video"). Change one, check the
  others.
- Package tests: `android/app/src/test/java/.../aa/`, references in `src/test/resources/aa/` (`golden.txt` is generated
  by `pc-headunit`).

**`link/` — transport to the HU** (wireless: ADR 0014)
- `StreamTransport` — USB AOA and debug TCP: one stream with an outer header, bodies written in pieces ≤ 16 KiB
  (`Wire.CHUNK`). Mobis CCNC writes the body in one write (ADR 0009). EIO in the log is the IOException text from here.
- `MultiSocketTransport` — wireless: the phone listens on 6 ports, the HU is the client, one socket per channel.
- Three ways onto a shared network with the HU: `bt/` (RFCOMM `carlife` + joining the HU's P2P group),
  `wifi/HotspotBeacon` (phone hotspot, UDP 7999), `car/CarNetwork` ("car Wi-Fi"). The bridge never turns the hotspot on
  by itself.
- The cable beats wireless (`BridgeService`). Mode selection logic is pure, in `car/CarLogic`, covered by tests.
- Never write passwords, SSIDs or keys to `WLog`.

**`tools/` — log analysis**
- A user's log: first `python -X utf8 tools/logdiag.py <zip|log>` (cause + advice), then `logdigest.py` (summary).
- `logdiag_rules.json` is shared with the app: Gradle `copyLogdiagRules` puts it into assets and `diag/LogDiag.kt` reads
  the same rules. A new cause = a rule in the JSON + a case in `logdiag_cases/` from a real log. Python and Kotlin must
  give the same result (`tools/test_logdiag.py`, `LogDiagTest.kt`).
- Markers are regexes over raw log lines. The `AppLog`/`WLog` line format is a contract (ADR 0016) — the Russian log
  strings must stay as they are.
- Never put users' logs in the repo: it is public.
