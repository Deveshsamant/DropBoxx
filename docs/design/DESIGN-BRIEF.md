# DropNest - redesign brief for Claude Design

> **Status (2026-09-18):** implemented. The Nocturne prototype `DropNest.dc.html` (same folder) is
> ported to Compose; screenshots of the shipped UI are in `nocturne/`, the pre-redesign ones in
> `current/`. See `docs/ARCHITECTURE.md` -> "Nocturne design system" for where each piece lives.

**Design system: Nocturne.** Dark-first, electric blue on deep navy, glow + depth. Must also
have a light variant (user-selectable; dark is the default - no "follow the OS" option).

## What the app is
Drop anything (files, photos, videos, documents, text, links) into *your box* - even offline.
Any nearby device running DropNest (same Wi-Fi or phone hotspot) can open your box, tick items
and download them. There is **no "send"**: it is pull-only. No accounts, no cloud.

Platforms: Android phone (portrait, 360-430 dp wide) and Windows desktop (window >= 840 dp,
navigation rail on the left). Same screens, one code base (Compose Multiplatform, Material 3).

## Brand
* Logo: `logo.png` - blue bird curled around a nest of file icons, neon glow, navy tile.
* Palette from the logo: navy `#07164A`, deep blue `#0A3FB5`, electric blue `#1E7BFF`,
  cyan glow `#4FD6FF`, nest amber `#E08A2E`, leaf green `#3CC46E`, white text.
* Tagline: "Drop. Share. Access anywhere."

## Screens (current screenshots in `current/`)
1. **My box** - drop zone (drag & drop on desktop, "Add files" / "Paste" on both), list of items
   (thumbnail or type icon, name, size, remove). Tap opens the file (images in-app, others in
   the system viewer). States: empty, importing, items, item unavailable.
2. **Devices** - this device card (name, IP:port, visibility switch, "Who can open my box":
   Ask me / Trusted only / Anyone nearby), hotspot mode card (Android only), nearby devices list
   (trusted badge). Tap a device -> Peer box. States: no devices, scanning, hotspot on/off.
3. **Peer box** - the other device's items with checkboxes, "Select all", "Fetch selected /
   Fetch all", per-row Fetch. States: waiting for approval (the other user sees a prompt), denied,
   PIN required, busy, offline, empty, list.
4. **Transfers** - active downloads with progress/speed/ETA, history; tap a finished file to open.
5. **Settings** - device name, box access, PIN, auto-copy text, save folder (desktop), theme,
   tray/startup (desktop), trusted devices list, port.
6. **Dialogs** - "X wants to open your box" (Allow once / Always allow / Deny), PIN entry,
   add text/link, add device by IP, in-app image viewer (full screen, "Open with...").

## Motion & depth (please keep implementable)
* Depth = layered glass surfaces, soft glow shadows, subtle tilt/parallax on the drop-zone card
  and device cards (pseudo-3D via transforms). Avoid real 3D models - the app must stay light.
* Hero animation: bird lands / nest fills when an item is added; items "drop" into the list.
* Transitions: shared-element from a device card to its Peer box; progress rings that fill;
  list items animate in/out. Spring-based, 200-350 ms, no bounce on data-heavy lists.
* Keep 60 fps on mid-range phones: no full-screen blur behind scrolling lists.

## Constraints
* Material 3 components (we implement in Compose): buttons, chips, switches, list rows,
  navigation bar (phone) / navigation rail (desktop), snackbars, dialogs.
* Text sizes accessible (min 12 sp), touch targets >= 48 dp, works one-handed on a phone.
* Both dark and light themes; dark is primary.
* Deliver per screen: phone (390x844) and desktop (1280x800) artboards, plus a components sheet.
