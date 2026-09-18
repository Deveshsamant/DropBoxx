# dropnest site

Static marketing site + privacy policy (required by Google Play and the Microsoft Store).
Plain HTML/CSS, no build step.

## Deploy on Vercel (once)
1. vercel.com -> **Add New… -> Project** -> import `Deveshsamant/DropBoxx`.
2. **Root Directory**: `site`  ·  Framework preset: **Other**  ·  leave build/output empty.
3. Deploy. You get `https://<project>.vercel.app`; add a custom domain under *Settings -> Domains* if you have one.
4. The privacy policy is then at `https://<project>.vercel.app/privacy` - paste that URL into both store consoles.

Every push to `main` that touches `site/` redeploys automatically.

## After the apps are published
Edit `links.js`, paste the store URLs (and optional direct APK/MSI links), commit, push.
Buttons switch from "Coming soon" to live links on the next deploy.

## Regenerate screenshots
`python packaging/store-shots.py <dir-with-raw-captures>` builds the store screenshots;
the site uses the smaller WebP copies in `assets/shots/` (see `docs/design/nocturne`).
