# DropBoxx

Drop anything - files, photos, videos, documents, text, links - into your box. Every device on the
same Wi-Fi (or on a phone hotspot when there is no router) can see your box and pull from it, and
you can push items straight to a device. No accounts, no cloud, no internet: everything moves
device-to-device over TLS.

> **Rename before publishing.** "DropBoxx" collides with the Dropbox(R) trademark; both stores
> screen for this. The name lives in `AppInfo.NAME`, `applicationId`, `strings.xml` and the MSIX manifest.

## Platforms

| Target | Tech | Distribution |
|---|---|---|
| Android 8.0+ (API 26+, target 36) | Kotlin, Compose | Google Play (`.aab`) - see `docs/PUBLISHING-PLAY.md` |
| Windows 10/11 | Kotlin/JVM, Compose Desktop, bundled JRE | Microsoft Store (`.msix`) or MSI - see `docs/PUBLISHING-WINDOWS.md` |

macOS and Linux build from the same desktop module (`packageDmg`, `packageDeb`).

## Features

* **My box** - persistent shelf; drop via drag & drop (PC), share sheet (Android), file picker,
  clipboard paste, or `Open with`. Items stay until you remove them. Tap a file to open it: images in
  the built-in viewer, everything else (PDF, video, docs) in the system's app.
* **Devices** - zero-config discovery (multicast + unicast registration + subnet scan + manual IP).
  Tap a device to open its box, select items, **Fetch**. Or **Send** selected box items to it.
* **Approval & trust** - the owner sees "X wants to open your box" / "X wants to send N items";
  *Always allow / Always accept* pairs the devices (per-device secret tokens). Optional PIN.
  Quick Save auto-accepts from trusted devices.
* **Transfers** - parallel streams, live speed/ETA, resume after a dropped Wi-Fi link, history.
* **Hotspot mode** (Android) - one tap local-only hotspot when no router is around.
* Windows: system tray, minimize-to-tray, launch at login, single instance.
  Android: foreground service keeps receiving in the background; files land in Pictures/Movies/
  Music/Download -> `DropBoxx` via MediaStore (no storage permission on Android 10+).

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
