# Store listing copy - paste into Play Console / Partner Center

Assets in this folder: `play-icon-512.png`, `play-feature-1024x500.png`, `msstore-logo-300.png`,
`screenshots/phone/*.png` (1080x1920, Play), `screenshots/desktop/*.png` (1920x1080, Microsoft Store).
Regenerate with `python packaging/store-shots.py <raw-captures-dir>`.

## App name
DropNest

## Short description (Play, max 80 chars)
Drop files, photos, text & links - your other devices pick them up over Wi-Fi.

## Full description (Play max 4000 chars; Microsoft Store max 10000)
A box that travels with you.

Drop anything into DropNest on one device - files, photos, videos, documents, text or links - and every other device you own on the same Wi-Fi can open your box and take what it wants, in one tap. No cloud, no account, no cable, no size limit.

HOW IT WORKS
• Drop: on your phone use the Share button in WhatsApp, Instagram, Gallery or your browser. On the PC, drag files onto the window or paste. Items stay in your box until you delete them - even when no other device is around.
• Meet: open DropNest on both devices on the same Wi-Fi. No router? Turn on hotspot mode on your phone. They find each other in seconds.
• Fetch: open the other device's box, tick what you want, press Fetch. Files land in your Gallery or Downloads, links and text go straight to your clipboard.

WHY DROPNEST
• Pull, don't push - nothing arrives uninvited. You browse and choose.
• Works without internet - same Wi-Fi or your phone's local hotspot.
• Encrypted device-to-device - every transfer runs over TLS between your own devices. Approve a device once, trust it forever, or protect your box with a PIN.
• Share sheet - anything an app can share goes into your box, and stays even if the original is deleted.
• Fast and resumable - parallel streams, resume after a dropped connection, live speed and ETA.
• Opens right there - view photos in the app; PDFs, videos and documents open in the app you already use.
• Phone ↔ PC, phone ↔ phone, PC ↔ PC - any two devices running DropNest.

PRIVACY
DropNest has no servers and collects nothing. Your files move directly between your devices on your own network. No analytics, no ads, no accounts.

Also available for Windows 10/11 - see the website.

## Categorisation
* Play: App · Category **Tools** · Free · no ads · no in-app purchases · Target audience 13+ (not designed for children)
* Microsoft Store: **Utilities & tools** · Free · all markets · age rating via IARC questionnaire (Everyone / 3+)

## Play - Data safety form
* Does your app collect or share any of the required user data types? **No**
  (device name, files and network addresses are processed on-device and sent only to devices the user chooses on the local network; nothing is sent to the developer or any third party.)
* Is all of the user data collected by your app encrypted in transit? **Yes** (TLS)
* Do you provide a way for users to request that their data is deleted? Not applicable (no collection); all local data is deletable in-app.

## Play - App content answers
* Privacy policy: `https://<your-vercel-domain>/privacy`
* Ads: No · App access: all functionality available without login · Content rating: Utility, no user-generated content shared publicly, no violence etc. -> Everyone
* News app: No · COVID-19: No · Government app: No · Financial features: No · Health: No
* **Foreground service permissions** (FOREGROUND_SERVICE_DATA_SYNC): task "Keeps the encrypted receive server running so files the user is fetching between their own devices finish even when the app is in the background. Started only when the user opens the app; stopped from the notification. Video: record 30 s showing the notification + a transfer completing while the app is backgrounded."
* Photo and video permissions: not requested (uses the system picker and share sheet).
* Nearby Wi-Fi devices (Android 13+) / location (≤12): "Required by Android to create a local-only hotspot when no router is available. Location is never read or stored." (declared with `neverForLocation`).

## Microsoft Store - Properties
* Category: Utilities & tools · Privacy policy URL: same as above · Website: your Vercel URL · Support contact: your email
* System requirements: Windows 10 1809 (build 17763) or later, x64 · Memory 4 GB
* Capabilities shown to users: internet & private network access (the app talks to devices on the local network only)

## Release notes (v1.0.0)
First release. Drop files, photos, text and links into your box; nearby devices on the same Wi-Fi or your hotspot can browse and fetch them. Encrypted transfers, trusted devices, PIN, share-sheet import, dark and light themes.
