# Testing

## Automated
`./gradlew :desktopApp:test` runs the engine end-to-end with two complete engines on loopback
(real TLS, real Netty, real files):

| Test | Covers |
|---|---|
| filesTextAndLinkArriveIntactAfterAcceptAndTrust | push transfer, accept dialog, trust pairing, SHA-256 identical bytes, notifications, history |
| trustedSenderIsAutoAcceptedWithoutDialog | Quick Save path |
| declinedTransferReportsDeclinedToSender | decline path and dialog state reset |
| receiverPinIsEnforced | PIN 401 handling |
| senderCancelAbortsReceiverAndCleansUp | cancel mid-stream, partial file removal |
| sameNameFilesInOneSessionStayIntact | concurrent same-name files get distinct names, no interleaving |
| boxSurvivesRestartAndPeerCanFetchAfterApproval | box persistence, access prompt, always-allow token, ranged fetch |
| deniedAndTrustedOnlyPolicies | Deny, Trusted-only and Anyone policies |
| downloadTokenIsRequired | downloads without a valid token are refused |

Test engines run with multicast disabled so they never appear on the real LAN.

## Manual matrix (before each release)
1. Phone + PC on the same Wi-Fi: both appear in Devices within 5 s of launch.
2. Drop a photo, a 1 GB video, a PDF, a text note and a link into the phone box; open it from the
   PC, fetch all; verify sizes, open each from Transfers (image viewer, PDF app, browser).
3. Reverse direction (PC box -> phone); files land in Pictures/Movies/Download -> DropNest.
4. Turn Wi-Fi off mid-fetch, back on: fetch resumes and completes (Transfers shows retry).
5. Hotspot mode on the phone, PC joins: PC finds the phone within 15 s.
6. Deny / Always allow / PIN flows; revoke a trusted device in Settings and confirm the prompt returns.
7. Android: share a PDF from Files, a photo from Gallery, a link from Instagram and a message
   with a link from WhatsApp into the app; each appears in the box (links extracted). Background
   the app and fetch them from the PC while the phone screen is off.
8. Windows: close to tray, fetch from the phone while minimised; "Open with" a file from Explorer.
