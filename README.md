# Mini Remote (Android)

Universal TV remote with a soft-touch charcoal design. Kotlin + Jetpack Compose.

- Control over local Wi-Fi: **Roku** (ECP), **Samsung** Tizen, **LG** webOS, **Android TV / Google TV** (remote protocol v2, pair with the code on screen). Fire TV runs Fire OS and doesn't support this protocol.
- **Material design**, dark, with your accent colour.
- **Remote page (Apple TV Remote style):** mute · TV picker (with what's on screen) · power on top; one big touch surface or the dial (Settings); Back, Home, Play and Search below. One finger navigates; two fingers slide for volume (up/down) and channels (sideways). Swipe up for a volume / channel / number tray, and the phone's volume buttons work too. OK: tap = OK, double tap = mute, hold = voice.
- **Pages, not tabs:** swipe between Remote, Apps and Settings.
- **Search** by typing or by voice (the phone's speech recognition).
- **Channels tab:** a quick access row you pin to, apps the TV reports (Roku, Samsung, LG), popular apps (Netflix, YouTube, Prime Video, JioHotstar…), and your own TV channels by number.
- Round clickpad: tap, hold to repeat, drag to scrub (landing zone picks the axis), hold OK for voice.
- Haptics, synthesized click sounds, accent colours, retro fader, keyboard sheet.
- Local-network only: private IPv4 addresses are enforced in code.
- Samsung and LG secure connections are pinned to the TV's certificate on first pairing. If it changes, the app refuses to connect until you remove and re-add the TV.

## Install
Every push to `main` builds an APK and publishes it here:
`https://github.com/krishnaar21/mini-remote/releases/tag/latest-build` (download `MiniRemote.apk`).
