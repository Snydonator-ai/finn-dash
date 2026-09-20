# Putting Finn Dash on the Fire tablets (and Android phones)

Two parts: build the app once on GitHub's free servers (about 10 minutes, nothing installed on your
PC), then install it on each tablet (about 2 minutes each).

The same app file works on Android phones — see **Phones** near the bottom.

---

## Part 1 — Build the app (once)

**1. Get a GitHub account.** Free, at <https://github.com>. Skip if you have one.

**2. Make a new repository.**
- Click **+** (top right) → **New repository**
- Name: `finn-dash`
- **Public** is easiest: the finished app gets a plain download link the tablets can open. Private
  works too, but then each tablet has to sign in to GitHub before it can download.
- Do **not** tick "Add a README file"
- Click **Create repository**

**3. Upload the project.**
- On the empty repository page, click the link **uploading an existing file**
- Open `Kids Game\finn-dash-android` on your PC
- Select everything **inside** that folder (Ctrl+A) — the `app` folder, `build.gradle`,
  `settings.gradle`, `gradle.properties`, `.gitignore`, `README.md`, `FIRE-TABLET-SETUP.md`,
  `workflow-build-apk.yml`
- Drag that selection into the browser window
  - Important: upload the *contents*, not the `finn-dash-android` folder itself, so that the
    `app` folder sits at the top level of the repository
- Click **Commit changes**

**3b. Add the build instructions file.** (One small file has to be created on GitHub itself, because
it lives in a hidden folder that Windows and browsers like to skip.)
- On the repository page click **Add file → Create new file**
- In the filename box type exactly: `.github/workflows/build-apk.yml`
  - typing the slashes creates the folders for you
- Open `workflow-build-apk.yml` from the Kids Game folder in Notepad, copy everything, and paste it
  into the big text box
- Click **Commit changes**

> **Already did this once?** If I send you a new `workflow-build-apk.yml`, don't create the file
> again — open the existing one on GitHub (**.github → workflows → build-apk.yml**), click the
> **pencil** icon, select everything in the box (Ctrl+A), paste the new contents over it, and commit.
> Saving it starts a fresh build straight away.

**4. Wait for the build.**
- Click the **Actions** tab. A job called *Build Finn Dash APK* starts as soon as step 3b is saved.
- It takes roughly 3–5 minutes. A green tick means it worked.
- If it fails, open the failed step, copy the red error text, and send it to me — I'll fix it.

**5. Find the app file.**
- Go back to the repository's main page and click **Releases** in the right-hand column
- Open **Finn Dash — latest build**
- Under *Assets* there's **finn-dash.apk**. Right-click it and copy the link — that's the address
  you'll open on the tablets. It looks like:
  `https://github.com/YOURNAME/finn-dash/releases/download/latest/finn-dash.apk`

---

## Part 2 — Install on a Fire tablet (repeat per tablet)

**1. Allow installing from the browser.**
- On the tablet: **Settings → Security & Privacy → Apps from Unknown Sources**
- Find **Silk Browser** in the list and switch it **on**
- (On some Fire OS versions this is Settings → Security & Privacy → Install Unknown Apps)

**2. Download the app.**
- Open the **Silk** browser and go to the release link from step 5 above
  - Tip: email yourself the link, or type the short version `github.com/YOURNAME/finn-dash/releases`
- Tap **finn-dash.apk**. Accept the "this type of file can harm your device" warning — that message
  appears for every file that doesn't come from the Appstore.

**3. Install it.**
- Tap the downloaded file (or pull down the notification and tap it)
- Tap **Install**, then **Open**
- **Finn Dash** is now on the home screen with the fish icon

**4. Play.** It opens full screen in landscape, the screen won't dim while playing, and the back
button pauses the game (press it twice to leave).

---

## Good to know

- **Fully offline.** The app has no internet permission at all. Once installed it never touches the
  network.
- **Each tablet keeps its own players.** Progress lives on the tablet. To move a player between
  tablets, use the **Backup Code** button in the menu and type the code on the other tablet.
- **Updates.** When I send a new version, open `Kids Game\finn-dash-android` on your PC, select
  everything inside it, and drag it onto the repository's **Add file → Upload files** page exactly
  like the first upload. Matching files are replaced, everything else is left alone, and the version
  number takes care of itself. GitHub rebuilds within minutes; installing on the tablet goes over the
  top, so players, coins and cosmetics are all kept.
- **Older tablets.** The app needs a Fire tablet from roughly 2017 or newer (Fire OS 6+). Anything
  older has a browser engine too old for the game.
- **Graphics.** If a tablet struggles, the game notices and switches itself to Lite graphics. You can
  also set it by hand: pause → **Graphics** button (Auto / Full / Lite).

## Phones

The same `finn-dash.apk` installs on any Android phone from about 2017 onward — there's nothing
different to build. On the phone: **Settings → Apps → Special app access → Install unknown apps**,
allow it for Chrome (wording varies by maker), then open the release link and tap the file.

The game knows it's on a phone: it uses the full width of the screen (so hazards come into view
sooner rather than sitting behind black bars), keeps its buttons finger-sized, stays clear of the
camera cutout, and shows a "turn me sideways" screen — and pauses — if the phone is held upright.

## If something goes wrong

| Problem | Fix |
|---|---|
| "App not installed" | An older Finn Dash with a different signature is on the device — uninstall it first (long-press the icon → Uninstall), then install again |
| The download does nothing | Unknown Sources isn't enabled for Silk (Part 2, step 1) |
| The Actions build failed | Send me the red error text from the failed step |
| The game looks squashed | Rotate the tablet to landscape; the app locks to landscape once it opens |
