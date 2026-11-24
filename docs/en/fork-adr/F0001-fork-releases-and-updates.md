# F0001. The fork publishes its own signed releases, and phones update from the fork

- Status: accepted
- Date: 2026-11-24

## Context

The developer's APKs are signed with their key, which only they have. Android installs an update only when it is signed
with the same key as the installed app, so a build made from this fork can never update, or be updated by, the official
app. The user wanted their own changes and still wanted automatic updates on the phone.

## Decision

- The fork signs with its own key (`~/.android-keys/headswap-release.jks`, also stored as Actions secrets).
- `UpdateLogic.REPO` points at `Mkhzoumi/headswap`, so the in-app updater only looks at this fork's releases.
- `.github/workflows/fork-release.yml` builds and publishes every release from `my-changes`:
  - the developer's releases daily, after the user approves them (environment `upstream-release`), as `v<version>-fork`;
  - the user's own releases by hand, as `<developer version>.N`.
- The unit tests run before every build, with one retry for two timing-sensitive concurrency tests.
- versionCode = the developer's versionCode × 100 + N, because the updater only installs a strictly higher versionCode:
  the developer's next release (×100) stays above the user's releases in between (+1…+99).
- Release tags carry a `-fork` suffix so they never collide with the developer's tags; the updater ignores the suffix
  when comparing versions.

## Alternatives

- A plain clone with manual `adb install` updates — simpler, but no automatic updates. Rejected by the user.
- Publishing the developer's releases without approval — rejected: the app holds powerful permissions in desktop mode,
  so the user reviews each developer release before it reaches the phone.

## Consequences

- Switching between the official app and the fork build requires uninstalling (different signing keys).
- Losing the signing key means phones can no longer be updated; back up `~/.android-keys/`.
- Fork changes to the developer's files can conflict with future releases; a conflict stops the automatic release until
  it is merged on the PC.
