---
name: stand
description: Brings up the Headswap stand (test bench) without a car — a smoke build on the phone over USB and a head-unit (HU) window on the PC (CarLife emulator with video, mouse touch and steering-wheel buttons). Use when Yegor says "open the stand", "bring up the stand", "start the stand", "show it on the stand", "let's check it on the stand", "/stand".
---

# Stand

The stand = a live "CarLife HU …" window on Yegor's desktop. An emulator without a window (video to a file) does not count as the stand.

## Launch

From the repo root (worktree), phone connected over USB with adb, screen unlocked:

```
powershell -File pc-headunit\stand.ps1 -Build      # rebuild smoke from the current branch and install it
powershell -File pc-headunit\stand.ps1             # without rebuilding
```

Run it via PowerShell with `run_in_background: true`: the script waits until the window is closed (default `-Duration 3600`).

Parameters: `-Mode desktop|aa|keep` (bridgeMode, default desktop), `-Size 1280x720` (for the Vezel; 1920x720 — wide HUs), `-HuArgs '--touch-test'` (any `carlife_hu.py` flags), `-NoView`, `-Audio` (activation with `--audio`: desktop audio over USB, ADR 0025).

Sound: the window plays MEDIA and TTS to the default Windows output device (`sounddevice` module); `-HuArgs '--mute'` — no sound. With "Sound through Headswap" (`Звук через Headswap` on a Russian-locale phone) enabled, the phone is silent — the sound went to the HU. Silence on the phone with it disabled — first check the music volume on the speaker (`dumpsys audio`, STREAM_MUSIC speaker).

What the script does: for the duration of the build, removes `TEMPLATE_RENDERER` from the debug manifest (otherwise smoke won't install alongside release, ADR 0022), installs smoke, writes `bridgeMode`, activates the bridge from the PC (`ManualAccessLauncher`), runs `adb reverse tcp:7240`, starts `carlife_hu.py --view` and `BridgeService --ei debug_tcp 7240`. Emulator and phone logs go to `pc-headunit/out/stand-<time>/`.

## Check that the stand is up

Before saying "the stand is open", make sure the window exists and frames are coming through in its title:

```
Get-Process python | ? MainWindowTitle -like 'CarLife HU*' | select Id,MainWindowTitle
```

Title `CarLife HU  1280x720  NN fps` — video is flowing. `Waiting for the bridge...` for longer than 20 s — look at the script output and the phone log.

## In the window

- LMB — touch (DOWN/MOVE/UP), RMB/Esc/Backspace — BACK, MMB/Home — HOME.
- Row of steering-wheel buttons under the video (labels are in Russian): `Голос` (Voice) — MODULE_CONTROL VR=1 like the Vezel; VR_START, tracks, Play/Pause, `Трубка` (Pick up) / `Сброс` (Hang up), `Назад` (Back), `Домой` (Home) — CarLife hard keys. Presses in the emulator log: `view: руль <button>` ("руль" = steering wheel).
- A frame for Yegor: `ffmpeg -i <out>/carlife_video.h264 -vf "select=eq(n\,N)" -frames:v 1 f.png`.

## Stop

Close the window or `Get-Process python | ? MainWindowTitle -like 'CarLife HU*' | Stop-Process`. Only remove smoke from the phone when asked: `adb uninstall com.yegorkapomidorka.headswap.smoke`.

## Pitfalls

- Yegor's Xiaomi doesn't allow `input keyevent` over adb (no INJECT_EVENTS) — the screen can't be locked from the PC, ask Yegor.
- `INSTALL_FAILED_USER_RESTRICTED` — Yegor must unlock the phone and confirm the install.
- Activation is lost after a force-stop/reinstall — the script activates on every launch by itself.
