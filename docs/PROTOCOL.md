# DropNest protocol v1

JSON over HTTPS between two devices on the same network. Every device runs its own server;
there is no central component and no internet dependency.

## Identity and trust

* On first launch each device generates a self-signed RSA-2048 certificate (10-year validity).
  `fingerprint` = SHA-256 of the DER certificate, lowercase hex. `deviceId` = first 20 hex chars.
* Clients **pin** the fingerprint a peer advertised: a per-peer `HttpClient` only trusts that exact
  certificate. A peer whose certificate changes fails the TLS handshake and must be re-discovered.
* Trust relationships are explicit: when a receiver ticks *Always accept* (push) or *Always allow*
  (box) it generates a random `pairToken`, stores it next to the peer's id/fingerprint and returns
  it once over TLS. The peer sends it back on later requests; a matching token = trusted.
* Optional receiver PIN: senders must include it in `pin`; wrong/missing PIN -> `401`.

## Discovery

| Tier | Mechanism | Notes |
|---|---|---|
| 1 | UDP multicast `239.255.77.77:47842` | `MulticastMessage{info, announce}` JSON; sent on start, every 12 s, and on refresh |
| 2 | `POST /api/v1/register` | Whoever hears an `announce:true` beacon registers back over unicast |
| 3 | Subnet scan `GET /api/v1/info` on every /24 host | Manual refresh / hostile networks; 48 parallel probes |
| 4 | Manual "Add by IP" | Same as tier 3 for a single host |

Peers expire after 45 s without a beacon or request.

## Endpoints (all under `https://<ip>:<port>`; default port 47843)

| Method | Path | Body / params | Response |
|---|---|---|---|
| GET | `/api/v1/info` | - | `DeviceInfo` |
| POST | `/api/v1/register` | `DeviceInfo` | `DeviceInfo` |
| POST | `/api/v1/prepare-upload` | `PrepareUploadRequest` | `200 PrepareUploadResponse`, `401` pin, `403` declined, `409` busy |
| POST | `/api/v1/upload?sessionId&fileId&token` | raw bytes, header `X-Offset` for resume | `200`, `404` unknown, `409 {bytesReceived}` offset mismatch |
| GET | `/api/v1/upload-status?sessionId&fileId&token` | - | `{bytesReceived}` |
| POST | `/api/v1/cancel?sessionId` | - | `200` |
| POST | `/api/v1/chat` | `ChatEnvelope{from, pairToken?, messages:[{id,text,sentAt}]}` | `200 ChatAck{accepted}`, `403` (sender not trusted) |
| POST | `/api/v1/box/list` | `BoxListRequest{info, pairToken?, pin?, visitToken?}` | `200 BoxListResponse{owner, items, accessToken, pairToken?}`, `401`, `403`, `409` |
| GET | `/api/v1/box/item/{id}?token=` | header `Range: bytes=N-` | `200`/`206` stream, `403`, `404` |

`prepare-upload` and `box/list` block (up to 90 s) while the receiving user decides; the sender
shows "waiting". TEXT and URL items travel inline in `FileMeta.content`/`BoxEntry.content`
(max 256 KB) and never go through the upload endpoints.

## Transfer semantics

* Files stream disk -> socket in 512 KB chunks, up to 3 files in parallel per session.
* Interrupted uploads resume: the sender asks `upload-status` and re-POSTs with `X-Offset`.
  Downloads resume with an HTTP `Range` request. Receivers append to `<name>.part` (desktop) or
  a pending MediaStore entry (Android) and finalise on completion.
* Receivers fail a session after 120 s without progress; senders retry 4 times with backoff.

### Refreshing a visit

`accessToken` from a `box/list` response is a *visit token* (6 h). Sending it back as
`visitToken` in a later `box/list` re-lists the box **without** a new approval prompt on the
owner's side and renews the token, so a browsing device can poll (the apps do so every 6 s while
the peer-box screen is open, plus a manual Refresh button) and pick up items dropped meanwhile.
A token that expired or was issued to another device is ignored and the normal flow applies.

### Chat

Messages are store-and-forward: the sender keeps them locally (`chat.json`) as PENDING and posts
the whole backlog for a peer to `/api/v1/chat` whenever discovery sees that peer (and every 15 s
while pending messages remain). The receiver accepts an envelope **only** if the sender is in its
trusted list with a matching pair token - there is no prompt; an untrusted sender gets `403` and
the message is marked FAILED with a hint to get "Always allow". Ids are deduplicated on the
receiver, so retries are safe. `accepted` ids flip to SENT on the sender (double tick).

## Bluetooth (secondary transport)

Wi-Fi/hotspot is always tried first. When *Bluetooth fallback* is on (Devices screen, off by
default), the same requests travel over one classic Bluetooth RFCOMM connection per request,
service UUID `7f1d3a2e-5c6b-4e8f-9a0b-d2e4c6a8f0b1`:

    frame = kind (1 byte) + length (4 bytes BE) + payload
    kind 0 = JSON BtMsg { type: hello | chat | list | get | ...-res | error, ... }
    kind 1 = file bytes (after a get-res), kind 2 = end of file

* Android listens (insecure RFCOMM, no pairing needed between phones) and connects.
* Windows only connects (Winsock `AF_BTH` via JNA; the JDK has no Bluetooth API) and the phone
  must be paired in Windows Bluetooth settings. Two PCs cannot reach each other over Bluetooth.
* Discovery: every 25 s each side says `hello` to paired/found phones and computers; anything
  that answers is added as a `Transport.BLUETOOTH` peer. A fresh Wi-Fi route always wins in the
  peer list (the MAC is kept as `bluetoothAddress` for later).
* Trust, PIN, private drops and visit tokens behave exactly as over HTTPS. Throughput is
  ~100-200 KB/s, so it is meant for chat, links and small files.
