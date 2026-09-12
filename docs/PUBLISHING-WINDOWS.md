# Publishing to the Microsoft Store

## 1. Reserve the app in Partner Center
1. partner.microsoft.com/dashboard -> Apps and games -> **New product -> MSIX or PWA app**.
2. Reserve the (renamed, non-trademarked) name. Partner Center then shows, under
   *Product management -> Product identity*: **Package/Identity/Name**,
   **Package/Identity/Publisher** (`CN=...`) and **Package/Properties/PublisherDisplayName**.
   These three values must be copied into `packaging/windows/AppxManifest.xml`.

## 2. Build the MSIX
Requires the Windows 10/11 SDK (`makeappx.exe`, `signtool.exe`) - install via Visual Studio
Installer -> Individual components -> *Windows 11 SDK*, or `winget install Microsoft.WindowsSDK`.

```powershell
./gradlew :desktopApp:createReleaseDistributable      # app-image with bundled JRE
powershell -File packaging/windows/build-msix.ps1 -Version 1.0.0.0 -IdentityName "<from Partner Center>" -Publisher "<CN=... from Partner Center>" -PublisherDisplayName "<name>"
```
Output: `desktopApp/build/msix/DropBoxx.msix`. For local testing the script can also create and
trust a self-signed certificate (`-SelfSign`); Store submissions must be **unsigned** - the Store
signs them with Microsoft's certificate.

## 3. Submit
1. Partner Center -> your app -> **Start your submission**.
2. *Packages*: upload the `.msix`. It must declare `runFullTrust` (it does) and pass the automatic
   checks (Windows App Certification Kit runs in the cloud).
3. *Properties*: category Utilities & tools; privacy policy URL (same page as Play).
4. *Store listings*: description, 1366x768 or larger screenshots (PNG), 300x300 logo.
5. *Pricing and availability*: Free, all markets.
6. Submit - certification typically takes 1-3 business days.

## Local install for testing (no Store)
`./gradlew :desktopApp:packageReleaseMsi` builds a classic installer in
`desktopApp/build/compose/binaries/main-release/msi/`. Windows SmartScreen warns for unsigned
MSIs; sign with a code-signing certificate (Azure Trusted Signing is ~US$10/month) for direct
distribution outside the Store.

## Firewall
The first launch triggers the Windows Defender Firewall prompt (the app listens on TCP 47843 and
UDP 47842). Users must click **Allow** on private networks; the MSIX declares
`privateNetworkClientServer` so the Store build is pre-authorised for private networks.
