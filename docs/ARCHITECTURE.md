# Architecture

Kotlin Multiplatform, one Gradle build, three modules:

```
shared/       Compose Multiplatform UI + engine (KMP library)
  commonMain      models, protocol DTOs, domain interfaces, ViewModels, Compose screens
  jvmSharedMain   the engine: Ktor/Netty HTTPS server, OkHttp-backed pinned clients,
                  multicast discovery, transfer + box controllers, JSON stores  (Android + desktop)
  androidMain     MediaStore saving, hotspot (LocalOnlyHotspot), share-sheet files, notifications
  jvmMain         Windows/macOS/Linux: drag & drop, native pickers, ~/Downloads, tray notifications
androidApp/   Android entry point: Application (Koin), MainActivity (share target, permissions),
              DropService (foreground service keeping the server alive)
desktopApp/   Desktop entry point: window, tray, single-instance lock, CLI file args; integration tests
```

Both targets are JVMs, so ~90 % of the code (including all networking) is written once in
`jvmSharedMain`; `commonMain` stays platform-free so an iOS/Kotlin-Native target can be added later
by porting only the engine's I/O layer.

## Engine (jvmSharedMain)

* `IdentityManager` - keystore + certificate, `DeviceInfo`.
* `PeerClients` - one pinned Ktor client per peer fingerprint, plus a trust-all probe client.
* `MulticastDiscovery` - beacons, register replies, subnet scan, peer expiry.
* `DropServer` - Ktor Netty HTTPS with all routes.
* `SessionRegistry` - single source of truth for live transfers; atomic byte counters folded into
  immutable snapshots 5x per second (keeps the UI at 60 fps during 100 MB/s transfers).
* `SendController` / `ReceiveController` - push model with accept dialog, tokens, resume, watchdog.
* `BoxRepositoryImpl` / `BoxAccessController` / `BoxClient` - pull model: persistent box, access
  approval and grants, ranged downloads.
* `JsonFileStore` - atomic JSON persistence for trust, history and the box.

## UI (commonMain)

Screens: **My box** (drop zone + persistent items, multi-select send), **Devices** (this device's
status, hotspot, nearby peers -> peer box), **Peer box** (browse, multi-select fetch),
**Transfers** (live + history, tap to open), **Settings**. Adaptive layout: navigation rail on
wide windows, bottom bar on phones. ViewModels are Koin-injected; state is `StateFlow` snapshots.

## Lag-free rules baked in

* No file I/O on the UI thread; everything streams inside `Dispatchers.IO`.
* Progress publishes at 5 Hz, never per chunk.
* Lists are lazy; image thumbnails are decoded by Coil off-thread.
* Desktop JVM runs with a small heap and serial GC for low latency.
