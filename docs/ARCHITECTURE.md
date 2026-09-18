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

Screens: **Onboarding** (first run: name, visibility), **My box** (3D nest + persistent items),
**Devices** (orbit of nearby peers, box access, hotspot), **Peer box** (browse, multi-select
fetch), **Transfers** (live cards with conveyor + history, tap to open), **Settings**. Adaptive
layout: 212 dp sidebar on wide windows, four-tab bottom bar on phones. ViewModels are
Koin-injected; state is `StateFlow` snapshots.

### Nocturne design system

The UI is a port of the Claude Design prototype (`docs/design/DropNest.dc.html`, Nocturne):

* `ui/theme/Nocturne.kt` - colour tokens for dark and light (`N.bg`, `N.surface`, `N.accent`, ...),
  exposed through `LocalNocturne`; a Material 3 scheme is derived from them only for text fields
  and progress indicators.
* `ui/theme/PhIcons.kt` - the Phosphor icon subset the prototype uses, as `ImageVector`s.
* `ui/motion/Motion.kt` - the shared easing (`cubic-bezier(.2,.8,.2,1)`), screen/dialog/sheet
  transitions, `rise` (staggered list entrance), `hoverLift`, `bobOffset`, and `SpinState`
  (auto-rotation with drag + inertia for the nest and the orbit).
* `ui/components/NestHero.kt` - the pseudo-3D nest: hex floor, six glass panels and item chips
  projected and depth-sorted on a `Canvas`; `Orbit.kt` - peers orbiting this device with depth
  scale/alpha; `TransferFx.kt` - sheen, conveyor and progress ring for live transfers.
* Motion can be switched off in Settings (`motion` setting); every animation checks it.

## Lag-free rules baked in

* No file I/O on the UI thread; everything streams inside `Dispatchers.IO`.
* Progress publishes at 5 Hz, never per chunk.
* Lists are lazy; image thumbnails are decoded by Coil off-thread.
* Desktop JVM runs with a small heap and serial GC for low latency.
