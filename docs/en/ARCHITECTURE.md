# Headswap architecture (English)

> English translation of [`docs/ARCHITECTURE.md`](../ARCHITECTURE.md) as of the developer's release **v4.3.0**.
> The Russian original is authoritative; after merging a new developer release, check it for changes
> (`git diff v4.3.0 -- docs/ARCHITECTURE.md`). Notes marked **[fork]** were added in translation.
> Log strings are kept in Russian on purpose: they are matched literally by `tools/logdiag_rules.json`.

A map for anyone opening the repo for the first time (human or Claude): what lives where, how data flows, what must not
break. Why things are done this way is in [docs/adr/](../adr/README.md) (English titles: [adr-index.md](adr-index.md)).
Protocols are in `docs/carlife-protocol.md`, `docs/carlife-v2-handshake.md`, `docs/aa-headunit.md`.

Code references use class and function names, not line numbers: those go stale quickly. Find them with grep or LSP.
`$P` = `android/app/src/main/java/com/yegorkapomidorka/headswap`.

Glossary: **HU** = head unit (the car's screen, Russian «ГУ»); **MD** = mobile device (the phone, in CarLife terms);
**AA** = Android Auto; **passthrough** = forwarding AA's H.264 video as-is; **re-encoding** = decode and encode again;
**desktop** = Headswap Desktop, the app's own launcher on a virtual display; **cluster** = the instrument cluster
behind the steering wheel; **wireless** = CarLife over Wi-Fi; **dongle** = an Android USB stick in the HU's port that
relays to the phone over Wi-Fi; **knob** = the HU's rotary controller.

## What it is

The phone sits between the Baidu CarLife protocol the HU speaks and Android Auto on the same phone:

```
car HU  ⇄  CarLife (USB AOA or Wi-Fi)  ⇄  Headswap  ⇄  TCP 127.0.0.1:5277  ⇄  Android Auto (head unit server)
```

To the HU, Headswap is a phone running Baidu CarLife (MD). To AA it is a head unit ("Desktop Head Unit"). The user
turns on AA's head unit server themselves in AA developer mode (ADR 0001, 0002).

The HU talks to Baidu CarLife, not to AA, so some AA features are lost: anything AA expects from the car only arrives
if CarLife has an equivalent. That includes the map's dark/light theme (CarLife has no headlight sensor, only
CHANGE_MD_THEME), gear and reverse, speed and other car events (we do not request CarLife car data blindly, ADR 0010).
Before promising an AA feature, check whether CarLife has a message for it.

## Stack and build

- One Gradle module `:app` (`android/`), minSdk 29, target 37, JDK 17.
- **No AndroidX or Compose** in the phone app: `MainActivity : android.app.Activity`, screens are built in code
  (`ui/Kit.kt`, `ui/Screen.kt`). **[fork]** The Headswap Desktop launcher (`launcher/`) does use Jetpack Compose and
  `androidx.car.app` (see `android/app/build.gradle.kts`).
  Strings: `res/values` (en), `values-ru`, `values-ar`, `values-fr`, `values-zh-rCN` — edit them together. Texts the HU
  sees (the bridge placeholder, desktop errors) also come from resources, not literals in code.
- Variants: `debug`, `release`, `smoke` (build type: debug with the `.smoke` suffix, installs next to release).
  No flavors. `-Pwlbeta=false` turns wireless off.
- AA certificates (`assets/aa/{hur,jvc}`) are not in git. The `syncAaCerts` task copies them from the main checkout
  (or `HEADSWAP_AA_CERTS` / `-Pheadswap.aaCertsDir`); release builds fail without them.
  Release signing: `~/.android-keys/headswap-release.properties`.
- Tests: `cd android; ./gradlew testDebugUnitTest` (JUnit4, `android/app/src/test/...`). Python: `pc-headunit/tests/`,
  `python -X utf8 -m unittest tools/test_logdiag.py`. UI on the HU test bench (phone over USB):
  `cd pc-headunit; python -m pytest autotest/scenarios --lang all` — README in `pc-headunit/autotest/`, ADR 0042.

## Packages

| Where | What |
|---|---|
| `$P/` (root) | CarLife side and the service. `BridgeService` — the orchestrator. `CarLifeSession` — one session with the HU. `Handshake`, `HuAuth` — handshake and HU_AUTHEN. `Protocol.kt` (`Ch`, `Msg`, `Wire`, `FrameParser`), `Proto.kt` (own protobuf). `VideoSource` — placeholder picture until AA arrives. `AppLog`, `CrashLog`. |
| `$P/aa/` | AA side and the brain of the bridge. `AapSession` (TCP+TLS to 5277), `AapMessages` (SDR, channels), `AaBridge.kt` (`AaBridgeCore` — all the wiring and HU flags; `AaConnector` — reconnection), `BridgeHost` (keeps AA alive between CarLife sessions). Video: `AaTranscoder`, `GlRelay`, `FeedQueue`, `IdrSplice`/`IdrIntra`/`RefDecoder`, `SpsCrop`, `VideoMode`, `CarPreset`, `BitratePolicy`, `IdrBitrateCeiling`, `ThermalThrottle`. Sound: `AaAudio`, `MusicResampler`, `Equalizer`, `TtsPreroll`. Microphone: `AaMic`, `PhoneMicCapture`. Metadata: `AaMeta` (`MetaRelay`). Touch: `TouchPacer`, `TouchDecimator`. Settings: `AaSettings` (`AaPrefs`). Diagnostics: `AaServerCheck`, `AaProbe`. |
| `$P/link/` | Transport to the HU. `CarTransport` + `TransportCaps`, `StreamTransport` (USB and debug TCP, one stream), `MultiSocketTransport` (wireless, 6 sockets). `link/bt` — RFCOMM `carlife`, joining Wi-Fi Direct (`CarlifeBtProbe`, `P2pJoin`). `link/car` — the selected car, autostart on Bluetooth ACL, "car Wi-Fi" (`CarProfile`, `CarAclReceiver`, `CarNetwork`, `CarLogic`). `link/wifi` — the `HotspotBeacon` beacon (UDP 7999), `HotspotDetect`. `link/diag/WLog` — wireless log. |
| `$P/ui/` | Phone screens (`HomeScreen`, `OnboardingScreen`, `SettingsScreen`, `DevScreen`, `DiagScreen`, `CarChooser`), pure logic (`HeadswapState`/`HomeLogic`, `Readiness`), `LogShare` (zip of logs). |
| `$P/carapp/` | Own Car App Library host for the desktop navigator (ADR 0021). `CarAppCatalog` (finds CarAppService + NAVIGATION), `CarAppSession` (one session: bind → handshake → create/start/resume, surface, areas, Trip → `aa/DesktopNavHints`), `CarAppHost` (facade for the launcher), `CarUiMapper` + `CarUi` (templates → model for Compose), `CarAppLogic` (pure: areas, dpi, verdicts). Templates are drawn by `launcher/NavHostUi.kt`. |
| `$P/diag/` | `LogDiag` — Kotlin port of `tools/logdiag.py`, same rules. |
| `$P/update/` | `UpdateLogic` (GitHub releases/latest, pure), `Updater` (download, SHA-256, PackageInstaller). ADR 0012. **[fork]** `UpdateLogic.REPO` points at this fork. |
| **[fork]** `$P/dex/` | Desktop mode plumbing: activation from a PC or from the phone itself (`ManualAccessLauncher`, `DelegatedDesktopInstrumentation`, `PhoneActivationService`, `PhoneAdb` via Kadb), the virtual display (`DexUserService`, `VdmDesktop`), shell-uid helper processes (`UinputHostMain`, `VdmHostMain`), desktop audio and media control. |
| **[fork]** `$P/launcher/` | The Headswap Desktop launcher (Compose) shown on the HU: home tiles, rail, music and phone cards, notifications, wallpapers, Alice/Gemini voice, knob handling, embedded app windows (`EmbedHost`). |
| **[fork]** `$P/embed/`, `$P/notify/` | Registry of embedded app windows; the notification listener that feeds the launcher. |
| `src/debug`, `src/release` | `DebugTransport.kt`: in debug — TCP to the HU emulator; in release — a stub. |
| `pc-headunit/` | Python. `hu.py` — an AA head unit for the PC (`golden.txt` references for tests). `carlife_hu.py` — CarLife HU emulator for checking the bridge. `stand.ps1` — the test bench in one command (smoke build on the phone + HU window; `-PrepareOnly` — only prepare the phone). `autotest/` — UI autotests without an LLM: `Stand` (taps, HU buttons, Windows OCR, comparison with references `refs/<hu>-<W>x<H>/<language>/`), `strings.py` (strings by key from `res/values*`), pytest scenarios in `autotest/scenarios`. English: [pc-headunit.md](pc-headunit.md). |
| `tools/` | `logdiag.py` + `logdiag_rules.json` (cause of a disconnect from the log), `logdigest.py` (log summary). English: [tools.md](tools.md). |

The three core files are large: `AaBridge.kt` (~2800 lines), `CarLifeSession.kt` (~1500), `BridgeService.kt` (~1100)
(**[fork]** at v4.3.0: ~3500, ~1700, ~1600). Read them by the function you need, not whole.

## Data flow

**1. Start.** USB: the HU switches the phone into the Baidu/CarLife accessory → `USB_ACCESSORY_ATTACHED` in
`MainActivity` → `Actions.scan` → permission → `BridgeService.onStartCommand` → `command()` →
`startSession(StreamTransport.fromPfd(...))`.
Wireless: a button (`ACTION_WIFI_B`) or the Bluetooth ACL of the selected car (`CarAclReceiver` → `ACTION_WIFI_B_AUTO`)
→ `startWifiB` → `startTcp6`. The cable beats wireless. After the cable is unplugged while the car is still nearby over
Bluetooth, it switches to wireless after 3 s.

**2. Wireless transport.** The phone listens on 6 ports (CMD 7240, VIDEO 8240, MEDIA 9240, TTS 9241, VR 9242,
TOUCH 9340); the HU connects as a client. How the phone and the HU end up on the same network:
(a) BT RFCOMM `carlife` + joining the HU's P2P group (Mazda G6S); (b) the phone's hotspot + the `HotspotBeacon` beacon;
(c) "car Wi-Fi" — the phone joins the HU's network (`CarNetwork`, VW). ADR 0014.

**3. CarLife session** (`CarLifeSession.onCmd`):
HU_PROTOCOL_VERSION → start batch (`Handshake.startBatch`) → HU_INFO and STATISTIC_INFO (the HU is identified here,
see below) → HU_AUTHEN → `HuAuth` → FEATURE_CONFIG (AUDIO_TRANSMISSION_MODE=1 — sound over Bluetooth, not sent over
USB) → VIDEO_ENCODER_INIT (the size comes from the video mode, not from the HU; the HU's size is only remembered) →
START (if none within 1.5 s, we start ourselves) → PAUSE/START/RESET along the way → MD_EXIT.

**4. AA side** (`BridgeService.createHost` → `AaConnector` → `AapSession`): TCP 127.0.0.1:5277 → VERSION → TLS
(jvc/hur certificates) → AUTH_COMPLETE → SDR (`head_unit_make/model` is always "Google / Desktop Head Unit"; the
chosen car brand goes only into the `make` field; ADR 0015) → channels CONTROL0, SENSOR1, VIDEO2, INPUT3, SPEECH4,
SYSTEM5, MEDIA6, MIC7, BT8, MEDIA_PLAYBACK9, NAVIGATION10.
Closing is always ByeBye → shutdownOutput → read to EOF → close.

**5. Video** (`AaBridgeCore.onFrame` → `aaFrame`):
- *Re-encoding* (turned on manually, ADR 0004): `feedTranscoder` → `AaTranscoder` (decoder → `GlRelay` → encoder,
  the frame is repeated every 100 ms, thread `aa-trans-feed` via `FeedQueue` — ADR 0011) → `onEncoderFrame` → HU.
- *Direct passthrough* (default since 3.0, ADR 0018): `passthroughFrame`; borders are cropped by `SpsCrop` editing the
  SPS; every 2 s our own IDR (`IdrSplice`, ADR 0005; can be turned off with the developer checkbox `AaPrefs.ownIdr`).
- *Own IDR* (`IdrSplice`) checks `link.videoStats()` before sending: with a backlog > 200 ms or a queue > 10 frames the
  IDR is postponed; if `sendVideo` refuses, the pause grows (10/20/30 s) and insertion keeps trying, without being
  disabled for the rest of the session (ADR 0039).
- Into the HU: `CarLifeSession.sendVideo` → `AnnexB.fixSps` → `sendVideoData`. When the queue overflows (>30 frames or
  ~500 ms), frames are dropped until the next IDR.
- *Own launcher*: the phone encodes the Surface once; for the CR-V 2026 presets the bitrate ceiling is
  `VideoGeometry.bitrate` (WIDE_HD 6 Mbit/s), and on Wi-Fi it starts at the ceiling without ramping up; other cars keep
  the previous 8 Mbit/s policy (ADR 0039).
- "Passthrough or re-encoding" is decided in four places: `AaPrefs.transcode`, `VideoGeometry.effective()`,
  `BridgeService.startSession` (a mode that can't be cropped forces re-encoding), runtime HU flags (Pioneer, Mobis).

**6. Sound.** AA MEDIA(6) → CarLife MEDIA(3), AA SPEECH(4) → CarLife TTS(4), SYSTEM(5) is not forwarded.
Music is 48 kHz stereo (resampled by `MusicResampler` for 44.1/32/24/22), TTS 16 kHz mono. Ordering as in Baidu:
MODULE_STATUS NAVI=1 before TTS, TTS_STOP with a 1.5 s delay.

**7. Microphone.** The HU sends VR_MIC_DATA (PCM16 16 kHz) → `AaMicRouter` → `AapSession.sendMicPcm`.
Mode car/phone/auto (auto: after 2 s of silence from the HU, the phone's microphone).

**8. Touch.** `CarLifeSession.onTouch` → `AaBridgeCore.onCarlifeTouch` → `TouchDecimator` → `dispatchTouch` →
`VideoGeometry.mapTouch` → `AapSession.sendTouch`. Steering wheel buttons — `onCarlifeHardKey`.
On the desktop, `VolumeSwipe` sits before the decimator: two fingers at the right edge change the phone's media volume
(`DesktopMedia.adjustVolume`); always on the Vezel, otherwise per `AaPrefs.volumeSwipe` (ADR 0038).

**9. Navigation and cluster.** AA NAVIGATION(10) → `MetaRelay` → `sendNaviTurn` (NAVI_NEXT_TURN_INFO), only after the
HU subscribes. Honda: `HondaClusterRoad` shortens the street name to fit the cluster.

**10. Media metadata.** AA MEDIA_PLAYBACK(9) → `MetaRelay` → `sendMediaInfo` (cover art JPEG < 30 KB).

**11. End.** USB drop/EOF → `BridgeService.onSessionClosed` → `BridgeHost` keeps AA alive for 8 s and hands it to the
next session.

## Workarounds for specific head units

Rule: a workaround is enabled only on the HU where it was found, by a flag (ADR 0006). Exception from `CLAUDE.md`:
brand fixes apply to all models and channels of that brand (Honda is detected by the channel prefix 2077/2083).

The HU is identified in `AaBridgeCore.onHuInfo` (manufacturer/model/product/board from HU_INFO) and `onHuStatistic`
(`STATISTIC_INFO.channel` — effectively the HU firmware). Flags are `@Volatile var hu*` in `AaBridgeCore`; channel
constants live in its companion.

| Flag | HU | What it does |
|---|---|---|
| `huDesayG6S` | Mazda CX-5 Desay G6S, ch 20622116 | no route NAVI=1; on PAUSE an empty VIDEO_DATA (USB) / heartbeat (wireless) |
| `huHonda` | ch 2077xxxx/2083xxxx | roadName shortened for the cluster, wait for the subscription, re-arm NAVI |
| `huVw20082120` | ch 20082120/22/34/14, 21290002 | **really means "heartbeat on PAUSE every 100 ms"**; the name is historical (ADR 0007) |
| `huIdleVideoHeartbeat` | 20082114, 21290002 | heartbeat also without PAUSE after 1 s without frames |
| `huVwCns2Delphi` | 20082114 | speech into the VR channel, touch pacer, own call buttons |
| `huVwCns3_20082122` | 20082122 (Tharu XR) | together with Delphi (`huReportsCalls`): with the "Tell the head unit about calls" checkbox, MODULE_STATUS PHONE + TELE_STATE and a MEDIA pause (ADR 0045) |
| `huVwBt1420` | "VW BT 1420" | wireless: heartbeat on PAUSE, warm resume |
| `huVw20082138` | VW ID (20082138, 20082128+"VW BT 9491", preset ID. UNYX) | bitrate ceiling by IDR size (ADR 0008) |
| `huHyundaiCn7` | product `wp_cn7cpe_cn` | music at 32 kHz; after an HFP call the desktop waits up to 5 s for MUSIC=1 from the HU (`BT_HFP_INDICATION`, ADR 0044) |
| `huPioneerRm56` | Pioneer RM56E8B | re-encoding, 800 kbit/s, ≤10 fps, music at 22 kHz |
| `huMazda20622111` | ch 20622111 | VIDEO_DATA ≤ 15000 B, 1.5 Mbit/s, cover ≤ 12 KB (ADR 0009) |
| `huBonAuto20362105` | Bon-Auto (Outlander), ch 20362105 | like Mazda: VIDEO_DATA ≤ 15000 B, cover ≤ 12 KB; ceiling 1 Mbit/s (ADR 0047) |
| `huMobisNx4c` | board/model CCNC, ch 20022188 | "Baidu mode": AOA in a single write, 20 fps, own IDR ceiling |

**Adding a head unit:**
1. A channel/name constant in the `AaBridgeCore` companion.
2. `@Volatile var huXxx` with `private set`, next to the others.
3. **Reset it in `attach()`.** `AaBridgeCore` outlives a CarLife session (8 s grace); without the reset the flag leaks
   into the next HU.
4. Set it in `onHuInfo`/`onHuStatistic`.
5. The behaviour goes into `CarLifeSession` (ceilings, chunking, heartbeat) or into the core.
6. A test in `aa/AaBridgeTest.kt`.
7. Optionally a `CarPreset` and en/ru strings.

Resolution presets: enum `VideoMode` (R720 default, R800, R1024, R1080, WIDE*, WIDE_2_1, WIDE_640, WIDE_976).
Cars: enum `CarPreset`; choosing one writes transcode/videoMode/DPI/fps/musicRate at once. **Enums are stored in
prefs by name — never rename them.**

## Logs

- `AppLog`: logcat tag `Headswap` + the file `headswap-<date>.log` in the app's external files folder. `AppLog.msg`
  writes `RX|TX <CHANNEL> 0x… NAME len=…`. `WLog`: lines `WL <phase> <event> k=v`. `CrashLog`: crashes and the reasons
  past processes died (`low_memory`).
- The user's report is the zip from `LogShare` (`device.txt`, up to 5 logs of 4 MiB from the end, `wireless.log`).
  Where to get full logs: the `headswap-logs` memory (**[fork]** the developer's private Claude memory, not in the repo).
- Landmarks in the log (literal strings): `== USB_ACCESSORY_ATTACHED`, `сессия: старт` (session: start),
  `== РЕЗЮМЕ СЕССИИ (N с, причина: …)` (session summary, N s, reason), `== HU_INFO:`, `== STATISTIC_INFO:`,
  `== ГУ ПРОСИТ РАЗРЕШЕНИЕ` (HU asks for permission), `VIDEO_ENCODER_PAUSE`, `шлю пустой VIDEO_HEARTBEAT` (sending an
  empty VIDEO_HEARTBEAT), `BRIDGE IDR -> CarLife`, `AA сессия установлена` (AA session established),
  `AA нет VERSION_RESPONSE` (AA: no VERSION_RESPONSE), `EOF (ГУ закрыла сокет)` (EOF, the HU closed the socket).
  EIO is the system IOException text from `StreamTransport` (`ошибка чтения/записи: …`, read/write error).
- **Start log analysis with `tools/logdiag.py`**, then `logdigest.py`. The rules are shared with the app
  (`tools/logdiag_rules.json` → assets via `copyLogdiagRules`). **The log line format is a contract:** if you change a
  line, update the markers in the JSON (ADR 0016).

## Invariants (do not break)

- **AA server 5277 is one-shot.** A dirty disconnect or a bare TCP connect/close kills it, and the app cannot restart
  it. Close only via ByeBye; start a new AA session only after the old one is fully closed; never tear down a live AA
  session because of a mode/DPI change (a new mode applies after a manual server restart). Don't run
  `AaServerCheck`/`AaProbe` while the bridge is alive. ADR 0003.
- **A video frame was lost — send no P-frames**, wait for an IDR (request an IDR at most once every 1.5 s).
- **All H.264 to the HU goes through `AnnexB.fixSps`**: HUs don't accept the SPS from Exynos encoders.
- **Never send CAR_DATA_START blindly.** It isn't in the code at all now; a blind START froze the video on the Vezel.
  Only in reply to the RSP and only for announced modules (ADR 0010).
- **Packets to the HU are ≤ 16 KiB** (`Wire.CHUNK`), smaller for some HUs (ADR 0009).
- **`hu*` flags are reset in `attach()`.**
- **Only the main thread touches `BridgeService` state**; callbacks from other threads go through `main.post`.
- Crop modes in passthrough only work via SPS crop (`spsCropable`), otherwise fall back to R720.
- **Own Car App host: visible ≠ stable** (otherwise 2GIS hides the speed limit, cameras and mini-map); one fullscreen
  surface with a cutout; first onSurfaceDestroyed, then unbind. ADR 0021. Only a navigator with
  "Hints on the cluster" enabled goes into the host, otherwise it runs in a window; the shadow AA session also only
  with them. ADR 0028.
- **The host needs its own `android.car.permission.TEMPLATE_RENDERER`** in the manifest (declared and requested),
  otherwise navigators with a host allow-list (Yandex) answer "Unknown host" in the handshake. ADR 0022.
- **Android 17: the desktop display is created by a shell VirtualDevice host** (`dex/VdmHostMain`, uid 2000) via
  `VdmHostLink`; without the host the display falls into group 0 and goes dark together with the phone. The host lives
  in adbd's group and dies on every USB mode change; `VdmHostRestart` restarts it through the app's own wireless
  debugging, which on Android 17 needs "USB debugging" (required only on Samsung). ADR 0033.
- Passwords, SSIDs and keys are never written to the log.
- Experiment switches in the `AaBridgeCore` companion: `PAUSE_KEEPALIVE_ENABLED`, `NAVI_STATUS_ENABLED`,
  `MODULE_MUSIC_ENABLED`, `ROUTE_NAVI_STATUS_ENABLED`, `AIR_WARM_RESUME_ENABLED`, `CN7_CALL_HOLD_ENABLED`.

## Symptom → where to look

| Symptom | Where |
|---|---|
| EIO seconds after PAUSE | heartbeat on PAUSE: `CarLifeSession.pauseHeartbeat`, flag `huVw20082120`; ADR 0007 |
| EIO right after a large IDR | `IdrBitrateCeiling`, ceilings per channel; ADR 0008 |
| Desktop: the voice button does nothing / Alice not in the panel | `AaBridgeCore.phoneAssistant` → `DesktopKeys.voice` → `LauncherActions.toggleAssistant`, slot `EmbedHost.alice`; fallback `dex/PhoneAssistant`; log `ASSIST`, `EMBED alice`; ADR 0026 |
| A route from Alice not in the tile / stuck on the phone | `LauncherActions.assistantOpened`, `carapp/RouteIntent`, `CarAppSession.newIntent` (repeated getTemplate), `PhoneAssistant.closePhoneTasks`; log `ASSIST маршрут`, `CARAPP onNewIntent`; ADR 0026 |
| "Alice by voice only": silent on the lock screen / the banner is wrong | `LauncherActions.voiceOnly` (hidden slot `alice`, 1×1 layer, `assistantHidden`), `PhoneAssistant.watch` (moves Yandex's MainActivity), `launcher/AliceVoice` (states from the mic, speech and `DesktopAudioState.level`), `AliceVoiceUi`; log `ASSIST скрытая`, `ASSIST плашка`; ADR 0034 |
| Gemini by voice: silent / route or music goes to the wrong place | `LauncherActions.voiceOnly` (`gemini()`: no Yandex → KEYCODE_VOICE_ASSIST via `DesktopKeys.injectKey`), `PhoneAssistant.watchPhone` (Google Maps → `RouteIntent.googleMapsDir`, YouTube Music → `musicFromGemini`), voice USAGE_ASSISTANT in `DesktopAudioLoopback`; log `ASSIST Gemini`, `ASSIST YouTube Music`, `ASSIST маршрут`; ADR 0035 |
| USB stalls after a large packet | chunking `Wire.CHUNK`, `huMazda20622111`, `huBonAuto20362105`; ADR 0009, 0047 |
| Black screen after the reverse camera | own IDR (`IdrSplice`); ADR 0005 |
| Black borders, stretched picture, wrong size | `VideoMode`, `SpsCrop`, VIDEO_ENCODER_INIT in the log |
| AA doesn't connect, `нет VERSION_RESPONSE` | the 5277 server died: ADR 0003; the case base (the developer's `hu/` memory) |
| Wireless drops | `MultiSocketTransport`, `P2pJoin`, `CarNetwork`, `WL` lines; a VPN breaks the sockets |
| Sound silent / muffled | `AaAudio`, AUDIO_TRANSMISSION_MODE, TTS_STOP; the source selected on the HU |
| No hints on the cluster | the HU's subscription, `MetaRelay.sendNaviTurn`, `HondaClusterRoad` |
| Text on the HU not in the interface language | placeholder: `DelegatedDexConnector.text()` (`desk_ph_*`, `desk_err_*`); AA mode — `AaBridgeCore` texts from `BridgeService` (`aa_ph_*`); language — `AppLocale` |
| Test bench autotest fails | `out/autotest-*/<language>/`: `steps/`, `fail/*-diff.png`, `actions.log`; "cut off with an ellipsis" means a layout bug; README `pc-headunit/autotest` |
| Home: glass/solid in the wrong place, the "Phone" card draws a call wrong | `LauncherModel.railStyle` ("Style" setting) → `homeGlassStyle`; `PhoneCard.kt` (`phoneSurface`, `homeGlass`, `homeGlassEdge`, `homeGlassPanel`), `HomeScreen.QuickAppsCard`, the `NotificationsUi` shade, the `LauncherRoot` status line; wallpaper under glass — `WallpaperLayer` (see the wallpaper row), ADR 0052; ADR 0049 |
| Keyboard on the HU split in half | desktop density `DesktopGeometryPolicy.density` (short side < 600 dp), ADR 0023 |
| Home: wrong layout, a tile doesn't page with "Layers", ⇄ in the wrong place | prefs `home_layout`/`card_view`/`small_view` → `LauncherModel.tiles`; what "Layers" pages through — prefs `layer_list` (`Layer`/`LayerList` in `HomeTilesLogic.kt`, "Screen" settings → `LayersCard` and the "+" picker `LayerPickerScreen` in `LayersUi.kt`; the tile's app — prefs `tile_app`, window by `appCardRect`, ADR 0030); rules in `HomeTilesLogic`; tile windows in `EmbedUi.EmbedLayers` (an app tile is the app slot by `appCardRect`); ⇄ and "Layers" on the strip — `HomeTileButtons`, ADR 0027 |
| Rail: glass/solid, navigation lens in the wrong place, repeated navigator tap, "Look", date format | prefs `rail_style` (`RailStyle`) → `LauncherModel.railStyle`, `Rail(glass)`/`NavLens`/`glassRail` in `LauncherRoot.kt`, lens and divider geometry — `LauncherSpec.lensCenterY`/`dividerY`; no glass in portrait; repeated navigator tap — `LauncherActions.openNavigator` (fullscreen → home); "Look" — `SettingsPage.LOOK` (`LookRow`/`Segmented`), which also has 12 h (`clock_12h`) and the date `date_mdy` → `StatusReader.formatDate`, ADR 0043 |
| Desktop in the wrong colours / gold where your own accent should be | prefs `colors_night`/`colors_day` → `LauncherModel.palette` → `LauncherColors.palette` (default set = `Palettes.DARK/LIGHT`), accent — `LocalAccent` = `LauncherPalette.accent`; page `SettingsPage.COLORS`; ADR 0036 |
| Wallpaper not visible / the wrong one / the "Gallery" picker didn't appear | wallpaper only with `RailStyle.GLASS`: prefs `wallpaper`/`wallpaper_blur`/`wallpaper_dim`/`wallpaper_ver` → `LauncherModel.wallpaper*`; `LauncherRoot` puts the `WallpaperLayer` first in the root when `rememberWallpaper` returned a picture (otherwise the area right of the rail is painted `bgSolid`); pictures are read by `WallpaperLoader` (built-in `wallpaper_*` from drawable-nodpi, your own photo — `filesDir/launcher_wallpaper.jpg`); "Gallery" → `LauncherActions.openWallpaperPicker` → `MainActivity` cmd `wallpaper_pick` → `WallpaperImport` → `LauncherModel.setCustomWallpaper`; settings — `SettingsPage.COLORS` (`WallpaperCard`); log `LAUNCHER обои`; ADR 0052 |
| Music card: Play/cover open the wrong player, Play doesn't start the chosen one | the choice — `LauncherModel.selectedPlayer` (chip `PlayerChip.slots`); session pick `MusicLogic.pickSession(chosenOnly, chip)` in `MusicController.refresh` — only the chip's players; a chip player that starts playing becomes the choice (`LauncherActions.adoptPlayer`); start — `MusicController.startPlayer` (MediaBrowser + MEDIA_BUTTON, after 4 s `openPlayerApp`); log `MUSIC`, `LAUNCHER плеер`; ADR 0046 |
| The knob/HU buttons do nothing in the own launcher | `DelegatedDexConnector.sendRotary/sendKey` → `DesktopKeys.sink` → `launcher/LauncherKnob` (focus in `Pressable`), codes in `KnobLogic`, ADR 0024; in a full-area window — `LauncherKnob.toWindow` → `EmbedHost.key` → `injectChildKey`, log `LAUNCHER шайба: клавиши идут в окно`, ADR 0037; two "Home" presses within 700 ms open the HU menu (`KnobLogic.doubleHome` → `LauncherActions.huDesktop`, log `HOME дважды`), ADR 0048 |
| Desktop: two-finger volume doesn't work | `VolumeSwipe` (30% zone on the right, step 1/12 of the height) in `AaBridgeCore.onCarlifeTouch`, toggle `AaPrefs.volumeSwipe` / `huHondaVezel`, `DesktopMediaControl.adjustVolume`; log `жест громкости`, `BRIDGE громкость`; ADR 0038 |
| 2GIS on the desktop: no speed limit/cameras, the splash screen hangs, "Restart 2GIS" | `carapp/CarAppSession` (areas, stop order), the "Restart navigator" button; ADR 0021 |
| Navigator in a window instead of the host, log `handshake: отказ ... Unknown host` | TEMPLATE_RENDERER in the manifest; ADR 0022 |
| Huge yellow arrow and icons, few labels in Yandex Navigator/Maps in a window | the child display buffer is larger than the window: `launcher/EmbedRenderPolicy.geometry` (400 / DPI, packages `MAP_PACKAGES`) in `EmbedHost.reconcileInternal`; log `EMBED ... крупнее для карты`; ADR 0050 |
| An app (Yandex Pro, Zvuk) shows as a narrow portrait column in a window; Gboard split in half in an app window | the window's density is lower than the desktop's so it is at least 600 dp wide: `launcher/EmbedRenderPolicy.wideDensity` in `EmbedHost.reconcileInternal`; log `EMBED ... окно создано ... dpi=131 (стол 200, окну ниже: 611 dp)`; windows narrower than 366 px still show the column; ADR 0051 |
| Yandex Maps/Navigator ask for Plus, or a navigator runs in a window without hints | the "Hints on the cluster" setting (prefs `nav_hints:<pkg>`, off by default for all) → `CarAppHost.allowFor` in `EmbedHost.carTargetFor`; shadow AA — `NavigatorPick.shadowWanted`; ADR 0028 |
| Android 17: the HU screen goes dark with the phone, log `группа=0`, `VDM хост завершился` | USB debugging (`adb_enabled=0`), `VdmHostLink`/`VdmHostRestart`, the `VDM` and `VDMHOST` lines; ADR 0033 |
| Navigator in the host: a bare map, no buttons/list | the template wasn't parsed (`CarUi.Unknown`): `carapp/CarUiMapper.map`, `cmd car_dump` |
| Brightness/contrast flicker, the picture "breathes" | `BitratePolicy`/`DesktopVideoQuality` (8 Mbit/s on Wi-Fi), `IdrSplice` (queue), ADR 0039; log `битрейт энкодера`, `бэклог`, `свой IDR ... не принят` |
| Touch misses / is jerky | `VideoGeometry.mapTouch`, `TouchDecimator`, `TouchPacer` |

Specific cars and past investigations are in the HU case base in the developer's Claude memory (`memory/hu/INDEX.md`,
outside the repo because it contains user data). **[fork]** Not available in this fork.
