# PC test tools: head unit emulators, test bench, UI autotests (English)

> English translation of [`pc-headunit/README.md`](../../pc-headunit/README.md) and
> [`pc-headunit/autotest/README.md`](../../pc-headunit/autotest/README.md) as of the developer's release **v4.3.0**.
> The Russian originals are authoritative.

## `hu.py` — Android Auto head unit emulator

A minimal Android Auto head unit emulator for the PC, for debugging: it connects to AA's "head unit server" on the
phone (TCP 5277), goes through Version → TLS → Auth → Service Discovery, receives H.264 video into `out/video.h264` and
writes the whole exchange to the console and `out/session.log`. It works without a CarLife head unit, so it is handy
for checking the AA side separately from the Headswap app. The protocol is described in
[`docs/aa-headunit.md`](../aa-headunit.md) (Russian).

### Dependencies

- Python 3.10+
- `pip install -r requirements.txt` (`protobuf` 5.x; the generated `proto/*_pb2.py` need runtime >= 5.27.2)
- for tests: `pytest`; the test that checks the stream with `ffprobe` is skipped if `ffmpeg`/`ffprobe` aren't in PATH
- to regenerate `proto/*_pb2.py` (not needed to run): `pip install grpcio-tools==1.67.1`, then `python proto/gen.py`

### Certificate

Like a real head unit, the emulator must present a client certificate signed by Google Automotive Link. Certificates
aren't in the repo (`certs/` is in `.gitignore`). Put the PEM files into:

```
pc-headunit/certs/jvc/cert.pem   # JVC Kenwood, from aasdk: cert/headunit.crt and headunit.key
pc-headunit/certs/jvc/key.pem
pc-headunit/certs/hur/cert.pem   # from headunit-revived (now open-headunit): app/src/main/res/raw/cert and privkey
pc-headunit/certs/hur/key.pem
```

Sources: [opencardev/aasdk](https://github.com/opencardev/aasdk),
[andreknieriem/open-headunit](https://github.com/andreknieriem/open-headunit). The set is chosen with
`--cert jvc|hur` (default `jvc`). The tests need both sets.

### Running

```
adb forward tcp:5277 tcp:5277
python hu.py --duration 30 --cert jvc
```

On the phone first: Android Auto → Settings → tap "Version" 10 times → three-dot menu → "Start head unit server". The
server accepts one connection per start and hangs after a rough disconnect, so stop the script with Ctrl+C or
`--duration`: it closes the session properly.

Useful flags: `--check` (local certificate and TLS check without connecting), `--touch-test` (a tap in the centre
10 s after the first frame), `--resolution`, `--fps`, `--moto`, `--full-audio`, `--minimal`. Full list:
`python hu.py --help`.

## `carlife_hu.py` — CarLife head unit emulator

Standard library only. By default (`--transport stream`) it is a TCP server: the Headswap bridge on the phone connects
to it with one connection through `adb reverse tcp:7240 tcp:7240`; the whole exchange goes with the outer channel
header. The log and video go to `out/carlife_hu.log` and `out/carlife_video.h264`.

```
adb reverse tcp:7240 tcp:7240
python carlife_hu.py --duration 120
```

## The test bench in one command (`stand.ps1`)

Phone on USB with adb, HU window on the PC:

```
.\stand.ps1 -Build              # build and install smoke, desktop mode, activation, window
.\stand.ps1                     # without rebuilding
.\stand.ps1 -Mode aa -Size 1920x720 -HuArgs '--touch-test'
```

During the build the script removes `TEMPLATE_RENDERER` from the debug manifest (otherwise smoke can't be installed
next to release, ADR 0022), writes `bridgeMode` into `aa.xml`, activates the bridge from the PC
(`ManualAccessLauncher`), runs `adb reverse`, starts `carlife_hu.py --view` and the bridge service. Emulator and phone
logs go to `out/stand-<time>/`. The Claude skill for this is `.claude/skills/stand`.

## Live window

`--view` opens a window with the bridge's video in real time; mouse clicks go to the bridge as HU touches. It needs
tkinter (bundled with Python for Windows) and `ffmpeg` in PATH. The emulator listens on the port, so it starts first,
then the bridge:

```
adb reverse tcp:7240 tcp:7240
python carlife_hu.py --view --size 1920x720
# in another terminal, while the window waits for the bridge:
adb shell am start-foreground-service -n com.yegorkapomidorka.headswap.smoke/com.yegorkapomidorka.headswap.BridgeService --ei debug_tcp 7240
```

The smoke package (`applicationId` with the `.smoke` suffix) is built by `assembleSmoke`; `debug_tcp` is enabled in
it. The service class name is written in full: it lives in the `com.yegorkapomidorka.headswap` namespace, and `am`
would expand the short `.BridgeService` into the `...smoke` package. The debug build's package is
`com.yegorkapomidorka.headswap`, with the same `--ei debug_tcp 7240`.

- `--size WxH` (default `1920x720`) — the HU screen the emulator asks for in `VIDEO_ENCODER_INIT`. The bridge answers
  with its own size in `INIT_DONE`; the log shows both (`video size from INIT_DONE: 1920x720 (same as asked)`) and the
  window follows the bridge's answer.
- `--view-scale K` — window scale. By default it fits the PC screen (at most 1.0); click coordinates are mapped back
  to video pixels.
- `--duration` with `--view` is unlimited by default; closing the window or Ctrl+C ends the session the same way as a
  timeout (with the summary in the log).
- Left button: press = DOWN, drag = MOVE (at most 60 per second), release = UP. Right button, Esc, Backspace — the
  BACK hard key (`TOUCH_CAR_HARD_KEY_CODE`, code 0x0E); middle button and Home — HOME (0x01; the bridge turns it into
  Android HOME only in Headswap Desktop mode).
- Also works with `--transport tcp6`.

Pipeline: video is written to `out/carlife_video.h264` and at the same time piped into `ffmpeg`
(`-f h264 -i pipe:0 -f rawvideo -pix_fmt rgb24`); frames are shown through tkinter without Pillow. The window doesn't
block the emulator's network loop: ffmpeg is fed from a separate thread and only the latest frame goes to the window.
Picture latency is about 70 ms. To check the pipeline without a phone:
`python carlife_view.py file.h264 1920x720 0.75`.

## Six-socket mode (wireless format)

In CarLife's wireless format each channel has its own TCP socket and there is no outer header. The phone listens on six
ports and the emulator connects to them as a client (CMD 7240, VIDEO 8240, MEDIA 9240, TTS 9241, VR 9242,
TOUCH 9340). The ports are forwarded from the phone with `adb forward`, so the default host is 127.0.0.1:

```
adb forward tcp:7240 tcp:7240
adb forward tcp:8240 tcp:8240
adb forward tcp:9240 tcp:9240
adb forward tcp:9241 tcp:9241
adb forward tcp:9242 tcp:9242
adb forward tcp:9340 tcp:9340
adb shell am start-foreground-service -n com.yegorkapomidorka.headswap/.BridgeService --ez debug_tcp6 true
python carlife_hu.py --transport tcp6 --touch-test
```

The bridge on the phone starts first (it opens the ports), then the emulator. The session logic is the same:
HU_PROTOCOL_VERSION, the init burst after MD_INFO, authorization, a heartbeat every second, a tap 15 s after the first
frame, a summary at the end. For the Data channel (port 9440, which the phone accepts and ignores) there is
`--extra-data-socket`.

Checking how the bridge reacts to losing a channel: the emulator closes one socket and waits to see whether the bridge
closes the others.

```
python carlife_hu.py --transport tcp6 --kill-chan VIDEO --kill-after 10
```

`--kill-chan` takes CMD, VIDEO, MEDIA, TTS, VR, TOUCH; `--kill-after` counts from the first video frame. The summary has
`kill: VIDEO at 12.3 s, bridge closed others after 0.4 s` or `bridge did NOT close others within 10 s` (the latter
means the bridge kept hanging on to some of the channels).

## Pause cycles

The "HU pauses the video and starts it again" scenario (on START the bridge resets the decoder and waits for a new
IDR). Works in both modes:

```
python carlife_hu.py --pause-cycles 20 --pause-every 10 --pause-for 3 --duration 300
```

`--pause-cycles N` (default 0 = off), `--pause-every S` (default 10: seconds from the first video frame to the first
PAUSE, then from each START to the next PAUSE), `--pause-for S` (default 3: between PAUSE and START). `--duration` must
cover N × (every + for), otherwise the session ends earlier. The emulator sends `VIDEO_ENCODER_PAUSE` (0x0001800A,
empty body, like START), waits, sends `VIDEO_ENCODER_START` and measures from START: the time to the first video frame
and to the first key frame (a NAL of type 5 in the Annex-B buffer). Frames during the pause are counted separately: the
bridge must not send any on PAUSE; the first such frame is logged with its delay from PAUSE (a few ms = a frame "in
flight", tens or hundreds = the bridge didn't stop). The summary:

```
pause #1: paused_frames=0, start->first_frame=120 ms, start->idr=480 ms
pause cycles: 20, worst start->idr 910 ms, frames during pause total 0
```

`none` instead of a time means no frame or IDR arrived after START before the next PAUSE or the end of the session; the
summary line marks this as `(no IDR in K cycles)`. If the session ended in the middle of a pause, that cycle isn't
counted and the log has `session ended during pause`.

## Branch B: the phone's hotspot

The phone shares a hotspot and the PC connects to it as a HU client. You don't need the phone's address: the emulator
listens for the UDP beacon (`CarlifeHost` every 2 s), takes the sender of the first datagram as the host and then works
as in six-socket mode.

1. Turn on the hotspot on the phone and connect the PC to it.
2. Start branch B on the phone:
   ```
   adb shell am start-foreground-service -n com.yegorkapomidorka.headswap/.BridgeService --ez debug_wifi_b true
   ```
3. On the PC:
   ```
   python carlife_hu.py --transport tcp6 --discover --probe-8999 --touch-test
   ```

On Windows allow incoming UDP 7999 for python in the firewall (private network), otherwise the beacon won't arrive. If
the PC has several network adapters, pick the hotspot one: `--bind-ip <PC's IP on the hotspot network>`.

Flags: `--discover` (only with `--transport tcp6`), `--beacon-port` (default 7999), `--bind-ip` (default 0.0.0.0),
`--discover-timeout` (default 60 s; without a beacon the session ends with `no beacon within 60 s`), `--probe-8999`
(after the beacon, one UDP datagram to the phone's port 8999; the reply is awaited up to 2 s and is optional). The
summary prints `discovered_host`, the number of beacons and the probe result.

## Emulator tests

```
python -m pytest tests -q
```

The tests are self-contained: the "phone" is written in the test itself and talks to the session through
`socket.socketpair()`. No port 5277 and no phone needed.

---

# UI autotests on the HU test bench (`pc-headunit/autotest`)

Python clicks through Headswap's screen on the CarLife HU emulator (`carlife_hu.py`) and checks it without an LLM: text
through Windows OCR, pictures by comparing with reference images. The phone is connected over USB, as for `stand.ps1`.

## Requirements

- a phone on USB with debugging; on Xiaomi also "Install via USB" in developer options;
- `ffmpeg` in PATH, `opencv-python`, `numpy`;
- OCR: the pywinrt packages and the Russian recognition language in Windows:
  `pip install winrt-runtime winrt-Windows.Media.Ocr winrt-Windows.Graphics.Imaging winrt-Windows.Storage.Streams winrt-Windows.Foundation winrt-Windows.Foundation.Collections winrt-Windows.Globalization`.
  rapidocr doesn't work: its models read Cyrillic as Latin («Настройки» → «HacTpoMK»).

## Running

From `pc-headunit`:

```
python -m pytest autotest/scenarios -v                 # scenarios
python -m pytest autotest/scenarios -v --update-refs   # re-shoot the reference images
python -m autotest dump "text Настройки" dump back     # explore screens without pytest
```

Preparing the phone (building smoke, installing, `bridgeMode`, activation, `adb reverse`) is done by
`stand.ps1 -PrepareOnly`. `--no-prepare` skips it.

Before the scenarios, `stand.ps1 -PrepareOnly` resets the launcher settings on the phone to defaults (theme, colours,
panel size and style, date and clock format, navigator; `autoplay=false`): reference images must not depend on what was
set on the phone. With `--no-prepare` nothing is reset.

The report goes to `out/autotest-<time>/`:
- `steps/NN-*.png` — a frame per step;
- `actions.log` — what was pressed;
- `carlife_hu.log` — the emulator log;
- the phone log;
- `fail/*-actual.png` and `*-diff.png` — the frame and the difference when it doesn't match the reference.

## A scenario

```python
def test_settings_opens(stand):
    stand.tap_text("Настройки")   # "Settings" in the Russian UI
    stand.wait_text("Вид")        # "Look"
    stand.check("settings", masks=[STATUS])
```

| Method | What it does |
|---|---|
| `tap(x, y)`, `long_press`, `swipe(x1, y1, x2, y2)` | touches in video pixels: the frame is 1:1, no conversion |
| `back()`, `home()`, `voice()`, `key(code)` | HU buttons |
| `tap_text("phrase")` | tap on a label |
| `wait_text`, `wait_no_text`, `has_text`, `text()` | work with the screen text |
| `check("name", masks=[...])` | compare with `refs/<hu>-<W>x<H>/name.png` |
| `tap_image("icon")` | find the picture `refs/.../icons/icon.png` |
| `settle()` | wait until the screen stops changing |
| `shot("name")` | a frame into the report |

Text is compared case-insensitively, without diacritics (`ё` = `е`, `è` = `e`), treating Cyrillic and Latin
look-alike letters as the same letter. OCR sometimes writes «домой» in lowercase and puts a Cyrillic «х» into
«1280х720».

## Scenarios (`scenarios/test_desktop_smoke.py`)

| Test | What it checks |
|---|---|
| `test_home`, `test_all_apps` | the home screen against its reference; "All apps" opens and closes |
| `test_settings_sections` | goes through the sections; the "Look" reference (`settings-look`: 6 rows, segments, "Date" and "12-hour format" in half-rows) |
| `test_rail_style_glass` | "Panel style" → "Glass", the home reference `home-glass`, back to "Solid" and compare with `home` |
| `test_date_format` | "Date" DD.MM ↔ MM.DD: the order of numbers in the status line is read by OCR (the phone's date; skipped when DD = MM) |
| `test_clock12_in_look` | the 12 h toggle in "Look" changes the clock on the rail; it's no longer in "Screen" (between 10 and 12 o'clock the clock text doesn't change, so only the toggle's absence is checked). Clock digits are read by the English OCR in every language: the Arabic engine can't read "7:36" with "PM" |
| `test_navigator_button_toggles` | pressing navigation opens the navigator fullscreen, pressing again returns home |

Settings tests put everything back (`try/finally`). "Look" segments are pressed by their label, and if OCR didn't read
it (Chinese characters, «ДД.ММ»), by a fallback point at 1280x720. While music plays or a notification arrives, the
tiles and the top are covered by masks; a notification over the tiles (Telegram etc.) can still make `check` fail:
just rerun.

## Languages

Texts in scenarios are the app's string keys: `stand.R.l_sec_look`, `stand.R("l_battery", 80)`. `strings.py` reads them
from `android/app/src/main/res/values*/strings*.xml`. If a language lacks a key, English is used, as in Android.
Running per language:

```
python -m pytest autotest/scenarios -v --lang ru,en,fr
python -m pytest autotest/scenarios -v --lang all      # en ru ar fr zh-CN
```

- The language is set with `cmd locale set-app-locales`, and the app is restarted and activated again: the set of
  navigators and the layout depend on the language. Changing language takes ~20 s.
- Reference images are per language in `refs/<hu>-<W>x<H>/<language>/`; icons are shared in
  `refs/<hu>-<W>x<H>/icons/`.
- If a phrase isn't found but its beginning with an ellipsis is on screen ("Nothing is playi..."), the test fails with
  «обрезан многоточием» (cut off with an ellipsis).
- Windows OCR needs its own language pack for each app language
  (`python -c "from autotest.ocr import available_ocr; print(available_ocr())"`). Without a French pack the English
  engine reads French and confuses accented letters, so one error per phrase is allowed. Arabic and Chinese have no
  fallback engine. Install packs in an administrator PowerShell:
  `Add-WindowsCapability -Online -Name "Language.OCR~~~ar-SA~0.0.1.0"` (and `zh-CN`, `fr-FR`).
- The bridge placeholder («Запускаю рабочий стол…», "Starting the desktop…") is hard-coded in Russian for every
  language; `wait_ready` looks for it with the Russian OCR.

## Things to know about the test bench

- The first frame is the bridge placeholder «Запускаю рабочий стол…». The bridge doesn't accept touches then, and they
  don't appear in the phone log at all. `start()` waits for the real screen (`wait_ready`).
- The launcher remembers the screen from the last run, and the gear button is a toggle: if the bench came up on
  "Settings", tapping it closes them. So the fixture presses HOME before every scenario.
- Frames are fresh: the screen changes ~0.1 s after a tap; `settle()` after a tap matches the last frame of the stream.
  The ffmpeg decoder runs with `-threads 1`.
- Speed: after a tap the bench waits for the first changed frame (up to `change_wait` 0.6 s), then `quiet` 0.3 s of
  stillness. A repeated `settle()` without new frames returns immediately. A "tap + find text" step takes ~0.7 s; a tap
  that doesn't change the screen (HOME on home) ~1 s. Pilot run: 28 s → 16 s, ~6 s of which is starting the bench.
- Android animations are turned off for the run (`settings put global *_scale 0`) and restored after `stop()`;
  `--animations` keeps them. Xiaomi phones without "USB debugging (Security settings)" forbid this: the bench writes
  "animations stay on" to `actions.log` and runs with animations.
- Smoke and the real Headswap can't be activated at the same time: shell delegates the permissions to only one app.
  Stop the real one before using the bench (`adb shell am force-stop com.yegorkapomidorka.headswap`).

`check` counts a pixel as changed if the difference is above `tol` (24), and a screen as matching if at most
`max_ratio` (1%) of pixels changed. Cover clocks and notifications with masks `(x, y, w, h)`.

If there's no reference image, `check` creates it and the test is marked skipped («эталон создан», reference created).
Look at a new reference with your own eyes and commit it. References depend on the HU profile, the screen size, and the
desktop's theme and colours; fix the theme and colours before shooting them.
