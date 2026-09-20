# Finn Dash — Android app

A small full-screen Android app that runs the Finn Dash game. The whole game is the single file
`app/src/main/assets/finn-dash.html`; this project just wraps it in a web view so it gets a home-screen
icon, runs in landscape with no browser bars, and works with no internet at all (the app asks for no
permissions whatsoever, not even network access).

## How a build happens

Pushing this project to GitHub triggers `.github/workflows/build-apk.yml`, which builds the app on
GitHub's servers and attaches `finn-dash.apk` to a release tagged **latest**. Nothing needs to be
installed on your own computer. The step-by-step guide is in `FIRE-TABLET-SETUP.md`, next to this
project in the Kids Game folder.

## Updating the game later

Replace `app/src/main/assets/finn-dash.html` with a newer copy of the game and push. `versionCode` is
derived from the GitHub build number, so every cloud build is automatically newer than the last and
no file needs editing by hand. Installs always go over the top of the previous one because every
build is signed with `app/finndash-key.jks`, so saved players and coins survive an update.

## Phones as well as tablets

The activity draws into the display cutout (`LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`) and the game
reads the cutout size through CSS `env(safe-area-inset-*)`, so the play field stays clear of the
camera hole while still using the whole screen. Orientation is `sensorLandscape`; held upright, the
game shows a rotate prompt and pauses itself.

## Files worth knowing

| Path | What it is |
|---|---|
| `app/src/main/assets/finn-dash.html` | the game itself |
| `app/src/main/java/com/jsnyder/finndash/MainActivity.java` | the web view wrapper: fullscreen, landscape, screen stays awake, back button pauses then exits |
| `app/src/main/res/mipmap-*` | the app icon (Finn on an ocean background) |
| `app/finndash-key.jks` | the family signing key, so updates install over the old version |
| `gradle.properties` | the (deliberately simple) key passwords |

The signing key is only here so updates work; it protects nothing valuable. If this project ever goes
to a store, generate a fresh key and keep it private.
