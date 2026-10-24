<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/img/logo-dark.png">
    <source media="(prefers-color-scheme: light)" srcset="docs/img/logo-light.png">
    <img alt="pulso.tv" src="docs/img/logo-light.png" width="480">
  </picture>
</p>

<p align="center"><b>English</b> · <a href="README.pt-BR.md">Português (Brasil)</a></p>

<p align="center">
  <b>Twitch, made for your TV.</b><br>
  An open-source client for Android TV and Google TV.
</p>

<p align="center">
  <a href="https://github.com/beetim/pulso-tv/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/beetim/pulso-tv?color=FFC800&labelColor=18181B"></a>
  <img alt="Android 7+" src="https://img.shields.io/badge/Android-7.0%2B-3D6BFF?labelColor=18181B">
  <img alt="License AGPL-3.0" src="https://img.shields.io/badge/license-AGPL--3.0-EFEFF1?labelColor=18181B">
</p>

<p align="center">
  <img src="docs/img/home.png" alt="pulso.tv home screen" width="85%">
</p>

## Why pulso.tv

The official Twitch app on TV is heavy and not really designed for the couch. pulso.tv is a
lightweight client.

- **No ads from the app and no tracking.** No telemetry, no account of our own, no advertising.
  The app only talks to Twitch and to the emote services.
- **Free and open source** (AGPL-3.0). You can read it, audit it and build it yourself.

## Features

| | |
|---|---|
| **Shelves on the home screen** | Continue watching, Following, Games, Most watched and Recommended. |
| **Your language first** | Recommendations favor your country; inside a game, channels in your language come before "Other channels", with a **Load more** button. |
| **Full-featured player** | **Auto** (adaptive) or fixed quality, aspect ratio, sleep timer, mute and fullscreen. |
| **On-screen chat** | Twitch, 7TV, BTTV and FFZ emotes and badges; send messages when signed in. |
| **Channels, videos and clips** | Channel page with recent videos and **Clips**; the list of channels you follow, even offline. |
| **Search** | Channels, live streams and categories, with history. |
| **QR code sign-in** | Scan the code with your phone, no typing a password on the TV. |
| **Resume videos** | Recorded videos pick up where you left off. |
| **Built-in updates** | The app tells you when a new version is out and installs it in one click. |
| **English and Portuguese** | Switch the language right from the home screen. |

## Requirements

- Android TV or Google TV running **Android 7.0** or newer.

## Installation

### Option 1 · one command from your PC (recommended)

1. On the TV, open **Settings → System → About** and press **Build** 7 times to enable developer mode.
2. In **Developer options**, turn on **USB debugging** and **Network debugging**
   (the name varies by manufacturer). Note the TV's IP address under **Network**.
3. On your PC, with [adb](https://developer.android.com/tools/releases/platform-tools) installed, run
   the command for your system. It downloads the latest version straight from this repository,
   installs it on the TV and opens the app.

**Windows (PowerShell):**

```powershell
irm https://raw.githubusercontent.com/beetim/pulso-tv/main/install.ps1 | iex
```

**Linux and macOS:**

```bash
curl -fsSL https://raw.githubusercontent.com/beetim/pulso-tv/main/install.sh | bash -s -- <tv-ip>
```

Accept the authorization prompt that appears on the TV the first time you connect. The scripts are
short: [`install.ps1`](install.ps1) and [`install.sh`](install.sh); read them first if you like.

Prefer doing it by hand? Download `Pulso.apk` from
[Releases](https://github.com/beetim/pulso-tv/releases/latest), then run
`adb connect <tv-ip>:5555` and `adb install -r Pulso.apk`.

### Option 2 · the Downloader app, straight on the TV

1. Install the **Downloader** app (by AFTVnews) from the TV's app store.
2. In the TV settings, allow **installing unknown apps** for Downloader.
3. In Downloader, enter the address of `Pulso.apk` from the Releases page and install it.

### Option 3 · USB drive

Copy `Pulso.apk` to a USB drive, plug it into the TV and open the file with a file manager
(allowing it to install unknown apps).

Once installed, the app shows up in the launcher as **pulso.tv**.

## Updates

pulso.tv checks this repository's Releases when the home screen opens. When there is a new version,
**Update vX.Y.Z** appears at the top of the screen: the app downloads the APK and opens Android's
installer, where you just confirm. You can also check right away under **Profile → Check for updates**.

Updating in place keeps your sign-in and settings, as long as the APK is signed with the same key.

## Signing in

Choose **Sign in**. The screen shows a code and a QR: scan it with your phone and authorize the
Twitch account that is open in its browser. That same account is then used on the TV.

## Troubleshooting

- **"App not installed" / signature conflict:** uninstall the old version before installing an APK
  signed with a different key (you will lose your sign-in).
- **`adb: device unauthorized`:** accept the authorization on the TV screen and run the command again.
- **Asked to sign in again:** the Twitch token may have expired; sign in again with the QR code.
- **Video is slow to start right after turning the TV on:** the network is still waking up; it
  usually settles within a few seconds.

## Building from source

You need JDK 21 and the Android SDK. On Windows (PowerShell):

```powershell
$env:JAVA_HOME='<jdk-21-folder>'
$env:ANDROID_HOME='<android-sdk-folder>'
.\gradlew.bat assembleRelease --no-daemon
```

The APK ends up in `app/build/outputs/apk/release/app-release.apk`. The release signing config reads
`keystore.properties` and a `.jks` file that are **not** in the repository. Create your own:

```bash
keytool -genkey -v -keystore pulso.jks -alias pulso -keyalg RSA -keysize 2048 -validity 10000
```

and fill in `keystore.properties` as described in `app/build.gradle.kts`. Without it, the build uses
the debug key. To point the updater at your own fork, change `pulso.githubRepo` in `gradle.properties`.


## Disclaimer

pulso.tv is an independent project and is **not affiliated with, sponsored by or endorsed by Twitch**.
"Twitch" and all other trademarks belong to their respective owners. Use at your own risk and respect
Twitch's terms of service.

## Credits and license

pulso.tv is built on the engine of **[Xtra](https://github.com/andreyasadchy/xtra)**, the Twitch client
for Android by **Andrey Asadchy**, under the [GNU Affero General Public License v3.0](LICENSE).

Xtra provides the data and playback layer: ExoPlayer/media3 with low-latency HLS, Twitch's GraphQL and
Helix APIs, chat (IRC, WebSocket, EventSub, PubSub), third-party emotes (7TV, BTTV, FFZ), follows,
search and the local Room database. The pulso.tv interface (home, search, channel, game, player and
chat) is new, written in Compose, and does not reuse any Xtra screen. The full credits are in the
header of `app/src/main/java/com/pulso/tv/PulsoCredits.kt`.

Because Xtra is AGPL-3.0, so is pulso.tv: anyone who distributes this app must publish the source
code and keep these credits.
