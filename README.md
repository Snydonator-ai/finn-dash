# Finn Dash: Ocean Run — Android app (v5)

A small full-screen Android app that runs the game. The whole game is the single file
`app/src/main/assets/finn-dash.html`; `MainActivity.java` wraps it in a web view and adds the parts a web
page can't do by itself.

## How a build happens

Pushing to GitHub runs `.github/workflows/build-apk.yml`, which builds on GitHub's servers and attaches two
files to the release tagged **latest**:

| File | What it's for |
|---|---|
| `finn-dash.apk` | install straight onto the family tablets, as before |
| `finn-dash.aab` | the "app bundle" you upload to the Google Play Console |

`versionCode` comes from the GitHub build number, so every build is newer than the last.

## What the app wrapper does

- full screen, screen kept awake, buttons kept clear of the camera hole
- Back button / back swipe (Android 16 style): closes menus, pauses a swim, and asks "Leave?" on the main menu
- pauses and goes quiet when another app needs the sound (a phone call, an alarm)
- **Tip Finn**: the Golden Narwhal (`golden_narwhal`, one-time, $2.99) through Google Play Billing 8.3,
  checked again at every launch (restores on a new tablet, removed after a refund)
- **Play Age Signals (beta)**: asked only when a grown-up opens the Support Finn card; if a parent's
  settings say no, the tip is hidden. Nothing from it is stored.

## Versions that matter

| Thing | Version | Why |
|---|---|---|
| targetSdk / compileSdk | 36 | Google Play requires Android 16 for new apps and updates |
| Android Gradle Plugin (root `build.gradle`) | 8.13.0 | the first plugin line that knows API 36 well; needs Gradle 8.13 + Java 17 |
| Gradle (in the workflow) | 8.13 | |
| Billing library | 8.3.0 | Play requires version 8 or newer |

## Files worth knowing

| Path | What it is |
|---|---|
| `app/src/main/assets/finn-dash.html` | the game itself |
| `app/src/main/java/com/jsnyder/finndash/MainActivity.java` | the wrapper described above |
| `app/src/main/res/values/strings.xml` | the app's name ("Finn Dash: Ocean Run"; "Finn Dash" under the icon) |
| `app/src/main/res/mipmap-*` | the app icon |
| `app/finndash-key.jks` + `gradle.properties` | the signing key. On Google Play it's only the *upload* key (Play App Signing re-signs the app), but keep this repository **private**. |
