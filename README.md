# Mini Remote (Android)

Universal TV remote with a soft-touch charcoal design. Kotlin + Jetpack Compose.

- Control over local Wi-Fi: **Roku** (ECP), **Samsung** Tizen, **LG** webOS, **Android TV / Google TV** (remote protocol v2, pair with the code on screen). Fire TV runs Fire OS and doesn't support this protocol.
- **Material design**, dark, with your accent colour.
- **Home (Apple TV Remote style):** TV picker on top with what's on screen, one big touch surface (or the dial, set in Settings), and round keys: Back, Home, Play, Mute, Search, Voice. **Watch / Navigate:** on live TV or an input the surface changes volume (up/down) and channel (left/right); in menus it moves around. Roku and LG switch by themselves. Volume and channels also hide in the tab bar (swipe up), and the phone's volume buttons work. OK: tap = OK, double tap = mute, hold = voice.
- **Channels tab:** a quick access row you pin to, apps the TV reports (Roku, Samsung, LG), popular apps (Netflix, YouTube, Prime Video, JioHotstar…), and your own TV channels by number.
- **Profile & settings** on one page, under a floating icon-only tab pill.
- Round clickpad: tap, hold to repeat, drag to scrub (landing zone picks the axis), hold OK for voice.
- Haptics, synthesized click sounds, accent colours, retro fader, keyboard sheet.
- Local-network only: private IPv4 addresses are enforced in code.
- Samsung and LG secure connections are pinned to the TV's certificate on first pairing. If it changes, the app refuses to connect until you remove and re-add the TV.

## Install
Every push to `main` builds an APK and publishes it here:
`https://github.com/krishnaar21/mini-remote/releases/tag/latest-build` (download `MiniRemote.apk`).
