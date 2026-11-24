# Log analysis tools (English)

> English translation of [`tools/README.md`](../../tools/README.md) as of the developer's release **v4.3.0**.
> The Russian original is authoritative. Log strings below stay in Russian: the tools match them literally.

## logdiag.py — likely cause and what to do

Input: a user's log. Output: 1–3 likely causes with evidence from the log, advice for the user and a question worth
asking. It works on top of `logdigest.py`. Python 3 standard library only; runs the same on Windows and Linux.

```
python -X utf8 tools/logdiag.py <archive.zip | folder | file.log> [--json] [--all]
```

- `--all` — every rule that fired, not only the best 3; `--json` — facts and causes as structured data.
- The rules are data in `logdiag_rules.json`: `markers` (regexes over the raw log; matches within 2 s count as one
  event) and `rules` with `all`/`any`/`none` conditions of the form `[fact, operator, value]`. Operators:
  `== != >= <= > < ~` (regex) and `<v`/`>=v` (version). Facts: `m.<marker>` — the number of events; summary facts from
  the digest — `n_sessions`, `frames_max`, `aa_video_max` (video from AA, not placeholder frames), `eio_sessions`,
  `eio_short` (dropped over USB within ≤15 s), `no_gms`, `pause_eio` (EIO 3–10 s after VIDEO_ENCODER_PAUSE),
  `version`, `sdk`, `hu`, `channel`, `requested`, `ep_<disconnect type>`. Missing data (`None`) is not zero.
- `score` — the rule's confidence; `boost` adds to it; `suppresses` silences more general rules; `fixed_in` — the rule
  doesn't fire if the user's version is at least this, otherwise "please update" is added to the advice.
  `category: note` — a note under the causes (old version, "everything worked").
- A new cause = a new rule in the JSON plus a log line from a real case in `markers`. Check:
  `python -X utf8 -m unittest tools/test_logdiag.py`.
- The same rules ship inside the app (Gradle `copyLogdiagRules` → assets, read by `diag/LogDiag.kt`), so Python and
  Kotlin must give the same result (`LogDiagTest.kt`).

## logdigest.py — summary of a drive's logs

Compresses a Headswap log archive into a 40–80 line digest: environment, a table of sessions, disconnects, fps, errors,
AUTOSRV, sound and microphone, navigation, touch. Python 3 standard library only.

```
python -X utf8 tools/logdigest.py <archive.zip | folder | file.log> [--json]
```

Input:
- the zip from the app (share report / save archive): `device.txt` + `logs/headswap-*.log` (`probe-*.log` in old
  versions), logs trimmed to the last 4 MiB;
- a folder with full logs taken over adb (searched recursively, `logcat-*.txt` is picked up too);
- a single `.log`.

`--json` outputs the same data as a structure; the text is built from it.

What the sections contain and where it comes from:
1. **Environment** — `device.txt`; the HU only from `HU_INFO` (the `аксессуар:` (accessory) line is always Baidu
   CarLife; it is not the HU).
2. **Sessions** — from `== USB_ACCESSORY_ATTACHED` / `сессия: старт` to `== РЕЗЮМЕ СЕССИИ`; duration and reason come
   from the summary. A `~` before the start time means the session began before the log was trimmed (time estimated).
   "лог кончился" (log ended) means there is no summary. The AA column is the main AA session (`AA итог`, the largest
   data); the number of AA sessions is in brackets (usually the second one is a short "bridge stopped" placeholder).
3. **Disconnects** — EOF, EIO/ENODEV, BYEBYE_REQUEST, USB_ACCESSORY_DETACHED, peer closed, POWER_DISCONNECTED from
   logcat ("ignition"); events within 15 s are glued into one episode. The "no GMS" flag means EOF within ≤5 s after
   MEDIA_START (not MEDIA_PLAYBACK) with config=0 and 0 video frames.
4. **Video** — `BRIDGE видео[...]` and `видео: отправлено ... (+N за 5с)`; intervals outside 3–15 s are discarded.
5. **Errors** — `ERROR` and lines with `Exception`, with numbers normalized.
6–9. AUTOSRV, sound/microphone (`MIC_REQUEST` on NAVIGATION(10) is not the microphone), navigation (0x18043/0x18045,
   NAVI_NEXT_TURN_INFO), touch.

After that, grep the log only in windows around the times from the digest.

Test: `python -X utf8 -m unittest tools/test_logdigest.py`

## idr_splice_check.py — our own IDR on an AA dump

On a recorded H.264 stream from Android Auto (for example a `pc-headunit` dump), checks the same thing the app does
with the developer setting "Own keyframe in direct passthrough" (`aa/IdrSplice.kt`): whether the stream qualifies
(CAVLC, `max_num_ref_frames = 1`), how big the IDRs are (limit 560,000 B), and whether the picture matches
bit-for-bit after splicing and when starting from our IDR. Needs numpy and ffmpeg. It checks only the lossless IDR; if
that is over the limit, the app sends a compressed IDR (`aa/IdrIntra.kt`), which is covered by JVM tests against a
reference verified with ffmpeg.

```
python -X utf8 tools/idr_splice_check.py <dump.h264> [--every 60] [--pcm-only]
```
