# Decision records (ADR) — English index

English titles of the developer's decision records in [`docs/adr/`](../adr/) (written in Russian), as of release
**v4.3.0**. Each record explains *why* something is done a certain way, what else was tried and why it was rejected.
The code shows "how"; an ADR keeps "why". Open the linked file for the full text.

**When the developer writes one:** a choice between several approaches (re-encoding vs passthrough, P2P vs hotspot); an
option rejected after testing in a car (and likely to be tried again); an invariant that is easy to break by accident
("don't close the 5277 server", "START only after the RSP"); a workaround for a class of head units that changes
general behaviour. Not for a one-off fix, a rename or a text change. If the reason is unknown, the record says so
rather than inventing one. A revised decision keeps its old record with the status "superseded by NNNN".

**In this fork:** decisions specific to the fork go to [`fork-adr/`](fork-adr/) as `F<NNNN>-<slug>.md`, so they never
collide with the developer's numbering.

| No. | Decision | Status |
|---|---|---|
| [0001](../adr/0001-aa-client-of-hu-server-5277.md) | The bridge is a client of Android Auto's head unit server at 127.0.0.1:5277, without an intent into Gearhead | accepted |
| [0002](../adr/0002-no-accessibility-service.md) | No accessibility service: the user turns the head unit server on manually | accepted |
| [0003](../adr/0003-server-5277-one-shot.md) | The 5277 server is one-shot: never shut it down, check it is alive only with a full handshake | accepted |
| [0004](../adr/0004-video-transcode-default.md) | Default video: re-encoding 1280x720 / 15 fps, passthrough as the exception | superseded by 0018 |
| [0005](../adr/0005-own-idr-in-passthrough.md) | In direct passthrough the phone builds and sends the HU its own IDR frame | accepted |
| [0006](../adr/0006-per-hu-workarounds.md) | Head unit workarounds are enabled by a flag per channel/model, not globally | accepted |
| [0007](../adr/0007-video-heartbeat-on-pause.md) | VIDEO_HEARTBEAT on PAUSE: an empty message every 100 ms | accepted |
| [0008](../adr/0008-idr-size-bitrate-ceiling.md) | A bitrate ceiling based on IDR size is enabled per HU channel | accepted |
| [0009](../adr/0009-usb-write-chunking-16k.md) | USB writes: header separately, body in pieces up to 16 KiB, with exceptions | accepted |
| [0010](../adr/0010-baidu-carlife-reference.md) | The behaviour reference is Baidu CarLife 8.6.7; any difference from it counts as a bug | accepted |
| [0011](../adr/0011-decoder-feed-thread.md) | A separate thread (FeedQueue) feeds frames into the re-encoder's decoder | accepted |
| [0012](../adr/0012-updates-via-github-releases.md) | The app updates itself from GitHub Releases | accepted |
| [0013](../adr/0013-legacy-adb-separate-apk.md) | Legacy ADB mode is a separate APK with the package `com.baidu.carlife` | accepted (not in `main`; lives in the branch `claude/carlife-adb-release`) |
| [0014](../adr/0014-wireless-transport.md) | Wireless CarLife: CarTransport, Wi-Fi Direct and the car network beacon | accepted |
| [0015](../adr/0015-aa-sees-desktop-head-unit.md) | AA sees the HU as "Google / Desktop Head Unit"; the car brand is not part of the HU's identity | accepted |
| [0016](../adr/0016-diagnostics-from-app-log.md) | Diagnostics come only from the app's log; the log format is a contract | accepted |
| [0017](../adr/0017-hu-limits-wont-fix.md) | Head unit limitations are closed as "won't fix"; a registry | accepted (the registry keeps growing) |
| [0018](../adr/0018-passthrough-default-3-0.md) | Since 3.0 video uses direct passthrough by default, frame rate "Auto" | accepted |
| [0019](../adr/0019-launcher-youtube-fullscreen.md) | Fullscreen YouTube in the launcher hides the rail; the signal is the task's `requestedVisibleTypes` | accepted (not verified on a HU) |
| [0020](../adr/0020-desktop-audio-bluetooth.md) | In desktop mode sound goes over Bluetooth; music is not sent over USB | superseded by 0025 |
| [0021](../adr/0021-own-car-app-host.md) | The navigator on the desktop runs through our own Car App Library host | accepted |
| [0022](../adr/0022-car-host-template-renderer.md) | The Car App host declares and holds TEMPLATE_RENDERER so navigators with a host allow-list let it in | accepted |
| [0023](../adr/0023-desktop-density-by-height.md) | Desktop density is chosen from the screen height so Gboard isn't split in half | accepted |
| [0024](../adr/0024-knob-in-own-launcher.md) | The knob and HU buttons in our own launcher go into the app process, not injected into the display | accepted |
| [0025](../adr/0025-desktop-audio-usb-toggle.md) | Desktop sound over USB or Bluetooth again, by choice; one switch for both modes | accepted, replaces 0020 |
| [0026](../adr/0026-desktop-voice-key-phone-assistant.md) | The voice button on the desktop opens Alice in the left panel | accepted |
| [0027](../adr/0027-home-two-tiles.md) | Home screen: two tiles 2:1 and 1:2, "Layers" pages the big one, ⇄ swaps them | accepted |
| [0028](../adr/0028-nav-hints-toggle.md) | "Hints on the cluster": each navigator has its own choice between car mode and a window | accepted |
| [0030](../adr/0030-home-layers-list.md) | What "Layers" pages through is chosen in the "Screen" settings: built-in views and any apps | accepted |
| [0031](../adr/0031-telegram-multi-window.md) | Telegram in a tile starts in multi-window mode | accepted (test bench) |
| [0032](../adr/0032-hidden-status-bar-notch.md) | Without the status bar, notifications hang from a notch | accepted (test bench) |
| [0033](../adr/0033-android17-vdm-shell-host.md) | On Android 17 the desktop display is held by a shell VirtualDevice host; USB debugging is required | accepted |
| [0034](../adr/0034-alice-voice-only-hidden-display.md) | Alice by voice only: a hidden display and a banner | accepted, extends 0026 |
| [0035](../adr/0035-gemini-voice-assistant-key.md) | Gemini by voice: the assistant key and a Google Maps catcher | accepted, extends 0034 |
| [0036](../adr/0036-desktop-own-colors.md) | Own desktop colours: sliders instead of presets | accepted |
| [0037](../adr/0037-knob-into-windowed-apps.md) | The HU knob goes into a full-area embedded window as DPAD arrows | accepted |
| [0038](../adr/0038-desktop-two-finger-volume.md) | Two-finger volume on the desktop | accepted |
| [0039](../adr/0039-desktop-bitrate-and-own-idr.md) | CR-V 2026: desktop bitrate ceiling from the video mode, 30 fps pacer and own IDR that is never switched off | accepted |
| [0041](../adr/0041-amap-chinese-navigators.md) | In the Chinese interface the only navigators are Amap and Amap Auto | accepted |
| [0042](../adr/0042-own-ui-autotests-ocr.md) | UI autotests on the HU test bench are our own, via Windows OCR and reference images, without an LLM | accepted |
| [0043](../adr/0043-glass-rail-nav-toggle-look-list.md) | Glass rail, collapsing the navigator by tapping it again, clock and date in "Look" | accepted |
| [0044](../adr/0044-cn7-wait-hu-music-after-call.md) | Hyundai CN7: after a call the head unit itself brings the sound back | accepted |
| [0045](../adr/0045-vw-cns3-20082122-calls-to-hu.md) | VW CNS3 20082122: with the checkbox on, calls are reported to the HU, as on Delphi | accepted |
| [0046](../adr/0046-music-card-chosen-player.md) | The music card follows only the player chosen on the chip | accepted |
| [0047](../adr/0047-bonauto-20362105-small-video-packets.md) | Bon-Auto 20362105: video in small packets and a 1 Mbit/s ceiling, like Mazda 20622111 | accepted |
| [0048](../adr/0048-knob-double-home-hu-menu.md) | A double "Home" on the knob opens the HU menu | accepted |
| [0049](../adr/0049-home-style-glass-phone-card.md) | "Glass/Solid" style across the whole home screen, new "Phone" card | accepted |
| [0050](../adr/0050-yandex-maps-supersampled-window.md) | Yandex Navigator and Yandex Maps windows: a buffer larger than the window | accepted, not verified on a HU |
| [0051](../adr/0051-app-window-density-600dp.md) | An app window gets a density at which it is at least 600 dp wide | accepted, verified on a VW Bora (Android 17) |
| [0052](../adr/0052-glass-wallpaper.md) | Desktop wallpaper only in the "Glass" style | accepted |

Numbers 0029 and 0040 don't exist at v4.3.0. After merging a newer developer release, add its new records here.
