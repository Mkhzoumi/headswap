# Headswap — fork guide (English)

**Headswap** runs Android Auto on car head units that only support Baidu CarLife 2.0 (common in Chinese-market cars).
The phone talks to Android Auto's "head unit server" on 127.0.0.1:5277 as if it were a head unit, and presents itself
to the car as a CarLife phone over USB or Wi-Fi. No dongle needed. The original project is
[YegorkaPomidorka/headswap](https://github.com/YegorkaPomidorka/headswap) (GPL-3.0); this is **Mkhzoumi's fork** of it.

## English documentation

| Document | What's in it |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | The map of the app: packages, data flow, head unit workarounds, invariants, symptom → where to look |
| [adr-index.md](adr-index.md) | English titles of the developer's design decisions (`docs/adr/`, in Russian) |
| [fork-adr/](fork-adr/) | Decisions specific to this fork |
| [pc-headunit.md](pc-headunit.md) | PC test tools: head unit emulators, the test bench (`stand.ps1`), UI autotests |
| [tools.md](tools.md) | Log analysis tools (`logdiag.py`, `logdigest.py`) |
| [`.claude/CLAUDE.md`](../../.claude/CLAUDE.md) | Instructions for Claude in this fork (override the developer's `CLAUDE.md`) |

The translations reflect the developer's release **v4.3.0**. The Russian originals stay authoritative; after merging a
newer developer release, check what changed in them (`git diff <old tag> <new tag> -- docs/ CLAUDE.md`) and update the
English files.

## How this fork differs

- **Own signing key and updates from this repo.** `UpdateLogic.REPO` = `Mkhzoumi/headswap`, so phones with a fork build
  update only from this repo's releases. Fork builds and the official app can't update each other (different keys);
  switching between them requires uninstalling.
- **Releases are built by GitHub Actions** (`.github/workflows/fork-release.yml`) from the `my-changes` branch, the
  repo's default branch. `main` only mirrors the developer.
- **Versions:** the developer's release `4.3.1` is published here as `v4.3.1-fork`; your own releases in between are
  `4.3.1.1`, `4.3.1.2`, … (versionCode = developer's × 100 + N, so the updater accepts each one).

Why it's set up this way: [fork-adr/F0001](fork-adr/F0001-fork-releases-and-updates.md).

## Releases

**The developer's releases (automatic, with your approval).** Every day the workflow checks the developer's latest
stable release. If it's new, the run stops and GitHub emails you. Open the waiting run (Actions → Fork release); its
summary links the release notes and the exact code changes. **Review deployments → Approve** merges it into
`my-changes`, runs the unit tests, builds, signs and publishes it; the phone offers it the next time Headswap checks.
**Reject** skips it. The developer's pre-releases (test builds) are never followed.

**Your own release** (after pushing changes to `my-changes`):

```
gh workflow run "Fork release" -R Mkhzoumi/headswap -f version=4.3.0.2 -f notes="What changed"
```

The version must be the current version plus `.N` (1–99), higher than your last one. The notes are shown on the phone
before it updates.

**Dry run** (test and build, publish nothing): add `-f dry_run=true`, or tick "Dry run" in Actions → Fork release →
Run workflow.

## Making changes

- Work on `my-changes` (local clone: `D:\android\android-auto\headswap`), commit, then push:
  `git -c credential.helper= -c "credential.helper=!gh auth git-credential" push origin my-changes`.
  Pushing alone releases nothing.
- **Keep changes small and prefer new files.** Each developer release is merged into `my-changes`; if you both changed
  the same lines, the release stops with a conflict until you resolve it:

  ```
  git switch my-changes
  git pull origin my-changes
  git fetch upstream --tags
  git merge v4.3.1          # the developer's tag the run reported
  # fix the conflicted files, then:
  git add <files>
  git commit
  git push origin my-changes
  gh workflow run "Fork release" -R Mkhzoumi/headswap
  ```

  (`upstream` is the developer's repo, `origin` is this fork. Don't push tags.)

## Building locally (Windows)

1. JDK and SDK for the shell (Android Studio's bundled JDK works):
   `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"`
2. Android Auto head unit certificates (not in git), named `cert.pem` / `key.pem`:
   - `android/app/src/main/assets/aa/jvc/` — from [opencardev/aasdk](https://github.com/opencardev/aasdk)
     `cert/headunit.crt` and `cert/headunit.key`;
   - `android/app/src/main/assets/aa/hur/` — from
     [andreknieriem/open-headunit](https://github.com/andreknieriem/open-headunit) `app/src/main/res/raw/cert` and
     `privkey`.

   The workflow downloads the same files from pinned commits and checks their SHA-256.
3. Tests: `cd android; .\gradlew testDebugUnitTest`. Debug APK: `.\gradlew assembleDebug`. Signed release:
   `.\gradlew assembleRelease` (uses `~/.android-keys/headswap-release.properties`).

A local release build keeps the developer's versionCode (for example 77), lower than the fork's releases (7700+), so it
can't be installed over them. To try a change on the phone, use the test bench's smoke build (installs next to the real
app, see [pc-headunit.md](pc-headunit.md)) or publish your own release.

## Keys and secrets

- Signing key: `~/.android-keys/headswap-release.jks` + `headswap-release.properties`. **Back them up.** Losing them
  means phones can never receive your updates again.
- The same key and password are in the repo's Actions secrets (`HEADSWAP_KEYSTORE_B64`, `HEADSWAP_KEYSTORE_PASSWORD`).
  Keep two-factor authentication on your GitHub account: whoever controls it could publish a signed update.
- Approvals for developer releases: Settings → Environments → `upstream-release` (required reviewer: you).
