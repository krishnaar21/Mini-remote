# Mini Remote (Android)

Universal TV remote with a soft-touch charcoal design. Kotlin + Jetpack Compose.

- Supports TVs released in **2017 or later**, with current firmware.
- Control over local Wi-Fi: **Roku** (ECP), **Samsung** Tizen, **LG** webOS, **Android TV / Google TV** (remote protocol v2, pair with the code on screen). Fire TV runs Fire OS and doesn't support this protocol.
- **Material design**, dark, with your accent colour.
- **Remote page (Apple TV Remote style):** mute · TV picker (with what's on screen) · power on top; one big touch surface or the dial (Settings); Back, Home, Play and Search below. One finger navigates; two fingers slide for volume (up/down) and channels (sideways). The Vol · Ch pill (bottom left) opens a volume / channel / number tray, and the phone's volume buttons work too. OK: tap = OK, double tap = mute, hold = voice.
- **Two pages:** Remote and Apps (dots bottom right; tap or swipe). Settings open from the gear in the TV sheet.
- **Search** by typing or by voice (the phone's speech recognition).
- **Channels tab:** a quick access row you pin to, apps the TV reports (Roku, Samsung, LG), popular apps (Netflix, YouTube, Prime Video, JioHotstar…), and your own TV channels by number.
- Round clickpad: tap, hold to repeat, drag to scrub (landing zone picks the axis), hold OK for voice.
- Haptics, synthesized click sounds, accent colours, retro fader, keyboard sheet.
- Local-network only: private IPv4 addresses are enforced in code.
- Samsung and LG secure connections are pinned to the TV's certificate on first pairing. If it changes, the app refuses to connect until you remove and re-add the TV.

## Install
Every push to `main` builds an APK and publishes it here:
`https://github.com/krishnaar21/mini-remote/releases/tag/latest-build` (download `MiniRemote.apk`).

## Privacy
See [PRIVACY.md](PRIVACY.md). No accounts, ads or tracking. Data stays on the phone, and pairing keys are encrypted with the Android Keystore.

## Legal
- Roku, Samsung, LG, webOS, Android TV, Google TV and all app names and logos are trademarks of their respective owners. Mini Remote is independent and not affiliated with or endorsed by any of them.
- Third-party software: AndroidX / Jetpack Compose, Kotlin, kotlinx.coroutines, OkHttp / Okio and AndroidSVG are under the Apache License 2.0; Simple Icons is CC0 1.0. The full licence texts ship in the app under **Settings › About › Open-source licences**.
- No fonts or images from third parties are bundled; logos are downloaded on demand (and can be turned off).
- **Google Play note:** the in-app updater uses `REQUEST_INSTALL_PACKAGES`, which Google Play only allows for apps whose main purpose is installing apps. Remove the updater (and that permission) before publishing on Play; it's meant for sideloaded builds from GitHub.
- This repository has no licence file yet, so all rights are reserved by default. Add one (for example MIT or Apache-2.0) if you want others to reuse the code.
