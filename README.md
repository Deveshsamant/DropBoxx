# DropNest


Drop anything - files, photos, videos, documents, text, links - into your box, any time, even with
no other device around. Whenever another device running the app is on the same Wi-Fi (or on your
phone's hotspot when there is no router) it can open your box, pick the items it wants and
download them. No accounts, no cloud, no internet: everything moves device-to-device over TLS.

## Platforms

| Target | Tech | Distribution |
|---|---|---|
| Android 8.0+ (API 26+, target 36) | Kotlin, Compose | Google Play (`.aab`) - see `docs/PUBLISHING-PLAY.md` |
| Windows 10/11 | Kotlin/JVM, Compose Desktop, bundled JRE | Microsoft Store (`.msix`) or MSI - see `docs/PUBLISHING-WINDOWS.md` |

macOS and Linux build from the same desktop module (`packageDmg`, `packageDeb`).

## Features

* **My box** - persistent shelf. Android: share from any app (WhatsApp media/documents,
  Instagram or browser links, Gallery photos) straight into the box - copies are kept in private
  storage so they survive the original being deleted. PC: drag & drop, "Open with", command line.
  Also file picker and clipboard paste on both. Items stay until you remove them. Tap a file to open it: images in
  the built-in viewer, everything else (PDF, video, docs) in the system's app.
* **Devices** - zero-config discovery (multicast + unicast registration + subnet scan + manual IP).
  Tap a device to see what it dropped, select items, **Fetch**.
* **Approval & trust** - the owner chooses who can open the box: *Ask me* (a prompt with
  Allow once / Always allow / Deny), *Trusted only*, or *Anyone nearby*. *Always allow* pairs the
  devices with a per-device secret token. Optional PIN.
* **Chats** - message a trusted device any time; it's delivered the moment both are on the same
  network (queued with a clock tick until then, double tick when it lands). Trusted devices only,
  nothing leaves your Wi-Fi.
* **Transfers** - parallel streams, live speed/ETA, resume after a dropped Wi-Fi link, history.
* **Hotspot mode** (Android) - one tap local-only hotspot when no router is around.
* **Bluetooth fallback** (opt-in) - paired devices stay reachable for chat, links and small
  files with no network at all; Wi-Fi/hotspot remain the primary, fast route.
* **Nocturne UI** - the Claude Design prototype (`docs/design/DropNest.dc.html`) ported 1:1 to
  Compose: dark/light themes, a draggable 3D nest that fills as you drop, peers orbiting your
  device, conveyor and sheen on live transfers, staggered card entrances, bottom sheets on
  phones and dialogs on desktop. "Motion & depth" can be turned off in Settings.
* Windows: themed custom title bar (drag, double-click to maximise, resizable edges), system tray,
  minimize-to-tray, launch at login, single instance.
  Android: foreground service keeps receiving in the background; files land in Pictures/Movies/
  Music/Download -> `DropNest` via MediaStore (no storage permission on Android 10+).

## Build

Prerequisites: JDK 17+, Android SDK (platform 37 + build-tools), Windows SDK for MSIX.

```bash
./gradlew :desktopApp:run                       # run the Windows app
./gradlew :androidApp:installDebug              # install on a connected phone
./gradlew :desktopApp:test                      # engine integration tests (two devices on loopback)
./gradlew :androidApp:bundleRelease             # Play bundle (needs androidApp/keystore.properties)
./gradlew :desktopApp:packageReleaseMsi         # Windows installer
./gradlew :desktopApp:createReleaseDistributable && powershell -File packaging/windows/build-msix.ps1   # Store package
```

## Layout

See `docs/ARCHITECTURE.md` (modules, engine, UI) and `docs/PROTOCOL.md` (wire protocol).

## Roadmap ideas

Clipboard sync, web-receive page for devices without the app, QR pairing, folder transfers with
on-the-fly zip, image compression option, Windows "Send to" shell entry, Android Quick Settings
tile, iOS via Kotlin/Native, localisation (Hindi first), one-time Pro unlock.
