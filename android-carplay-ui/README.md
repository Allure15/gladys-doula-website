# Ridgeline Dock - custom CarPlay UI layer (2008 Honda Ridgeline, Android 11 head unit)

1024x600 landscape. **Top 80 %** ZLink/CarPlay content, **bottom 20 %** native dock:
`Trailer | Aux | Honda H (-> Settings) | Fog | Video`.

> **Status: written but NOT compiled or run.** No Android SDK or head unit was available when
> this was authored. Expect to fix build errors, and see "Must be verified on the unit" below.

## Channel separation (never merged)
| Channel | Role | Where |
|---|---|---|
| Bluetooth | handshake + HFP call/mic only | `StatusRepository.bluetooth` (adapter + HEADSET profile) |
| Wi-Fi | carries CarPlay video + media audio | `StatusRepository.wifiBars` 0-4 |
| CarPlay GUI (ZLink) | touch + screen | `zlink/` - modes 0x1/0x2/0x4/0x8 in `ZLinkMode` |

Three separate flows -> three separate slots in `StatusCluster`. Bluetooth stays live while CarPlay runs over Wi-Fi.

## Boot sequence
`BOOT_COMPLETED` (or `scripts/early-zlink.sh` from init.rc, earlier) -> `ZLinkEarlyService` starts the vendor ZLink
service -> waits for `ZLinkReadiness` (BT discoverable + hotspot ready, 20 s cap) -> launches `MainActivity` with
status already populated. The service also hosts the turn-signal overlay, CAN->blinker decoder and USB watcher.
Android components cannot start before system_server; `persistent="true"` (priv-app) is the earliest supported hook.

## Turn signal
Static green arrow, top corner, `TYPE_APPLICATION_OVERLAY` + `FLAG_NOT_TOUCHABLE` (touches pass to CarPlay),
hides 2 s after the last signal event. Needs `SYSTEM_ALERT_WINDOW` (`install-as-privapp.sh` grants it).

## CAN diagnostic (Honda H -> Settings -> CAN sniffer)
Reads a serial node, shows RECEIVING / NO DATA / BYTES-BUT-NO-FRAMES, a raw-hex tail, per-ID activity, and detects
**bits toggling at blinker cadence**. Tap a candidate -> assign LEFT/RIGHT/HAZARD -> overlay follows the car.
If it stays at NO DATA the CAN Pro box ("disconnect", profile *Hiworld-2004~2007 Odyssey*) isn't passing data - either
select the correct CAN profile for the 2008 Ridgeline or use the **ESP32 fallback**: CAN transceiver (e.g. SN65HVD230) ->
ESP32 -> USB-serial, one line per frame `"<hexid>:<hexbytes>\n"` (select "Source: ESP32 lines"). Only build it if decode stays unreliable.

## Updates (one pipeline: `UpdateCoordinator`)
1. **LocalSend** (phone + radio, port 53317, auto-accept, optional PIN): set the receive folder to `/update`. `UpdateWatcher` sees the file.
2. **USB**: `<stick>/RidgelineUpdate/*.apk` (FileObserver + `ACTION_MEDIA_MOUNTED` + 15 s rescan).
3. **Manual**: Honda H -> Settings -> *Open file manager* / *Pick update APK*.

All three: same package, **strictly higher versionCode**, **identical signing cert** -> stage -> wait until CarPlay is idle
(or **Install now**) -> 10 s countdown -> `PackageInstaller` -> `MY_PACKAGE_REPLACED` relaunches only this UI; ZLink is a separate app and is untouched.
Sign every release with the **same keystore** or the verifier (correctly) refuses it. Bump `versionCode` each build.

## Must be verified on the unit (placeholders in code)
- **ZLink package/service names and broadcast actions** (`ZLinkConfig`) are guesses. `BroadcastZLinkClient.attachSurface/sendTouch`
  are **stubs**: there is no public ZLink API, so rendering the phone screen inside the top 80 % needs the vendor SDK/AIDL (or placing the
  vendor ZLink activity into that region via the ROM's multi-window support). Until wired, the top area is an empty surface.
- **MCU/CAN serial node, baud, and framing** (`McuFrameParser`: `0x2E,id,len,data,checksum`) are assumptions - the raw-hex pane exists to confirm them.
- **Vehicle outputs**: Trailer, Aux and Fog do nothing until mapped under Settings -> Configure buttons (send a broadcast, write to a serial port such as an ESP32 relay board, or launch an app; each has a Test button). Every tap shows a toast with the result. **Video** launches the radio's video-input app, chosen under Settings -> Set video input app.
- **Silent install** and early start require a priv-app install (`scripts/install-as-privapp.sh`); otherwise Android shows its install prompt.
- **Icons**: were redrawn from `docs/mockup.png`; the Honda H is a hand-traced approximation - swap in an SVG of the exact logo if you have one.
  Drop it into `res/drawable/ic_honda_h.xml` (Android Studio: Vector Asset import).
- LocalSend: I did not verify a headless/CLI receive mode; use the GUI app with auto-accept, or check LocalSend's current docs.

## Build
```
cd android-carplay-ui && gradle wrapper && ./gradlew assembleRelease
```
