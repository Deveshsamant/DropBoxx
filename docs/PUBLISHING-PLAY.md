# Publishing to Google Play - step by step

Personal developer accounts created after Nov 2023 must run a **closed test with at least 12
testers opted in for 14 continuous days** before they can apply for production access. You have
12 testers, so the plan below gets that clock started as early as possible.

## 0. One-time preparation (do this today)

1. **Name:** DropNest, `applicationId` `com.dropnest.app` (permanent once uploaded - do not change after the first upload).
2. **Create the upload keystore** (keep it forever; losing it means you can never update the app):
   ```bash
   keytool -genkeypair -v -keystore release.keystore -alias upload -keyalg RSA -keysize 4096 -validity 10000
   ```
   Put `release.keystore` outside the repo and create `keystore.properties` next to
   `androidApp/build.gradle.kts` (already git-ignored):
   ```
   storeFile=C:/secure/release.keystore
   storePassword=...
   keyAlias=upload
   keyPassword=...
   ```
3. **Build the release bundle**: `./gradlew :androidApp:bundleRelease` ->
   `androidApp/build/outputs/bundle/release/androidApp-release.aab`.
4. **Privacy policy URL** - required because the app uses network + notifications. Deploy `site/`
   on Vercel first (see `site/README.md`); the policy is then at `https://<project>.vercel.app/privacy`.
5. Store assets are ready in `docs/store/`: 512x512 icon, 1024x500 feature graphic, six 1080x1920
   phone screenshots (Play rejects raw 1080x2400 captures - the long side may be at most 2x the
   short side; `packaging/store-shots.py` frames captures correctly), and all listing text in
   `docs/store/LISTING.md`.

## 1. Play Console setup

1. play.google.com/console -> **Create app** -> name, default language, *App* (not game), *Free*.
   Free apps can never be switched to paid later; a Pro in-app purchase is still possible.
2. **Dashboard -> Set up your app** - complete every card:
   * App access: *All functionality is available without special access*.
   * Ads: No.  * Content rating: fill the questionnaire (Utility) -> Everyone.
   * Target audience: 13+ (avoids the Families policy).
   * News app: No.  * COVID: No.  * Data safety: declare *no data collected, no data shared*;
     device name is processed locally only. Encryption in transit: Yes (TLS).
   * Government apps: No.  * Financial features: No.  * Health: No.
   * Privacy policy: paste the URL from step 0.4.
   * App category: Tools.  Contact email.
   * Store listing: descriptions, graphics, screenshots.
3. **App integrity -> App signing**: accept *Play App Signing* (Google keeps the final key; you
   keep the upload key from step 0.2).

## 2. Closed testing (starts the 14-day clock)

1. **Testing -> Closed testing -> Create track** (name it "Alpha"). Choose *Email list* testers,
   create a list and paste your 12 tester emails (Google accounts). Save.
2. **Create new release** -> upload the `.aab` -> release name `1.0.0 (1)` -> release notes ->
   *Next* -> fix any warnings -> **Save and publish** (older console: *Review release -> Start rollout*).
3. First submission goes through review (hours to a few days). When approved, copy the
   **opt-in link** from the track page and send it to all 12 testers. Each tester must
   **open the link, tap "Become a tester", then install from Play** - being on the list is not
   enough; they must opt in.
4. Keep the release live and the testers opted in for **14 consecutive days**. The dashboard shows
   the countdown. You may (and should) push updated builds during this time - every update needs a
   higher `versionCode`.
5. Ask testers to actually use it (send a photo phone->PC, fetch from a box, hotspot mode); Google
   asks how you used feedback in the next step.

## 3. Apply for production access (day 15)

1. Dashboard -> **Apply for production** -> answer: what you tested, how testers were recruited,
   what feedback you got and what you changed. Be concrete (e.g. "3 testers hit a duplicate-name
   bug; fixed in 1.0.2"). Google answers within ~7 days.
2. After approval: **Production -> Create new release** -> upload the same or newer `.aab` ->
   countries (start with India + worldwide) -> roll out. First production review can take up to 7 days.
3. Then: **Release -> Setup -> Advanced settings -> Managed publishing** if you want to control the
   exact go-live moment.

## 4. After launch checklist

* Set up **Pre-launch report** review (automatic crawler on ~10 devices) after each release.
* Watch **Android vitals** (ANR rate < 0.47 %, crash rate < 1.09 % keep you out of "bad behaviour").
* Every August Google raises the target SDK requirement; `targetSdk` is 36 now (2026 requirement).
* Keep `versionCode` strictly increasing; bump `versionName` for humans.
* Store the keystore + passwords in a password manager and an offline backup.

## Common rejection reasons for this kind of app

* Missing/invalid privacy policy URL, or Data safety form contradicting permissions.
* `FOREGROUND_SERVICE_DATA_SYNC` needs a justification video/description in
  *App content -> Foreground service permissions* (text in `docs/store/LISTING.md`): "Keeps the receive server running while the user
  sends files between their own devices; started only by the user". Add it.
* NEARBY_WIFI_DEVICES / location: explain "hotspot mode creates a local-only Wi-Fi network".
* Trademarked name or icon similar to another brand.
