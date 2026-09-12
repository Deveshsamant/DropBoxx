# DropBoxx protocol v1

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
| POST | `/api/v1/box/list` | `BoxListRequest{info, pairToken?, pin?}` | `200 BoxListResponse{owner, items, accessToken, pairToken?}`, `401`, `403`, `409` |
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
