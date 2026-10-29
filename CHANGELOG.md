# Changelog

## 1.0.8
- Fixed the update loop: the app asked for "install unknown apps" every time and never installed. It now asks once, then downloads and opens the installer.

## 1.0.7
- New player option "Chat size" (Small / Medium / Large) that resizes the whole chat block.
- The profile panel now shows your Twitch profile picture.
- Automatic update check every 30 minutes (was 6 hours); the "latest version" message is centered.

## 1.0.6
- Live preview (with sound) while a stream card has the focus; it can be turned off in Settings.
- Ad protection: playback token requested anonymously first, plus the engine's video swap and "hide ads" as a safety net. It can be turned off in Settings.
- The chat now disconnects when hidden (or in full screen / in the background) and reconnects when shown again, keeping the messages already received.
- New setting: open the player with or without the chat.
- Much lighter: the APK went from 11.9 MB to 6.5 MB (old Xtra screens, unused libraries and the bundled TLS library removed). Android 9 or newer is now required.
- The card preview switches player type when an ad shows up, instead of waiting for the ad to end.
- Lighter chat: 7TV event socket, paints and raid/poll/prediction messages are off.

## 1.0.5
- Smaller APK (translation libraries and x86 removed), focus fixes, release and update flow.
